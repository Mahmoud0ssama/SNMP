package com.snmp.manager.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScriptExecutorTest {

    @Test
    void execute_validScript_returnsSuccess(@TempDir Path tempDir) throws IOException {
        Path script = tempDir.resolve("ok.sh");
        Files.writeString(script, "#!/bin/bash\necho ok\nexit 0\n");
        script.toFile().setExecutable(true);

        ScriptExecutor.ExecutionResult result = ScriptExecutor.execute(script.toString());

        assertTrue(result.success());
        assertTrue(result.message().contains("exit=0"));
    }

    @Test
    void execute_failingScript_returnsFailure(@TempDir Path tempDir) throws IOException {
        Path script = tempDir.resolve("fail.sh");
        Files.writeString(script, "#!/bin/bash\nexit 1\n");
        script.toFile().setExecutable(true);

        ScriptExecutor.ExecutionResult result = ScriptExecutor.execute(script.toString());

        assertFalse(result.success());
        assertTrue(result.message().contains("exit=1"));
    }

    @Test
    void execute_missingPath_returnsFailure() {
        ScriptExecutor.ExecutionResult result = ScriptExecutor.execute("/nonexistent/path.sh");

        assertFalse(result.success());
    }

    @Test
    void execute_blankPath_returnsFailure() {
        ScriptExecutor.ExecutionResult result = ScriptExecutor.execute("   ");

        assertFalse(result.success());
        assertTrue(result.message().contains("Script path is empty"));
    }
}
