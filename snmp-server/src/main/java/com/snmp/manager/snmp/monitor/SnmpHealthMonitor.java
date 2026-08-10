package com.snmp.manager.snmp.monitor;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.heartbeat.service.HeartbeatService;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import com.snmp.manager.snmp.poller.SnmpPoller;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SnmpHealthMonitor implements AutoCloseable {

    private static final int POLL_INTERVAL_SECONDS = 3;
    private static final int FAILURE_THRESHOLD = 3;
    private static final String COMMUNITY_STRING = "public";
    private static final int SNMP_PORT = 161;

    private final SnmpPoller snmpPoller;
    private final NodeDAO nodeDAO;
    private final HeartbeatService heartbeatService;

    private final ScheduledExecutorService scheduler;
    private volatile boolean running;

    private final Map<Long, AtomicInteger> failureCounts = new ConcurrentHashMap<>();

    public SnmpHealthMonitor(SnmpPoller snmpPoller, NodeDAO nodeDAO, HeartbeatService heartbeatService) {
        this.snmpPoller = snmpPoller;
        this.nodeDAO = nodeDAO;
        this.heartbeatService = heartbeatService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snmp-health-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler.scheduleAtFixedRate(this::pollAllNodes, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        System.out.println("SNMP health monitor started (poll every " + POLL_INTERVAL_SECONDS + "s, threshold=" + FAILURE_THRESHOLD + " failures).");
    }

    private void pollAllNodes() {
        if (!running) {
            return;
        }
        try {
            List<Node> nodes = nodeDAO.findAll();
            for (Node node : nodes) {
                try {
                    pollNode(node);
                } catch (Exception e) {
                    System.err.println("Error polling node " + node.getId() + ": " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("SNMP health monitor DB error: " + e.getMessage());
        }
    }

    private void pollNode(Node node) {
        try {
            com.snmp.manager.snmp.poller.SnmpGetResult result = snmpPoller.poll(node.getIpAddress(), SNMP_PORT, COMMUNITY_STRING);
            if (result.reachable()) {
                failureCounts.remove(node.getId());
                heartbeatService.onNodeSeen(node.getId(), Instant.now());
                heartbeatService.updateMetrics(node.getId(), result);
                if (node.getStatus() != NodeStatus.UP) {
                    System.out.println("Node " + node.getId() + " (" + node.getName() + ") marked UP via SNMP poll.");
                }
                
                System.out.println(String.format(
                    "[SNMP POLL] Node %d (%s) - Uptime: %d, CPU Load: %d, MemAvail: %d kB, Disk: %d MB, Temp: %d°C, Congested: %d",
                    node.getId(), result.ipAddress(), result.uptime(), result.cpuLoad(), result.memAvail(), result.diskUsage(), result.temperature(), result.congestion()
                ));
            } else {
                int failures = failureCounts.computeIfAbsent(node.getId(), k -> new AtomicInteger(0)).incrementAndGet();
                if (failures >= FAILURE_THRESHOLD && node.getStatus() != NodeStatus.DOWN) {
                    nodeDAO.updateStatus(node.getId(), NodeStatus.DOWN);
                    heartbeatService.notifyNodeDown(node.getId());
                    System.out.println("Node " + node.getId() + " (" + node.getName() + ") marked DOWN (SNMP poll timeout).");
                }
            }
        } catch (Exception e) {
            System.err.println("Error polling node " + node.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
        System.out.println("SNMP health monitor stopped.");
    }
}
