package com.datadog.crashtracking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ScriptInitializerTest {
  private Path tempDir;

  @BeforeEach
  void setup() throws Exception {
    tempDir = Files.createTempDirectory("dd-test-");
  }

  @AfterEach
  void teardown() throws Exception {
    try (Stream<Path> fileStream = Files.walk(tempDir)) {
      fileStream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
    Files.deleteIfExists(tempDir);
  }

  @Test
  void testCrashUploaderSanity() {
    assertDoesNotThrow(() -> ScriptInitializer.initializeCrashUploader(null, null));
    assertDoesNotThrow(
        () ->
            ScriptInitializer.initializeCrashUploader(
                tempDir.resolve("dummy.sh").toString(), null));
    assertDoesNotThrow(() -> ScriptInitializer.initializeCrashUploader(null, "hs_err.log"));
  }

  @Test
  void testOomeNotifierSanity() {
    assertDoesNotThrow(() -> ScriptInitializer.initializeOOMENotifier(null));
    assertDoesNotThrow(
        () -> ScriptInitializer.initializeOOMENotifier(tempDir.resolve("dummy.sh").toString()));
  }

  @ParameterizedTest
  @MethodSource("crashTrackingScripts")
  void testCrashUploaderInitializationSuccess(String target, String pidArg)
      throws IOException, InterruptedException {
    Path file = tempDir.resolve(target);
    String hsErrFile = "/tmp/hs_err.log";
    ScriptInitializer.initializeCrashUploader(file + pidArg, hsErrFile);
    assertTrue(Files.exists(file), "File " + file + " should have been created");
    List<String> lines = Files.readAllLines(file);
    assertFalse(lines.isEmpty(), "File " + file + " is expected to be non-empty");
    // sanity to check the crash log file was properly replaced in the script
    assertTrue(lines.stream().anyMatch(l -> l.contains(hsErrFile)));
  }

  private static Stream<Arguments> crashTrackingScripts() {
    return Stream.of(
        Arguments.of("dd_crash_uploader.sh", ""),
        Arguments.of("dd_crash_uploader.bat", ""),
        Arguments.of("dd_crash_uploader.sh", " %p"),
        Arguments.of("dd_crash_uploader.bat", " %p"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"dd_oome_notifier.sh", "dd_oome_notifier.bat"})
  void testOomeNotifierInitializationSuccess(String target)
      throws IOException, InterruptedException {
    Path file = tempDir.resolve(target);
    ScriptInitializer.initializeOOMENotifier(file + " %p");
    assertTrue(Files.exists(file), "File " + file + " should have been created");
    List<String> lines = Files.readAllLines(file);
    assertFalse(lines.isEmpty(), "File " + file + " is expected to be non-empty");
    // sanity to check the placeholder was properly replaced
    assertTrue(lines.stream().anyMatch(l -> !l.contains("!TAGS!")));
  }

  @Test
  void testCrashUploaderNoInitialization() throws IOException {
    // the initializer needs a particular script name to kick-in
    Path file = tempDir.resolve("some_other_script.sh");
    String hsErrFile = "/tmp/hs_err.log";
    ScriptInitializer.initializeCrashUploader(file.toString(), hsErrFile);
    assertFalse(Files.exists(file), "File " + file + " should not have been created");
  }

  @Test
  void testOomeNotifierNoInitialization() throws IOException {
    // the initializer needs a particular script name to kick-in
    Path file = tempDir.resolve("some_other_script.sh");
    ScriptInitializer.initializeOOMENotifier(file.toString());
    assertFalse(Files.exists(file), "File " + file + " should not have been created");
  }

  @Test
  void testOomeNotifierInitializationExisting() throws IOException {
    Path file = tempDir.resolve("dd_oome_notifier.sh");
    Files.createFile(file);
    ScriptInitializer.initializeOOMENotifier(file.toString());
    assertTrue(Files.exists(file), "File " + file + " should not have been removed");
    assertTrue(
        Files.readAllLines(file).isEmpty(),
        "File " + file + " content should not have been modified");
  }

  @Test
  void testCrashUploaderNoErrFileSpec() throws IOException {
    Path file = tempDir.resolve("dd_crash_uploader.sh");
    ScriptInitializer.initializeCrashUploader(file.toString(), "");
    assertTrue(Files.exists(file), "File " + file + " should have been created");
    // sanity to check the crash log file was properly replaced in the script
    List<String> lines = Files.readAllLines(file);
    assertFalse(lines.isEmpty(), "File " + file + " is expected to be non-empty");
    // sanity to check the crash log file was properly replaced in the script
    assertTrue(lines.stream().anyMatch(l -> l.contains("hs_err")));
  }

  @Test
  void testCrashUploaderInvalidFolder() throws IOException {
    Files.setPosixFilePermissions(tempDir, PosixFilePermissions.fromString("r-x------"));
    Path file = tempDir.resolve("dd_crash_uploader.sh");
    assertThrows(
        IOException.class,
        () -> ScriptInitializer.initializeCrashUploader(file.toString(), "/tmp/hs_err.log"));
  }

  @Test
  void testOomeInitializeInvalidFolder() throws IOException {
    Files.setPosixFilePermissions(tempDir, PosixFilePermissions.fromString("r-x------"));
    Path file = tempDir.resolve("dd_oome_notifier.sh");
    assertThrows(
        IOException.class, () -> ScriptInitializer.initializeOOMENotifier(file.toString() + " %p"));
  }
}
