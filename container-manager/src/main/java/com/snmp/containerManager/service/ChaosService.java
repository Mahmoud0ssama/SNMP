package com.snmp.containerManager.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ChaosService {

    private final String scriptsDir = "../docker/chaos";

    public record ChaosResult(boolean success, String message, String output) {}

    public ChaosResult triggerChaos(String type, String containerName) {
        String scriptPath = "";
        switch (type) {
            case "disk_full":
                scriptPath = scriptsDir + "/disk_full.sh";
                break;
            case "high_temp":
                scriptPath = scriptsDir + "/high_temp.sh";
                break;
            case "congestion":
                scriptPath = scriptsDir + "/congestion.sh";
                break;
            case "link_down":
                scriptPath = scriptsDir + "/link_down.sh";
                break;
            case "recover_link":
                scriptPath = scriptsDir + "/recover_link.sh";
                break;
            case "fix_disk_full":
                scriptPath = "../scripts/remediate_disk_full.sh";
                break;
            case "fix_high_temp":
                scriptPath = "../scripts/remediate_high_temp.sh";
                break;
            case "fix_congestion":
                scriptPath = "../scripts/remediate_congestion.sh";
                break;
            default:
                return new ChaosResult(false, "Unknown chaos type: " + type, null);
        }

        try {
            ProcessBuilder pb;
            if (type.startsWith("fix_")) {
                // Fixing scripts are shared with snmp-server and are designed to run INSIDE the container via stdin
                pb = new ProcessBuilder("bash", "-c", "docker exec -i " + containerName + " bash < " + scriptPath);
            } else {
                // Chaos scripts run on the host and issue docker commands themselves
                pb = new ProcessBuilder("bash", scriptPath, containerName);
            }
            
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ChaosResult(false, "Chaos script timed out", output.toString().trim());
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return new ChaosResult(true, "Chaos triggered successfully", output.toString().trim());
            } else {
                return new ChaosResult(false, "Chaos script failed with exit code " + exitCode, output.toString().trim());
            }
        } catch (Exception e) {
            return new ChaosResult(false, "Exception executing chaos script: " + e.getMessage(), null);
        }
    }
}
