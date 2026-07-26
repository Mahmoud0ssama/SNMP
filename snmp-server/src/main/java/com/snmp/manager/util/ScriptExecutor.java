package com.snmp.manager.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

// Executes local scripts triggered by trap actions.
public class ScriptExecutor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    // Runs the script at the given path.
    public static ExecutionResult execute(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return new ExecutionResult(false, "Script path is empty");
        }

        ProcessBuilder processBuilder = new ProcessBuilder("bash", scriptPath);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            boolean finished = process.waitFor(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(false, "Script timed out after " + DEFAULT_TIMEOUT.getSeconds() + "s: " + scriptPath);
            }

            int exitCode = process.exitValue();
            String message = "Script executed: " + scriptPath + " (exit=" + exitCode + ")";
            if (!output.isEmpty()) {
                message += "\n" + output.toString().trim();
            }
            return new ExecutionResult(exitCode == 0, message);
        } catch (IOException e) {
            return new ExecutionResult(false, "Failed to execute script " + scriptPath + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutionResult(false, "Script execution interrupted: " + scriptPath);
        }
    }

    public record ExecutionResult(boolean success, String message) {
    }
}
