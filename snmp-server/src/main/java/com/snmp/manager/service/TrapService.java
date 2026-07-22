package com.snmp.manager.service;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.dao.TrapActionDAO;
import com.snmp.manager.dao.TrapHistoryDAO;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import com.snmp.manager.model.TrapAction;
import com.snmp.manager.model.TrapHistory;
import com.snmp.manager.model.TrapSeverity;
import com.snmp.manager.model.TrapStatus;
import com.snmp.manager.snmp.model.TrapEvent;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Business logic for handling received SNMP traps.
 *
 * <p>Coordinates the DAOs to locate the originating node and trap definition,
 * persist a trap history record and update the node status. Contains no SQL
 * strings.</p>
 */
public class TrapService {

    private final NodeDAO nodeDAO;
    private final TrapActionDAO trapActionDAO;
    private final TrapHistoryDAO trapHistoryDAO;
    private final NodeService nodeService;

    public TrapService(NodeDAO nodeDAO,
                       TrapActionDAO trapActionDAO,
                       TrapHistoryDAO trapHistoryDAO,
                       NodeService nodeService) {
        this.nodeDAO = nodeDAO;
        this.trapActionDAO = trapActionDAO;
        this.trapHistoryDAO = trapHistoryDAO;
        this.nodeService = nodeService;
    }

    /**
     * Processes a received trap end to end.
     *
     * <ol>
     *   <li>Locate the sending node by source IP, or auto-register if unknown.</li>
     *   <li>Locate the trap definition by OID.</li>
     *   <li>Persist a trap history record.</li>
     *   <li>Update the node status based on severity.</li>
     * </ol>
     *
     * @param event the parsed trap event, must not be {@code null}
     * @throws SQLException on database access error
     */
    public void process(TrapEvent event) throws SQLException {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        String networkIp = extractIp(event.getSourceIp());
        String nodeIp = event.getNodeIp() != null && !event.getNodeIp().isEmpty() ? event.getNodeIp() : networkIp;
        
        Optional<Node> nodeOpt = nodeDAO.findByIp(nodeIp);

        Node node;
        if (nodeOpt.isPresent()) {
            node = nodeOpt.get();
        } else {
            // Auto-register unknown nodes using data from the trap payload
            node = nodeService.registerNode(event.getNodeName(), nodeIp, event.getNodeType());
            System.out.println("Auto-registered new node: " + node.getName()
                    + " (" + node.getNodeType() + ") at " + nodeIp);
        }

        Optional<TrapAction> actionOpt = trapActionDAO.findByOid(event.getTrapOid());
        TrapAction action = actionOpt.orElse(null);

        TrapHistory history = buildHistory(event, node, action);
        trapHistoryDAO.save(history);

        NodeStatus newStatus = resolveStatus(action);
        nodeService.updateStatus(node, newStatus);
    }

    private TrapHistory buildHistory(TrapEvent event, Node node, TrapAction action) {
        TrapHistory history = new TrapHistory();
        history.setNodeId(node.getId());
        history.setTrapOid(event.getTrapOid());
        // Store the simulated IP (from the node we resolved) instead of the physical network IP
        history.setSourceIp(node.getIpAddress());
        history.setStatus(TrapStatus.OPEN);

        if (action != null) {
            history.setTrapActionId(action.getId());
            // Combine the trap action name with optional details from the emulator
            String message = action.getTrapName();
            if (event.getDetails() != null && !event.getDetails().isEmpty()) {
                message += " - " + event.getDetails();
            }
            history.setMessage(message);
        } else {
            String message = "Unrecognized trap: " + event.getTrapOid();
            if (event.getDetails() != null && !event.getDetails().isEmpty()) {
                message += " - " + event.getDetails();
            }
            history.setMessage(message);
        }
        return history;
    }

    private NodeStatus resolveStatus(TrapAction action) {
        if (action == null) {
            return NodeStatus.WARNING;
        }
        TrapSeverity severity = action.getSeverity();
        return switch (severity) {
            case CRITICAL -> NodeStatus.DOWN;
            case MAJOR, MINOR -> NodeStatus.WARNING;
            case INFO -> NodeStatus.UP;
        };
    }

    private String extractIp(String peerAddress) {
        if (peerAddress == null) {
            return "";
        }
        int slash = peerAddress.indexOf('/');
        return slash >= 0 ? peerAddress.substring(0, slash) : peerAddress;
    }
}
