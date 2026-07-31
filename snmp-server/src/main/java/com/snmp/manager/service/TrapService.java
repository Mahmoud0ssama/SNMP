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
import com.snmp.manager.util.EmailNotifier;
import com.snmp.manager.config.DatabaseConnection;

import java.sql.SQLException;
import java.util.Optional;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

/* 
 * Coordinates the DAOs to locate the originating node and trap definition,
 * persist a trap history record and update the node status. 
*/
public class TrapService {

    private final NodeDAO nodeDAO;
    private final TrapActionDAO trapActionDAO;
    private final TrapHistoryDAO trapHistoryDAO;
    private final NodeService nodeService;
    private final DatabaseConnection databaseConnection;

    public TrapService(NodeDAO nodeDAO,
                       TrapActionDAO trapActionDAO,
                       TrapHistoryDAO trapHistoryDAO,
                       NodeService nodeService,
                       DatabaseConnection databaseConnection) {
        this.nodeDAO = nodeDAO;
        this.trapActionDAO = trapActionDAO;
        this.trapHistoryDAO = trapHistoryDAO;
        this.nodeService = nodeService;
        this.databaseConnection = databaseConnection;
    }

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
        trapHistoryDAO.save(history); // Save method sets the auto-generated ID inside the history object

        NodeStatus newStatus = resolveStatus(action);
        
        if (node.getStatus() != NodeStatus.UNKNOWN) {
            nodeService.updateStatus(node, newStatus);
        }

        executeAction(action, event, history, node);
    }

    private void executeAction(TrapAction action, TrapEvent event, TrapHistory history, Node node) {
        if (action == null || action.getActionType() == null) {
            return;
        }
        
        boolean actionSuccess = false; 

        switch (action.getActionType().toUpperCase()) {
            case "SCRIPT" -> {
                String fileName = action.getTargetPayload();
                if (fileName == null || fileName.isBlank()) {
                    System.err.println("Script action defined but target_payload is empty for trap OID: " + event.getTrapOid());
                    return;
                }

                // Dynamically resolve the absolute path to the scripts directory
                Path currentDir = Paths.get(System.getProperty("user.dir"));
                Path scriptDir;
                if (currentDir.getFileName().toString().equals("snmp-server")) {
                    scriptDir = currentDir.resolveSibling("scripts");
                } else {
                    scriptDir = currentDir.resolve("scripts");
                }
                
                Path fullScriptPath = scriptDir.resolve(fileName).normalize();
                
                String scriptContent = "";
                try {
                    if (Files.exists(fullScriptPath)) {
                        scriptContent = Files.readString(fullScriptPath);
                    }
                } catch (Exception e) {
                    System.err.println("Could not read script file for AI evaluation: " + e.getMessage());
                }

                // --- AI SAFETY GATE ---
                try {
                    AiAnalysisService aiGate = new AiAnalysisService(databaseConnection);
                    List<Node> allNodes = nodeDAO.findAll();
                    AiAnalysisService.AiSafetyVerdict verdict = aiGate.evaluateActionSafety(node, action, allNodes, scriptContent);

                    if (!verdict.isSafe()) {
                        System.out.println("\u2764 AI BLOCKED script execution for node " + node.getName() + ": " + verdict.reason());
                        String blockedMsg = history.getMessage() + " [AI BLOCKED: " + verdict.reason() + "]";
                        trapHistoryDAO.updateMessage(history.getId(), blockedMsg);
                        return; // Do NOT execute the script
                    }
                    System.out.println("\u2764 AI APPROVED script execution for node " + node.getName() + ": " + verdict.reason());
                } catch (Exception e) {
                    // FAIL-CLOSED: if DB crashes or AI is unavailable, block execution
                    System.err.println("AI Safety Gate error (fail-closed), blocking script execution: " + e.getMessage());
                    return; // Do NOT execute the script
                }
                // --- END AI SAFETY GATE ---
                
                ScriptExecutor.ExecutionResult result = ScriptExecutor.execute(fullScriptPath.toString());
                if (result.success()) {
                    System.out.println("Script executed successfully: " + fullScriptPath.toString());
                    actionSuccess = true; 
                } else {
                    System.err.println("Script execution failed: " + result.message());
                }
            }
            case "SMS" -> { sendSms(action, event); actionSuccess = true; }
            case "EMAIL" -> { sendEmail(action, event); actionSuccess = true; }
            default ->
                System.out.println("Unknown action type '" + action.getActionType() + "' for trap OID: " + event.getTrapOid());
        }

        // --- Auto Resolve Logic ---
        // If action succeeded and auto_resolve is enabled for this action
        if (actionSuccess && action.isAutoResolve()) {
            try {
                // Pass null for user ID since the SYSTEM resolved the issue automatically
                trapHistoryDAO.resolveTrap(history.getId(), null);
                System.out.println("System Auto-Resolved trap history ID: " + history.getId());
            } catch (SQLException e) {
                System.err.println("Failed to auto-resolve trap: " + e.getMessage());
            }
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

    private void sendEmail(TrapAction action, TrapEvent event) {
        String recipient = action.getTargetPayload();
        if (recipient == null || recipient.isBlank()) {
            System.err.println("Email action defined but target_payload (recipient) is empty for trap OID: " + event.getTrapOid());
            return;
        }
        try {
            EmailNotifier emailNotifier = EmailNotifier.fromResource();
            String subject = "[SNMP Alert] " + action.getTrapName();
            String body = String.format(
                    "Trap: %s\nSeverity: %s\nNode: %s\nTrap OID: %s\nSource IP: %s",
                    action.getTrapName(),
                    action.getSeverity(),
                    extractIp(event.getSourceIp()),
                    event.getTrapOid(),
                    event.getSourceIp()
            );
            emailNotifier.send(recipient, subject, body);
            System.out.println("Email sent to " + recipient + ": " + subject);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
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