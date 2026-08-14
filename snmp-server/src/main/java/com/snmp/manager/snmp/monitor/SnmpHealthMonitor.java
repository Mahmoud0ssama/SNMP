package com.snmp.manager.snmp.monitor;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.heartbeat.service.HeartbeatService;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import com.snmp.manager.snmp.poller.SnmpGetResult;
import com.snmp.manager.snmp.poller.SnmpPoller;
import com.snmp.manager.snmp.model.TrapEvent;
import com.snmp.manager.service.TrapService;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SnmpHealthMonitor implements AutoCloseable {

    private static final int HEARTBEAT_INTERVAL_SECONDS = 3;
    private static final int METRICS_INTERVAL_SECONDS = 15;
    private static final int FAILURE_THRESHOLD = 3;
    private static final String COMMUNITY_STRING = "public";
    private static final int SNMP_PORT = 161;

    private final SnmpPoller snmpPoller;
    private final NodeDAO nodeDAO;
    private final HeartbeatService heartbeatService;
    private final TrapService trapService; 

    private final ScheduledExecutorService heartbeatScheduler;
    private final ScheduledExecutorService metricsScheduler;
    private final ExecutorService workerPool;
    
    private volatile boolean running;
    private final Map<Long, AtomicInteger> failureCounts = new ConcurrentHashMap<>();

    public SnmpHealthMonitor(SnmpPoller snmpPoller, NodeDAO nodeDAO, HeartbeatService heartbeatService, TrapService trapService) {
        this.snmpPoller = snmpPoller;
        this.nodeDAO = nodeDAO;
        this.heartbeatService = heartbeatService;
        this.trapService = trapService;

        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-scheduler");
            t.setDaemon(true);
            return t;
        });

        this.metricsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-scheduler");
            t.setDaemon(true);
            return t;
        });

        this.workerPool = Executors.newFixedThreadPool(50, r -> {
            Thread t = new Thread(r, "snmp-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) return;
        running = true;

        heartbeatScheduler.scheduleAtFixedRate(this::doHeartbeatSweep, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        metricsScheduler.scheduleAtFixedRate(this::doMetricsSweep, 5, METRICS_INTERVAL_SECONDS, TimeUnit.SECONDS);

        System.out.println("========================================");
        System.out.println("  ENTERPRISE SNMP MONITOR STARTED       ");
        System.out.println("========================================");
    }

    private void doHeartbeatSweep() {
        if (!running) return;
        try {
            List<Node> nodes = nodeDAO.findAll();
            for (Node node : nodes) {
                workerPool.submit(() -> processHeartbeat(node));
            }
        } catch (SQLException e) {
            System.err.println("Heartbeat Sweep DB error: " + e.getMessage());
        }
    }

    private void doMetricsSweep() {
        if (!running) return;
        try {
            List<Node> nodes = nodeDAO.findAll();
            for (Node node : nodes) {
                if (node.getStatus() == NodeStatus.UP || node.getStatus() == NodeStatus.WARNING) {
                    workerPool.submit(() -> processMetrics(node));
                }
            }
        } catch (SQLException e) {
            System.err.println("Metrics Sweep DB error: " + e.getMessage());
        }
    }

    private void processHeartbeat(Node node) {
        try {
            SnmpGetResult result = snmpPoller.pollHeartbeat(node.getIpAddress(), SNMP_PORT, COMMUNITY_STRING);
            if (result.reachable()) {
                failureCounts.remove(node.getId());
                heartbeatService.onNodeSeen(node.getId(), Instant.now());
                
            }else {
                int failures = failureCounts.computeIfAbsent(node.getId(), k -> new AtomicInteger(0)).incrementAndGet();
                if (failures == FAILURE_THRESHOLD && node.getStatus() != NodeStatus.DOWN) {
                    nodeDAO.updateStatus(node.getId(), NodeStatus.DOWN);
                    heartbeatService.notifyNodeDown(node.getId());
                    System.out.println("[HEARTBEAT] Node " + node.getName() + " marked DOWN (Timeout).");
                    TrapEvent internalTrap = new TrapEvent(
                            node.getIpAddress(),
                            "1.3.6.1.4.1.99999.0.0", 
                            Instant.now(),
                            "Internal",
                            "System",
                            null,
                            node.getName(),
                            node.getNodeType(),
                            node.getIpAddress(),
                            "Critical: Loss of Communication. Heartbeat timeout."
                    );
                    
                    try {
                        trapService.process(internalTrap);
                    } catch (Exception ex) {
                        System.err.println("Failed to process internal unreachable trap: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Heartbeat error on " + node.getName() + ": " + e.getMessage());
        }
    }

    private void processMetrics(Node node) {
        try {
            SnmpGetResult result = snmpPoller.pollMetrics(node.getIpAddress(), SNMP_PORT, COMMUNITY_STRING);
            if (result.reachable()) {
                heartbeatService.updateMetrics(node.getId(), result);
                
                System.out.printf("[METRICS] %s - CPU: %d%%, Mem: %d MB, Disk: %d MB, Temp: %d°C%n",
                        node.getName(), result.cpuLoad(), (result.memAvail()/1024), result.diskUsage(), result.temperature());
            }
        } catch (Exception e) {}
    }

    @Override
    public void close() {
        running = false;
        heartbeatScheduler.shutdownNow();
        metricsScheduler.shutdownNow();
        workerPool.shutdownNow();
    }
}