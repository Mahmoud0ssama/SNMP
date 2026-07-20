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
     *   <li>Locate the sending node by source IP.</li>
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

        String sourceIp = extractIp(event.getSourceIp());
        Optional<Node> nodeOpt = nodeDAO.findByIp(sourceIp);
        if (nodeOpt.isEmpty()) {
            throw new IllegalStateException("No node registered for IP: " + sourceIp);
        }
        Node node = nodeOpt.get();

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
        history.setSourceIp(event.getSourceIp());
        history.setStatus(TrapStatus.OPEN);
        if (action != null) {
            history.setTrapActionId(action.getId());
            history.setMessage(action.getTrapName());
        } else {
            history.setMessage("Unrecognized trap: " + event.getTrapOid());
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
