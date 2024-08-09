package datadog.trace.bootstrap;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Telemetry class used to relay information about tracer activation. */
public abstract class BootstrapInitializationTelemetry {
  public static final BootstrapInitializationTelemetry noneInstance() {
    return None.INSTANCE;
  }

  public static final BootstrapInitializationTelemetry createFromForwarderPath(
      String forwarderPath) {
    return new JsonBased(new ForwarderJsonSender(forwarderPath));
  }

  /**
   * Adds meta information about the process to the initialization telemetry Does NOT support
   * overriding an attr, each attr should be once and only once
   */
  public abstract void initMetaInfo(String attr, String value);

  /**
   * Indicates that an abort condition occurred during the bootstrapping process Abort conditions
   * are assumed to leave the bootstrapping process incomplete. {@link #markIncomplete()}
   */
  public abstract void onAbort(String reasonCode);

  /**
   * Indicates that an exception occurred during the bootstrapping process By default the exception
   * is assumed to NOT have fully stopped the initialization of the tracer.
   *
   * <p>If this exception stops the core bootstrapping of the tracer, then {@link #markIncomplete()}
   * should also be called.
   */
  public abstract void onError(Throwable t);

  /**
   * Indicates an exception that occurred during the bootstrapping process that left initialization
   * incomplete. Equivalent to calling {@link #onError(Throwable)} and {@link #markIncomplete()}
   */
  public abstract void onFatalError(Throwable t);

  public abstract void onError(String reasonCode);

  public abstract void markIncomplete();

  public abstract void finish();

  public static final class None extends BootstrapInitializationTelemetry {
    static final None INSTANCE = new None();

    private None() {}

    @Override
    public void initMetaInfo(String attr, String value) {}

    @Override
    public void onAbort(String reasonCode) {}

    @Override
    public void onError(String reasonCode) {}

    @Override
    public void onFatalError(Throwable t) {}

    @Override
    public void onError(Throwable t) {}

    @Override
    public void markIncomplete() {}

    @Override
    public void finish() {}
  }

  public static final class JsonBased extends BootstrapInitializationTelemetry {
    private final JsonSender sender;

    private JsonBuffer metaBuffer = new JsonBuffer();
    private JsonBuffer pointsBuffer = new JsonBuffer();

    // one way false to true
    private volatile boolean incomplete = false;

    JsonBased(JsonSender sender) {
      this.sender = sender;
    }

    @Override
    public void initMetaInfo(String attr, String value) {
      synchronized (metaBuffer) {
        metaBuffer.name(attr).value(value);
      }
    }

    @Override
    public void onAbort(String reasonCode) {
      onPoint("library_entrypoint.abort", "reason:" + reasonCode);

      markIncomplete();
    }

    @Override
    public void onError(Throwable t) {
      onPoint("library_entrypoint.error", "error_type:" + t.getClass().getName());
    }

    @Override
    public void onFatalError(Throwable t) {
      onError(t);

      markIncomplete();
    }

    @Override
    public void onError(String reasonCode) {
      onPoint("library_entrypoint.error", "error_type:" + reasonCode);
    }

    @Override
    public void markIncomplete() {
      incomplete = true;
    }

    void onPoint(String pointName) {
      synchronized (pointsBuffer) {
        pointsBuffer.beginObject();
        pointsBuffer.name("name").value(pointName);
        pointsBuffer.endObject();
      }
    }

    void onPoint(String pointName, String tag) {
      synchronized (pointsBuffer) {
        pointsBuffer.beginObject();
        pointsBuffer.name("name").value(pointName);
        pointsBuffer.name("tags").array(tag);
        pointsBuffer.endObject();
      }
    }

    void onPoint(String pointName, String[] tags) {
      synchronized (pointsBuffer) {
        pointsBuffer.beginObject();
        pointsBuffer.name("name").value(pointName);
        pointsBuffer.name("tags").array(tags);
        pointsBuffer.endObject();
      }
    }

    @Override
    public void finish() {
      if (!incomplete) {
        onPoint("library_entrypoint.complete");
      }

      JsonBuffer buffer = new JsonBuffer();
      buffer.beginObject();

      buffer.name("metadata");
      synchronized (metaBuffer) {
        buffer.object(metaBuffer);
      }

      buffer.name("points");
      synchronized (pointsBuffer) {
        buffer.array(pointsBuffer);

        pointsBuffer.reset();
      }

      buffer.endObject();

      try {
        sender.send(buffer);
      } catch (Throwable t) {
        // Since this is the reporting mechanism, there's little recourse here
        // Decided to simply ignore - arguably might want to write to stderr
      }
    }
  }

  public static interface JsonSender {
    public abstract void send(JsonBuffer buffer) throws IOException;
  }

  public static final class ForwarderJsonSender implements JsonSender {
    private final String forwarderPath;

    ForwarderJsonSender(String forwarderPath) {
      this.forwarderPath = forwarderPath;
    }

    @Override
    public void send(JsonBuffer buffer) throws IOException {
      ProcessBuilder builder = new ProcessBuilder(forwarderPath);

      Process process = builder.start();
      process.getOutputStream().write(buffer.toByteArray());

      try {
        process.waitFor(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // just for hygiene, reset the interrupt status
        Thread.currentThread().interrupt();
      }
    }
  }
}
