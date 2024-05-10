package com.datadog.debugger.exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datadog.debugger.probe.ExceptionProbe;
import com.datadog.debugger.util.ClassNameFiltering;
import datadog.trace.api.Config;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ExceptionProbeManagerTest {
  private final RuntimeException exception = new RuntimeException("test");

  @Test
  public void instrumentStackTrace() {
    ClassNameFiltering classNameFiltering = ClassNameFiltering.allowAll();
    ExceptionProbeManager exceptionProbeManager = new ExceptionProbeManager(classNameFiltering);
    RuntimeException exception = new RuntimeException("test");
    String fingerprint = Fingerprinter.fingerprint(exception, classNameFiltering);
    exceptionProbeManager.createProbesForException(exception.getStackTrace());
    assertFalse(exceptionProbeManager.getProbes().isEmpty());
  }

  @Test
  void instrumentSingleFrame() {
    ClassNameFiltering classNameFiltering =
        new ClassNameFiltering(
            Stream.of(
                    "java.",
                    "jdk.",
                    "sun.",
                    "com.sun.",
                    "org.gradle.",
                    "worker.org.gradle.",
                    "org.junit.")
                .collect(Collectors.toSet()));
    ExceptionProbeManager exceptionProbeManager = new ExceptionProbeManager(classNameFiltering);

    String fingerprint = Fingerprinter.fingerprint(exception, classNameFiltering);
    assertEquals("1c27b291764c9d387fb85247bb7c2711f885aadfbf2f64fed34b2e0c64c5a2", fingerprint);
    exceptionProbeManager.createProbesForException(exception.getStackTrace());
    assertEquals(1, exceptionProbeManager.getProbes().size());
    ExceptionProbe exceptionProbe = exceptionProbeManager.getProbes().iterator().next();
    assertEquals(
        "com.datadog.debugger.exception.ExceptionProbeManagerTest",
        exceptionProbe.getWhere().getTypeName());
  }

  @Test
  void filterAllFrames() {
    Config config = mock(Config.class);
    when(config.getThirdPartyExcludes()).thenReturn(Collections.emptySet());
    when(config.getThirdPartyIncludes())
        .thenReturn(
            Stream.of(
                    ",",
                    "org.gradle.",
                    "worker.org.gradle.",
                    "org.junit.",
                    "com.datadog.debugger.exception.ExceptionProbeManagerTest")
                .collect(Collectors.toSet()));
    ClassNameFiltering classNameFiltering = new ClassNameFiltering(config);
    ExceptionProbeManager exceptionProbeManager = new ExceptionProbeManager(classNameFiltering);
    String fingerprint = Fingerprinter.fingerprint(exception, classNameFiltering);
    assertEquals("7a1e5e1bcc64ee26801d1471245eff6b6e8d7c61d0ea36fe85f3f75d79e42c", fingerprint);
    exceptionProbeManager.createProbesForException(exception.getStackTrace());
    assertEquals(0, exceptionProbeManager.getProbes().size());
  }

  @Test
  void lastCapture() {
    ClassNameFiltering classNameFiltering = ClassNameFiltering.allowAll();
    ExceptionProbeManager exceptionProbeManager = new ExceptionProbeManager(classNameFiltering);
    RuntimeException exception = new RuntimeException("test");
    String fingerprint = Fingerprinter.fingerprint(exception, classNameFiltering);
    exceptionProbeManager.addFingerprint(fingerprint);
    assertTrue(exceptionProbeManager.shouldCaptureException(fingerprint));
    exceptionProbeManager.updateLastCapture(fingerprint);
    assertFalse(exceptionProbeManager.shouldCaptureException(fingerprint));
    Clock clock =
        Clock.fixed(Instant.now().plus(Duration.ofMinutes(61)), Clock.systemUTC().getZone());
    assertTrue(exceptionProbeManager.shouldCaptureException(fingerprint, clock));
  }
}
