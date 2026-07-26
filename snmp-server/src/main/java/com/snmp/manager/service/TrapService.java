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
import com.snmp.manager.util.ScriptExecutor;
import com.snmp.manager.util.SmsNotifier;

import java.sql.SQLException;
import java.util.Optional;

/* 
 * Coordinates the DAOs to locate the originating node and trap definition,
 * persist a trap history record and update the node status. 
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
     *   Locate the sending node by source IP, or auto-register if unknown.
     *   Locate the trap definition by OID.
     *   Persist a trap history record
     *   Update the node status based on severity.
     *   Execute any configured action (e.g. run a script).
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
            node = nodeService.registerNode(event.getNodeName(), nodeIp, event.getNodeType());
            System.out.println("Auto-registered new node: " + node.getName()
                    + " (" + node.getNodeType() + ") at " + nodeIp);
        }

        Optional<TrapAction> actionOpt = trapActionDAO.findByNodeAndOid(node.getId(), event.getTrapOid());
        TrapAction action = actionOpt.orElse(null);

        TrapHistory history = buildHistory(event, node, action);
        trapHistoryDAO.save(history);

        NodeStatus newStatus = resolveStatus(action);
        nodeService.updateStatus(node, newStatus);

        executeAction(action, event);
    }

    private void executeAction(TrapAction action, TrapEvent event) {
        if (action == null || action.getActionType() == null) {
            return;
        }
        switch (action.getActionType().toLowerCase()) {
            case "script" -> {
                String scriptPath = action.getTargetPayload();
                if (scriptPath == null || scriptPath.isBlank()) {
                    System.err.println("Script action defined but target_payload is empty for trap OID: " + event.getTrapOid());
                    return;
                }
                ScriptExecutor.ExecutionResult result = ScriptExecutor.execute(scriptPath);
                if (result.success()) {
                    System.out.println("Script executed successfully: " + scriptPath);
                } else {
                    System.err.println("Script execution failed: " + result.message());
                }
            }
            case "sms" -> { sendSms(action, event); }
            case "email" ->
                System.out.println("Action type '" + action.getActionType() + "' is not implemented yet for trap OID: " + event.getTrapOid());
            default ->
                System.out.println("Unknown action type '" + action.getActionType() + "' for trap OID: " + event.getTrapOid());
        }
    }

    private void sendSms(TrapAction action, TrapEvent event) {
        String recipient = action.getTargetPayload();
        if (recipient == null || recipient.isBlank()) {
            System.err.println("SMS action defined but target_payload (recipient) is empty for trap OID: " + event.getTrapOid());
            return;
        }
        try {
            SmsNotifier smsNotifier = SmsNotifier.fromResource();
            String body = String.format(
                    "[SNMP Alert] Trap: %s | Severity: %s | Node: %s",
                    action.getTrapName(),
                    action.getSeverity(),
                    extractIp(event.getSourceIp())
            );
            smsNotifier.send(recipient, body);
            System.out.println("SMS sent to " + recipient + ": " + body);
        } catch (Exception e) {
            System.err.println("Failed to send SMS: " + e.getMessage());
        }
    }

    private TrapHistory buildHistory(TrapEvent event, Node node, TrapAction action) {
        TrapHistory history = new TrapHistory();
        history.setNodeId(node.getId());
        history.setTrapOid(event.getTrapOid());
        history.setSourceIp(node.getIpAddress());
        history.setStatus(TrapStatus.OPEN);

        if (action != null) {
            history.setTrapActionId(action.getId());
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
