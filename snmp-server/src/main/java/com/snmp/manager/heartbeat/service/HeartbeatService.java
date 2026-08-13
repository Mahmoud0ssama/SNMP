package com.snmp.manager.heartbeat.service;
import com.snmp.manager.snmp.poller.SnmpGetResult;
import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class HeartbeatService {

    private final Map<Long, NodeStatus> cachedStatus = new ConcurrentHashMap<>();
    // Cache for Live Metrics
    private final Map<Long, SnmpGetResult> cachedMetrics = new ConcurrentHashMap<>();

    public void updateMetrics(long nodeId, SnmpGetResult metrics) {
        cachedMetrics.put(nodeId, metrics);
    }

    public SnmpGetResult getMetrics(long nodeId) {
        return cachedMetrics.get(nodeId);
    }

    public void syncStatus(long nodeId, NodeStatus status) {
        cachedStatus.put(nodeId, status);
    }

    private final NodeDAO nodeDAO;
    private Consumer<Long> onNodeRecovered;

    public HeartbeatService(NodeDAO nodeDAO) {
        this.nodeDAO = nodeDAO;
    }

    public void setOnNodeRecovered(Consumer<Long> onNodeRecovered) {
        this.onNodeRecovered = onNodeRecovered;
    }

    public boolean onNodeSeen(long nodeId, Instant timestamp) {
        Optional<Node> nodeOpt;
        try {
            nodeOpt = nodeDAO.findById(nodeId);
        } catch (SQLException e) {
            System.err.println("Failed to resolve node " + nodeId + ": " + e.getMessage());
            return false;
        }
        if (nodeOpt.isEmpty()) {
            System.err.println("Heartbeat ignored: unknown node id '" + nodeId + "'");
            return false;
        }

        Node node = nodeOpt.get();

        NodeStatus previous = cachedStatus.get(nodeId);
        if (previous == null) {
            previous = node.getStatus();
            cachedStatus.put(nodeId, previous);
        }

        if (previous == NodeStatus.DOWN || previous == NodeStatus.UNKNOWN) {
            if (onNodeRecovered != null) {
                onNodeRecovered.accept(nodeId);
            } else {
                try {
                    nodeDAO.updateStatus(nodeId, NodeStatus.UP);
                    cachedStatus.put(nodeId, NodeStatus.UP);
                    System.out.println("Node " + nodeId + " (" + node.getName() + ") marked UP via heartbeat.");
                } catch (SQLException e) {
                    System.err.println("Failed to update status for node " + nodeId + ": " + e.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

    public void notifyNodeDown(long id) {
        cachedStatus.put(id, NodeStatus.DOWN);
    }

    public void initializeFromDatabase() throws SQLException {
        List<Node> nodes = nodeDAO.findAll();
        for (Node node : nodes) {
            cachedStatus.put(node.getId(), node.getStatus());
        }
    }
}
