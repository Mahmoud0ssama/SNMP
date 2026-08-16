package com.snmp.containerManager.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

public class DockerService {

    private final ObjectMapper mapper = new ObjectMapper();

    public record DockerResult(boolean success, String message, Object data) {}

    private DockerResult executeDockerCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new DockerResult(false, "Command timed out", null);
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return new DockerResult(true, "Success", output.toString().trim());
            } else {
                return new DockerResult(false, "Command failed with exit code " + exitCode + ":\n" + output.toString(), null);
            }
        } catch (Exception e) {
            return new DockerResult(false, "Exception executing command: " + e.getMessage(), null);
        }
    }

    public DockerResult listContainers() {
        DockerResult res = executeDockerCommand("docker", "ps", "-a", "--format", "{{json .}}");
        if (!res.success()) return res;
        
        List<Map<String, Object>> containers = new ArrayList<>();
        String[] lines = ((String) res.data()).split("\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            try {
                // The format is JSON per line
                @SuppressWarnings("unchecked")
                Map<String, Object> map = mapper.readValue(line, Map.class);
                containers.add(map);
            } catch (Exception ignored) {}
        }
        return new DockerResult(true, "Success", containers);
    }

    public DockerResult getContainerInfo(String name) {
        DockerResult inspectRes = executeDockerCommand("docker", "inspect", name);
        if (!inspectRes.success()) return inspectRes;

        DockerResult statsRes = executeDockerCommand("docker", "stats", "--no-stream", "--format", "{{json .}}", name);
        
        Map<String, Object> result = new HashMap<>();
        try {
            // docker inspect returns a JSON array
            Object inspectData = mapper.readValue((String) inspectRes.data(), Object.class);
            result.put("inspect", inspectData);
            
            if (statsRes.success() && statsRes.data() != null && !((String)statsRes.data()).isBlank()) {
                 Object statsData = mapper.readValue((String) statsRes.data(), Object.class);
                 result.put("stats", statsData);
            }
        } catch(Exception e) {
             return new DockerResult(false, "Failed to parse JSON: " + e.getMessage(), null);
        }

        return new DockerResult(true, "Success", result);
    }

    public DockerResult startContainer(String name) {
        return executeDockerCommand("docker", "start", name);
    }

    public DockerResult stopContainer(String name) {
        return executeDockerCommand("docker", "stop", name);
    }

    public DockerResult restartContainer(String name) {
        return executeDockerCommand("docker", "restart", name);
    }

    public DockerResult removeContainer(String name) {
        return executeDockerCommand("docker", "rm", "-f", name);
    }

    public DockerResult getContainerLogs(String name, String lines) {
        // We do not parse as JSON, just return raw string
        return executeDockerCommand("docker", "logs", "--tail", lines, name);
    }

    public DockerResult execCommand(String name, String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return new DockerResult(false, "Command cannot be empty", null);
        }
        // docker exec <name> sh -c "<cmd>"
        return executeDockerCommand("docker", "exec", name, "sh", "-c", cmd);
    }

    public DockerResult setSimulatorMode(String name, String mode) {
        if (!mode.equals("sensor") && !mode.equals("file")) {
            return new DockerResult(false, "Invalid mode: " + mode, null);
        }
        return executeDockerCommand("bash", "../docker/simulator.sh", "--node", name, "--mode", mode);
    }

    public DockerResult getSimulatorMode(String name) {
        // Return whatever is in mode.cfg, or default to sensor
        DockerResult res = execCommand(name, "cat /var/snmp/mode.cfg 2>/dev/null || echo sensor");
        if (res.success() && res.data() != null) {
            return new DockerResult(true, "Success", res.data().toString().trim());
        }
        return new DockerResult(true, "Success", "sensor");
    }
}
