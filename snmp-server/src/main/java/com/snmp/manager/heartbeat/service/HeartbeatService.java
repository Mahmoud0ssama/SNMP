package com.snmp.manager.heartbeat.service;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.heartbeat.model.Heartbeat;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business logic for processing received heartbeats.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Validate that the heartbeat originates from a known node.</li>
 *   <li>Update an in-memory cache of last-seen timestamps (no DB churn).</li>
 *   <li>Mark the node {@link NodeStatus#UP} when a heartbeat arrives and the
 *       status had changed.</li>
 * </ul>
 *
 * <p>This class contains no UDP code. Database writes happen only when the
 * node status actually changes.</p>
 */
public class HeartbeatService {

    /** In-memory cache: node id {@code ->} last heartbeat instant. */
    private final Map<Long, Instant> lastHeartbeats = new ConcurrentHashMap<>();

    /** Tracks current cached status to avoid redundant database writes. */
    private final Map<Long, NodeStatus> cachedStatus = new ConcurrentHashMap<>();

    private final NodeDAO nodeDAO;

    public HeartbeatService(NodeDAO nodeDAO) {
        this.nodeDAO = nodeDAO;
    }

    /**
     * Processes a received heartbeat: validates the node, refreshes the
     * in-memory cache and promotes the node to {@code UP} when appropriate.
     *
     * @param heartbeat the parsed heartbeat, never {@code null}
     * @return {@code true} if the heartbeat was accepted and applied
     */
    public boolean process(Heartbeat heartbeat) {
        if (heartbeat == null || heartbeat.getNodeId() == null) {
            return false;
        }

        Optional<Node> nodeOpt = resolveNode(heartbeat.getNodeId());
        if (nodeOpt.isEmpty()) {
            System.err.println("Heartbeat ignored: unknown node '" + heartbeat.getNodeId() + "'");
            return false;
        }

        Node node = nodeOpt.get();
        long id = node.getId();
        lastHeartbeats.put(id, heartbeat.getTimestamp());

        NodeStatus previous = cachedStatus.get(id);
        if (previous == null) {
            previous = node.getStatus();
            cachedStatus.put(id, previous);
        }

        if (previous != NodeStatus.UP) {
            try {
                nodeDAO.updateStatus(id, NodeStatus.UP);
                cachedStatus.put(id, NodeStatus.UP);
                System.out.println("Node " + id + " (" + node.getName() + ") marked UP via heartbeat.");
            } catch (SQLException e) {
                System.err.println("Failed to update status for node " + id + ": " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves the node referenced by a heartbeat identifier. The identifier
     * may be a numeric database id or an IP address.
     */
    private Optional<Node> resolveNode(String nodeId) {
        try {
            Long id = Long.parseLong(nodeId.trim());
            return nodeDAO.findById(id);
        } catch (NumberFormatException e) {
            // Not a numeric id: treat the token as an IP address.
            try {
                return nodeDAO.findByIp(nodeId.trim());
            } catch (SQLException ex) {
                System.err.println("Error resolving node by ip " + nodeId + ": " + ex.getMessage());
                return Optional.empty();
            }
        } catch (SQLException e) {
            System.err.println("Error resolving node by id " + nodeId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /** @return immutable snapshot of last-seen timestamps (node id -&gt; instant). */
    public Map<Long, Instant> getLastHeartbeats() {
        return Map.copyOf(lastHeartbeats);
    }

    /**
     * Seeds the cache with the current status of all known nodes. Call once
     * during startup so the monitor can detect transitions correctly.
     *
     * @throws SQLException on database access error
     */
    public void initializeFromDatabase() throws SQLException {
        List<Node> nodes = nodeDAO.findAll();
        for (Node node : nodes) {
            cachedStatus.put(node.getId(), node.getStatus());
        }
    }
}
