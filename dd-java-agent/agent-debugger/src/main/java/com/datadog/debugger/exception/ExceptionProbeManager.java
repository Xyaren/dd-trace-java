package com.datadog.debugger.exception;

import com.datadog.debugger.probe.ExceptionProbe;
import com.datadog.debugger.probe.Where;
import com.datadog.debugger.sink.Snapshot;
import com.datadog.debugger.util.ClassNameFiltering;
import com.datadog.debugger.util.ExceptionHelper;
import com.datadog.debugger.util.WeakIdentityHashMap;
import datadog.trace.bootstrap.debugger.ProbeId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the probes used for instrumentation of exception stacktraces. */
public class ExceptionProbeManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionProbeManager.class);

  private final Map<String, Instant> fingerprints = new ConcurrentHashMap<>();
  private final Map<String, ExceptionProbe> probes = new ConcurrentHashMap<>();
  private final ClassNameFiltering classNameFiltering;
  // FIXME: if this becomes a bottleneck, find a way to make it concurrent weak identity hashmap
  private final Map<Throwable, ThrowableState> snapshotsByThrowable =
      Collections.synchronizedMap(new WeakIdentityHashMap<>());
  private final long captureIntervalS;
  private final Clock clock;

  public ExceptionProbeManager(ClassNameFiltering classNameFiltering, Duration captureInterval) {
    this(classNameFiltering, captureInterval, Clock.systemUTC());
  }

  ExceptionProbeManager(ClassNameFiltering classNameFiltering) {
    this(classNameFiltering, Duration.ofHours(1), Clock.systemUTC());
  }

  ExceptionProbeManager(
      ClassNameFiltering classNameFiltering, Duration captureInterval, Clock clock) {
    this.classNameFiltering = classNameFiltering;
    this.captureIntervalS = captureInterval.getSeconds();
    this.clock = clock;
  }

  public ClassNameFiltering getClassNameFiltering() {
    return classNameFiltering;
  }

  public boolean createProbesForException(StackTraceElement[] stackTraceElements) {
    boolean created = false;
    for (StackTraceElement stackTraceElement : stackTraceElements) {
      if (stackTraceElement.isNativeMethod() || stackTraceElement.getLineNumber() < 0) {
        // Skip native methods and lines without line numbers
        // TODO log?
        continue;
      }
      if (classNameFiltering.isExcluded(stackTraceElement.getClassName())) {
        continue;
      }
      Where where =
          Where.convertLineToMethod(
              stackTraceElement.getClassName(),
              stackTraceElement.getMethodName(),
              null,
              String.valueOf(stackTraceElement.getLineNumber()));
      ExceptionProbe probe = createMethodProbe(this, where);
      created = true;
      probes.putIfAbsent(probe.getId(), probe);
    }
    return created;
  }

  void addFingerprint(String fingerprint) {
    fingerprints.put(fingerprint, Instant.MIN);
  }

  private static ExceptionProbe createMethodProbe(
      ExceptionProbeManager exceptionProbeManager, Where where) {
    String probeId = UUID.randomUUID().toString();
    return new ExceptionProbe(
        new ProbeId(probeId, 0), where, null, null, null, exceptionProbeManager);
  }

  public boolean isAlreadyInstrumented(String fingerprint) {
    return fingerprints.containsKey(fingerprint);
  }

  public Collection<ExceptionProbe> getProbes() {
    return probes.values();
  }

  public boolean shouldCaptureException(String fingerprint) {
    return shouldCaptureException(fingerprint, clock);
  }

  boolean shouldCaptureException(String fingerprint, Clock clock) {
    Instant lastCapture = fingerprints.get(fingerprint);
    if (lastCapture == null) {
      return false;
    }
    return ChronoUnit.SECONDS.between(lastCapture, Instant.now(clock)) >= captureIntervalS;
  }

  public void addSnapshot(Snapshot snapshot) {
    Throwable throwable = snapshot.getCaptures().getReturn().getCapturedThrowable().getThrowable();
    throwable = ExceptionHelper.getInnerMostThrowable(throwable);
    if (throwable == null) {
      LOGGER.debug(
          "Unable to find root cause of exception: {}",
          snapshot.getCaptures().getReturn().getCapturedThrowable().getThrowable().toString());
      return;
    }
    ThrowableState state =
        snapshotsByThrowable.computeIfAbsent(
            throwable, key -> new ThrowableState(UUID.randomUUID().toString()));
    snapshot.setExceptionId(state.getExceptionId());
    state.addSnapshot(snapshot);
  }

  public ThrowableState getSateByThrowable(Throwable throwable) {
    return snapshotsByThrowable.get(throwable);
  }

  public void updateLastCapture(String fingerprint) {
    updateLastCapture(fingerprint, clock);
  }

  void updateLastCapture(String fingerprint, Clock clock) {
    fingerprints.put(fingerprint, Instant.now(clock));
  }

  public static class ThrowableState {
    private final String exceptionId;
    private List<Snapshot> snapshots;

    private ThrowableState(String exceptionId) {
      this.exceptionId = exceptionId;
    }

    public String getExceptionId() {
      return exceptionId;
    }

    public List<Snapshot> getSnapshots() {
      return snapshots;
    }

    public boolean isSampling() {
      return snapshots != null && !snapshots.isEmpty();
    }

    public void addSnapshot(Snapshot snapshot) {
      if (snapshots == null) {
        snapshots = new ArrayList<>();
      }
      snapshots.add(snapshot);
    }
  }
}
