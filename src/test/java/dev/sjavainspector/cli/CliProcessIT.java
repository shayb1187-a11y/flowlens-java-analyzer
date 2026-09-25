package dev.sjavainspector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Runs the packaged executable JAR in a separate JVM, exactly as a user would. */
class CliProcessIT {
    @Test @DisplayName("real CLI process exit codes")
    void realProcessExitCodes() throws Exception {
        assertEquals(0, process("check", "examples/valid.sjava", "examples/branching.sjava"));
        assertEquals(1, process("check", "examples/errors.sjava"));
        assertEquals(2, process("check", "examples/does-not-exist.sjava"));
        assertEquals(2, process("gui")); // Deliberately headless JVM.
    }

    private static int process(String... args) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(executable, "-Djava.awt.headless=true",
                "-jar", System.getProperty("flowlens.jar", "target/flowlens.jar")));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }
}
