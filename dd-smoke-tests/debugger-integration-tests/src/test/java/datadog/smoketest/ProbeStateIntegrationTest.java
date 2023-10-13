package datadog.smoketest;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datadog.debugger.agent.Configuration;
import com.datadog.debugger.agent.ProbeStatus;
import com.datadog.debugger.probe.LogProbe;
import com.datadog.debugger.sink.Snapshot;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ProbeStateIntegrationTest extends ServerAppDebuggerIntegrationTest {
  @Test
  @DisplayName("testAddRemoveProbes")
  void testAddRemoveProbes() throws Exception {
    LogProbe logProbe =
        LogProbe.builder().probeId(PROBE_ID).where(TEST_APP_CLASS_NAME, FULL_METHOD_NAME).build();
    addProbe(logProbe);
    waitForInstrumentation(appUrl);
    execute(appUrl, FULL_METHOD_NAME);
    List<Snapshot> snapshots = waitForSnapshots();
    assertEquals(1, snapshots.size());
    assertEquals(FULL_METHOD_NAME, snapshots.get(0).getProbe().getLocation().getMethod());
    setCurrentConfiguration(createConfig(Collections.emptyList())); // remove probes
    waitForReTransformation(appUrl);
    addProbe(logProbe);
    waitForInstrumentation(appUrl);
    execute(appUrl, FULL_METHOD_NAME);
    snapshots = waitForSnapshots();
    assertEquals(1, snapshots.size());
    assertEquals(FULL_METHOD_NAME, snapshots.get(0).getProbe().getLocation().getMethod());
  }

  @Test
  @DisplayName("testDisableEnableProbes")
  void testDisableEnableProbes() throws Exception {
    LogProbe logProbe =
        LogProbe.builder().probeId(PROBE_ID).where(TEST_APP_CLASS_NAME, FULL_METHOD_NAME).build();
    addProbe(logProbe);
    waitForInstrumentation(appUrl);
    execute(appUrl, FULL_METHOD_NAME);
    List<Snapshot> snapshots = waitForSnapshots();
    assertEquals(1, snapshots.size());
    assertEquals(FULL_METHOD_NAME, snapshots.get(0).getProbe().getLocation().getMethod());
    setCurrentConfiguration(createConfig(Collections.emptyList())); // no probe
    waitForReTransformation(appUrl);
    addProbe(logProbe);
    waitForInstrumentation(appUrl);
    execute(appUrl, FULL_METHOD_NAME);
    snapshots = waitForSnapshots();
    assertEquals(1, snapshots.size());
    assertEquals(FULL_METHOD_NAME, snapshots.get(0).getProbe().getLocation().getMethod());
  }

  @Test
  @DisplayName("testDisableEnableProbesUsingDenyList")
  void testDisableEnableProbesUsingDenyList() throws Exception {
    LogProbe logProbe =
        LogProbe.builder().probeId(PROBE_ID).where(TEST_APP_CLASS_NAME, FULL_METHOD_NAME).build();
    addProbe(logProbe);
    waitForInstrumentation(appUrl);
    execute(appUrl, FULL_METHOD_NAME);
    List<Snapshot> snapshots = waitForSnapshots();
    assertEquals(1, snapshots.size());
    assertEquals(FULL_METHOD_NAME, snapshots.get(0).getProbe().getLocation().getMethod());

    datadogAgentServer.enqueue(EMPTY_200_RESPONSE); // expect BLOCKED status
    Configuration.FilterList denyList =
        new Configuration.FilterList(asList("datadog.smoketest.debugger"), Collections.emptyList());
    setCurrentConfiguration(createConfig(asList(logProbe), null, denyList));
    waitForReTransformation(appUrl);
    waitForAProbeStatus(ProbeStatus.Status.BLOCKED);

    datadogAgentServer.enqueue(EMPTY_200_RESPONSE); // expect INSTALLED status
    addProbe(logProbe);
    // waitForInstrumentation(appUrl);
    waitForReTransformation(appUrl);
    waitForAProbeStatus(ProbeStatus.Status.INSTALLED);
    execute(appUrl, FULL_METHOD_NAME);
    snapshots = waitForSnapshots();
    assertEquals(1, snapshots.size());
    assertEquals(FULL_METHOD_NAME, snapshots.get(0).getProbe().getLocation().getMethod());
  }

  @Test
  @DisplayName("testDisableEnableProbesUsingAllowList")
  void testDisableEnableProbesUsingAllowList() throws Exception {
    LogProbe logProbe =
        LogProbe.builder().probeId(PROBE_ID).where(TEST_APP_CLASS_NAME, FULL_METHOD_NAME).build();
    addProbe(logProbe);
    waitForInstrumentation(appUrl);
    execute(appUrl, FULL_METHOD_NAME);
    List<Snapshot> snapshots = waitForSnapshots();
    assertEquals(1, snapshots.size());
    assertEquals(FULL_METHOD_NAME, snapshots.get(0).getProbe().getLocation().getMethod());

    datadogAgentServer.enqueue(EMPTY_200_RESPONSE); // expect BLOCKED status
    Configuration.FilterList allowList =
        new Configuration.FilterList(asList("datadog.not.debugger"), Collections.emptyList());
    setCurrentConfiguration(createConfig(asList(logProbe), allowList, null));
    waitForReTransformation(appUrl);
    waitForAProbeStatus(ProbeStatus.Status.BLOCKED);

    datadogAgentServer.enqueue(EMPTY_200_RESPONSE); // expect INSTALLED status
    addProbe(logProbe);
    // waitForInstrumentation(appUrl);
    waitForReTransformation(appUrl);
    waitForAProbeStatus(ProbeStatus.Status.INSTALLED);
    execute(appUrl, FULL_METHOD_NAME);
    snapshots = waitForSnapshots();
    assertEquals(1, snapshots.size());
    assertEquals(FULL_METHOD_NAME, snapshots.get(0).getProbe().getLocation().getMethod());
  }

  @Test
  @DisplayName("testProbeStatusError")
  public void testProbeStatusError() throws Exception {
    LogProbe logProbe =
        LogProbe.builder()
            .probeId(PROBE_ID)
            .where(TEST_APP_CLASS_NAME, "unknownMethodName")
            .build();
    addProbe(logProbe);
    AtomicBoolean received = new AtomicBoolean(false);
    AtomicBoolean error = new AtomicBoolean(false);
    registerProbeStatusListener(
        probeStatus -> {
          if (probeStatus.getDiagnostics().getStatus() == ProbeStatus.Status.RECEIVED) {
            received.set(true);
          }
          if (probeStatus.getDiagnostics().getStatus() == ProbeStatus.Status.ERROR) {
            assertEquals(
                "Cannot find method datadog/smoketest/debugger/ServerDebuggerTestApplication::unknownMethodName",
                probeStatus.getDiagnostics().getException().getMessage());
            error.set(true);
          }
          return received.get() && error.get();
        });
  }
}
