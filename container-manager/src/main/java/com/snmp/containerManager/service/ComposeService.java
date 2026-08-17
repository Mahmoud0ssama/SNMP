package com.snmp.containerManager.service;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class ComposeService {
    
    private final String dockerDir = "../docker";
    private final String composePath = dockerDir + "/docker-compose.yml";
    private final String subnetPrefix = "172.25.0.";
    
    public record ComposeResult(boolean success, String message, Object data) {}

    public ComposeResult listDefinedServices() {
        try (FileInputStream inputStream = new FileInputStream(composePath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            Map<String, Object> services = (Map<String, Object>) data.get("services");
            
            List<Map<String, String>> serviceList = new ArrayList<>();
            if (services != null) {
                for (Map.Entry<String, Object> entry : services.entrySet()) {
                    String name = entry.getKey();
                    Map<String, Object> config = (Map<String, Object>) entry.getValue();
                    String ip = "Unknown";
                    try {
                        Map<String, Object> networks = (Map<String, Object>) config.get("networks");
                        Map<String, Object> telecomNet = (Map<String, Object>) networks.get("telecom_net");
                        ip = (String) telecomNet.get("ipv4_address");
                    } catch (Exception ignored) {}
                    
                    serviceList.add(Map.of("name", name, "ip", ip));
                }
            }
            return new ComposeResult(true, "Success", serviceList);
        } catch (Exception e) {
            return new ComposeResult(false, "Failed to read docker-compose.yml: " + e.getMessage(), null);
        }
    }

    public ComposeResult getNextAvailableIp() {
        try (FileInputStream inputStream = new FileInputStream(composePath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            Map<String, Object> services = (Map<String, Object>) data.get("services");
            
            List<Integer> usedLastOctets = new ArrayList<>();
            usedLastOctets.add(1); // Gateway

            if (services != null) {
                for (Object configObj : services.values()) {
                    try {
                        Map<String, Object> config = (Map<String, Object>) configObj;
                        Map<String, Object> networks = (Map<String, Object>) config.get("networks");
                        Map<String, Object> telecomNet = (Map<String, Object>) networks.get("telecom_net");
                        String ip = (String) telecomNet.get("ipv4_address");
                        if (ip != null && ip.startsWith(subnetPrefix)) {
                            int lastOctet = Integer.parseInt(ip.substring(subnetPrefix.length()));
                            usedLastOctets.add(lastOctet);
                        }
                    } catch (Exception ignored) {}
                }
            }
            
            Collections.sort(usedLastOctets);
            for (int i = 2; i < 255; i++) {
                if (!usedLastOctets.contains(i)) {
                    return new ComposeResult(true, "Success", subnetPrefix + i);
                }
            }
            return new ComposeResult(false, "No IPs available in subnet", null);
        } catch (Exception e) {
            return new ComposeResult(false, "Failed to read IP allocations: " + e.getMessage(), null);
        }
    }

    public ComposeResult validateIp(String ip) {
        if (!ip.startsWith(subnetPrefix)) {
            return new ComposeResult(false, "IP must be in " + subnetPrefix + "0/24 subnet", null);
        }
        
        try {
            int lastOctet = Integer.parseInt(ip.substring(subnetPrefix.length()));
            if (lastOctet < 2 || lastOctet > 254) {
                 return new ComposeResult(false, "IP octet out of range (2-254)", null);
            }
        } catch(Exception e) {
            return new ComposeResult(false, "Invalid IP format", null);
        }
        
        // check if used
        ComposeResult listResult = listDefinedServices();
        if (listResult.success()) {
             List<Map<String, String>> srvs = (List<Map<String, String>>) listResult.data();
             for(Map<String, String> srv : srvs) {
                 if (ip.equals(srv.get("ip"))) {
                     return new ComposeResult(false, "IP is already used by " + srv.get("name"), null);
                 }
             }
        }
        
        return new ComposeResult(true, "IP is valid and available", null);
    }

    public ComposeResult addNode(String serviceName, String ip, String nodeType, String nodeName, String region, String vendor) {
        // Validate IP
        ComposeResult ipCheck = validateIp(ip);
        if (!ipCheck.success()) {
            return ipCheck;
        }

        try {
            // Read YAML
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            Map<String, Object> data;
            try (FileInputStream inputStream = new FileInputStream(composePath)) {
                data = yaml.load(inputStream);
            }

            Map<String, Object> services = (Map<String, Object>) data.get("services");
            if (services.containsKey(serviceName)) {
                return new ComposeResult(false, "Service '" + serviceName + "' already exists in compose file.", null);
            }

            // Create service block
            Map<String, Object> newService = new LinkedHashMap<>();
            Map<String, Object> buildConfig = new LinkedHashMap<>();
            buildConfig.put("context", ".");
            buildConfig.put("dockerfile", "Dockerfile.telecom-node");
            newService.put("build", buildConfig);
            newService.put("container_name", serviceName);
            
            Map<String, Object> telecomNet = new LinkedHashMap<>();
            telecomNet.put("ipv4_address", ip);
            Map<String, Object> networks = new LinkedHashMap<>();
            networks.put("telecom_net", telecomNet);
            newService.put("networks", networks);
            
            List<String> volumes = new ArrayList<>();
            volumes.add("./nodes/" + serviceName + "/hardware_specs.cfg:/etc/node/hardware_specs.cfg:ro");
            newService.put("volumes", volumes);
            
            List<String> environment = new ArrayList<>();
            environment.add("NMS_SERVER_IP=172.25.0.1");
            environment.add("NODE_NAME=" + nodeName);
            environment.add("NODE_TYPE=" + nodeType);
            newService.put("environment", environment);

            services.put(serviceName, newService);

            // Write YAML
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(composePath))) {
                yaml.dump(data, writer);
            }

            // Create config dir and file
            Path nodeDir = Paths.get(dockerDir, "nodes", serviceName);
            Files.createDirectories(nodeDir);
            
            String hwSpecs = "NODE_NAME=" + nodeName + "\n" +
                             "NODE_TYPE=" + nodeType + "\n" +
                             "VENDOR=" + (vendor == null || vendor.isBlank() ? "Unknown" : vendor) + "\n" +
                             "SUBSCRIBERS=100000\n";
            Files.writeString(nodeDir.resolve("hardware_specs.cfg"), hwSpecs);

            // Start it
            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f", composePath, "up", "-d", serviceName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(30, TimeUnit.SECONDS);

            return new ComposeResult(true, "Node added and started", null);
        } catch (Exception e) {
            return new ComposeResult(false, "Error adding node: " + e.getMessage(), null);
        }
    }

    public ComposeResult removeNode(String serviceName) {
         try {
            // Read YAML
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            Map<String, Object> data;
            try (FileInputStream inputStream = new FileInputStream(composePath)) {
                data = yaml.load(inputStream);
            }

            Map<String, Object> services = (Map<String, Object>) data.get("services");
            if (!services.containsKey(serviceName)) {
                return new ComposeResult(false, "Service '" + serviceName + "' does not exist.", null);
            }

            // rm container
            ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", serviceName);
            Process p = pb.start();
            p.waitFor(10, TimeUnit.SECONDS);

            services.remove(serviceName);

            // Write YAML
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(composePath))) {
                yaml.dump(data, writer);
            }

            // Remove config directory
            Path nodeDir = Paths.get(dockerDir, "nodes", serviceName);
            if (Files.exists(nodeDir)) {
                ProcessBuilder pbRm = new ProcessBuilder("rm", "-rf", nodeDir.toString());
                Process pRm = pbRm.start();
                pRm.waitFor(5, TimeUnit.SECONDS);
            }

            return new ComposeResult(true, "Node removed from compose file", null);
        } catch (Exception e) {
            return new ComposeResult(false, "Error removing node: " + e.getMessage(), null);
        }
    }
}
