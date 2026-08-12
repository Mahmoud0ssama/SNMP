package com.snmp.manager.service;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import com.snmp.manager.snmp.poller.SnmpGetResult;
import com.snmp.manager.snmp.poller.SnmpPoller;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Core service responsible for discovering and registering active network nodes
 * across specified IPv4 address ranges.
 */
public class DiscoveryService {

    private final NodeDAO nodeDAO;
    private final SnmpPoller snmpPoller;
    private static final int MAX_THREADS = 20;

    /**
     * Constructs a new DiscoveryService with required dependencies.
     * 
     * @param nodeDAO Data access object for Node persistence.
     * @param snmpPoller Component for executing SNMP queries.
     */
    public DiscoveryService(NodeDAO nodeDAO, SnmpPoller snmpPoller) {
        this.nodeDAO = nodeDAO;
        this.snmpPoller = snmpPoller;
    }

    /**
     * Executes a concurrent network scan over the provided IP range, persisting newly
     * discovered nodes and updating existing ones.
     * 
     * @param startIp The beginning IPv4 address (inclusive).
     * @param endIp The ending IPv4 address (inclusive).
     * @return A list of successfully discovered and processed nodes.
     */
    public List<Node> discoverAndRegister(String startIp, String endIp) {
        List<SnmpGetResult> results = scanRange(startIp, endIp);
        List<Node> processedNodes = new ArrayList<>();

        for (SnmpGetResult result : results) {
            try {
                var existingNode = nodeDAO.findByIp(result.ipAddress());
                if (existingNode.isPresent()) {
                    Node node = existingNode.get();
                    node.setDescription(result.sysDescr());
                    if (result.nodeInfo() != null) {
                        String[] lines = result.nodeInfo().split("\n");
                        for (String line : lines) {
                            if (line.startsWith("NODE_NAME=")) {
                                node.setName(line.substring("NODE_NAME=".length()).trim());
                            } else if (line.startsWith("NODE_TYPE=")) {
                                node.setNodeType(line.substring("NODE_TYPE=".length()).trim());
                            }
                        }
                    }
                    processedNodes.add(node);
                } else {
                    Node node = new Node();
                    node.setIpAddress(result.ipAddress());
                    node.setPort(161);
                    node.setStatus(NodeStatus.UP);
                    node.setCreatedAt(Instant.now());
                    
                    String sysName = result.sysName();
                    String nodeType = null;
                    
                    if (result.nodeInfo() != null) {
                        String[] lines = result.nodeInfo().split("\n");
                        for (String line : lines) {
                            if (line.startsWith("NODE_NAME=")) {
                                sysName = line.substring("NODE_NAME=".length()).trim();
                            } else if (line.startsWith("NODE_TYPE=")) {
                                nodeType = line.substring("NODE_TYPE=".length()).trim();
                            }
                        }
                    }
                    
                    if (sysName == null || sysName.isBlank()) {
                        sysName = "Unknown_Node_" + result.ipAddress();
                    }
                    
                    node.setName(sysName);
                    node.setNodeType(nodeType);
                    node.setDescription(result.sysDescr());
                    
                    processedNodes.add(node);
                }
            } catch (SQLException e) {
                System.err.println("Database error checking IP " + result.ipAddress() + ": " + e.getMessage());
            }
        }
        
        return processedNodes;
    }
    /**
     * Performs a multi-threaded SNMP scan across the specified IP range.
     * 
     * @param startIp The beginning IPv4 address.
     * @param endIp The ending IPv4 address.
     * @return A list of reachable SNMP query results.
     * @throws IllegalArgumentException if the provided range is invalid or exceeds the threshold.
     */
    private List<SnmpGetResult> scanRange(String startIp, String endIp) {
        long start = ipToLong(startIp);
        long end = ipToLong(endIp);
        
        if (start > end) {
            throw new IllegalArgumentException("Start IP must be less than or equal to End IP.");
        }

        if (end - start > 1000) {
            throw new IllegalArgumentException("Scan range exceeds the maximum limit of 1000 IPs.");
        }

        List<Callable<SnmpGetResult>> tasks = new ArrayList<>();
        
        for (long i = start; i <= end; i++) {
            String ip = longToIp(i);
            tasks.add(() -> snmpPoller.poll(ip, 161, "public"));
        }

        List<SnmpGetResult> successfulResults = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        
        try {
            List<Future<SnmpGetResult>> futures = executor.invokeAll(tasks);
            for (Future<SnmpGetResult> future : futures) {
                SnmpGetResult result = future.get();
                if (result.reachable()) {
                    successfulResults.add(result);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Discovery scan execution failed: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        return successfulResults;
    }

    private static long ipToLong(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address provided: " + ipAddress);
        }
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result |= Long.parseLong(parts[i]) << (24 - (8 * i));
        }
        return result;
    }

    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >> 8) & 0xFF) + "." +
               (ip & 0xFF);
    }
}
