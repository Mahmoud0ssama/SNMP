package com.snmp.manager.heartbeat.monitor;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.heartbeat.service.HeartbeatService;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically scans the in-memory heartbeat cache and marks nodes
 * {@link NodeStatus#DOWN} when their last heartbeat is older than the
 * configured timeout.
 *
 * <p>The monitor never queries the database for every check; it only reads the
 * in-memory cache maintained by {@link HeartbeatService}. The database is
 * written to only when a node's status actually transitions.</p>
 */
public class NodeHealthMonitor implements AutoCloseable {

    /** Default timeout after which a silent node is considered down. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** Default interval between health scans. */
    public static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(5);

    private final HeartbeatService heartbeatService;
    private final NodeDAO nodeDAO;
    private final Duration timeout;
    private final Duration checkInterval;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "node-health-monitor");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean running;

    public NodeHealthMonitor(HeartbeatService heartbeatService, NodeDAO nodeDAO) {
        this(heartbeatService, nodeDAO, DEFAULT_TIMEOUT, DEFAULT_CHECK_INTERVAL);
    }

    public NodeHealthMonitor(HeartbeatService heartbeatService, NodeDAO nodeDAO,
                             Duration timeout, Duration checkInterval) {
        this.heartbeatService = Objects.requireNonNull(heartbeatService, "heartbeatService must not be null");
        this.nodeDAO = Objects.requireNonNull(nodeDAO, "nodeDAO must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.checkInterval = Objects.requireNonNull(checkInterval, "checkInterval must not be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (checkInterval.isNegative() || checkInterval.isZero()) {
            throw new IllegalArgumentException("checkInterval must be positive");
        }
    }

    /** Starts the periodic health scan on a background thread. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler.scheduleAtFixedRate(this::scan, checkInterval.toSeconds(), checkInterval.toSeconds(), TimeUnit.SECONDS);
        System.out.println("Node health monitor started (timeout=" + timeout.getSeconds()
                + "s, check every " + checkInterval.getSeconds() + "s)");
    }

    /** Performs a single health scan across all known nodes. */
    void scan() {
        if (!running) {
            return;
        }
        try {
            List<Node> nodes = nodeDAO.findAll();
            Map<Long, Instant> lastSeen = heartbeatService.getLastHeartbeats();
            Instant now = Instant.now();

            for (Node node : nodes) {
                if (node.getStatus() == NodeStatus.DOWN) {
                    continue; // already down; avoid redundant work
                }
                Instant last = lastSeen.get(node.getId());
                if (last == null || Duration.between(last, now).compareTo(timeout) > 0) {
                    markDown(node);
                }
            }
        } catch (SQLException e) {
            System.err.println("Health monitor DB error: " + e.getMessage());
        }
    }

    private void markDown(Node node) {
        try {
            nodeDAO.updateStatus(node.getId(), NodeStatus.DOWN);
            System.out.println("Node " + node.getId() + " (" + node.getName() + ") marked DOWN (heartbeat timeout).");
        } catch (SQLException e) {
            System.err.println("Failed to mark node " + node.getId() + " DOWN: " + e.getMessage());
        }
    }

    /** Stops the monitor. */
    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
        System.out.println("Node health monitor stopped.");
    }
}
