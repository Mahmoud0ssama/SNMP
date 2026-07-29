package com.snmp.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.TrapAction;
import com.snmp.manager.model.TrapHistory;
import java.util.List;
import java.util.Map;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AiAnalysisService {
    private final DatabaseConnection databaseConnection;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static String geminiApiKey = null;
    private static String fallbackApiKey = null;
    private static boolean keysLoaded = false;

    public AiAnalysisService(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
        if (!keysLoaded) {
            loadApiKey();
            keysLoaded = true;
        }
    }

    private void loadApiKey() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("gemini_api_key.txt")) {
            if (in != null) {
                geminiApiKey = new String(in.readAllBytes()).trim();
            }
        } catch (Exception e) {
            System.err.println("Failed to load primary Gemini API key: " + e.getMessage());
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("gemini_api_key_2.txt")) {
            if (in != null) {
                fallbackApiKey = new String(in.readAllBytes()).trim();
            }
        } catch (Exception e) {
            System.err.println("Failed to load fallback Gemini API key: " + e.getMessage());
        }
    }

    public String generateInsights() {
        if (geminiApiKey == null || geminiApiKey.isEmpty() || geminiApiKey.contains("PUT_YOUR_GEMINI_API_KEY_HERE")) {
            return "Error: Gemini API key is not configured. Please add it to `secrets/gemini_api_key.txt` in the project root.";
        }

        String trapData = fetchFrequentTraps();
        if (trapData.isEmpty()) {
            return "No recent traps found to analyze. Wait for the system to generate traps.";
        }

        String prompt = "You are an expert telecom and Network Operations Center (NOC) engineer. " +
                "Analyze these frequent SNMP traps logged over the last 7 days:\n\n"
                + trapData + "\n\n"
                + "Please provide a professional, structured analysis in Markdown format. " +
                "Identify potential root causes for the repetitive problems and provide a clear, step-by-step remediation plan for the most critical issues.";

        return callGeminiAPI(prompt);
    }

    private String fetchFrequentTraps() {
        String sql = "SELECT trap_oid, message, COUNT(*) as freq " +
                "FROM trap_history " +
                "WHERE received_at > CURRENT_TIMESTAMP - INTERVAL '7 days' " +
                "GROUP BY trap_oid, message " +
                "ORDER BY freq DESC " +
                "LIMIT 20";

        StringBuilder sb = new StringBuilder();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String oid = rs.getString("trap_oid");
                String msg = rs.getString("message");
                int freq = rs.getInt("freq");
                sb.append("- OID: ").append(oid)
                  .append("\n  Message: ").append(msg)
                  .append("\n  Frequency: ").append(freq).append(" times\n\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private String callGeminiAPI(String prompt) {
        return callGeminiAPIInternal(prompt, geminiApiKey, true);
    }

    private String callGeminiAPIInternal(String prompt, String apiKey, boolean allowRetry) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("PUT_YOUR_")) {
            return "Error: API key is not configured.";
        }
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

            ObjectNode rootNode = mapper.createObjectNode();
            ArrayNode contentsNode = rootNode.putArray("contents");
            ObjectNode contentItem = contentsNode.addObject();
            ArrayNode partsNode = contentItem.putArray("parts");
            ObjectNode textPart = partsNode.addObject();
            textPart.put("text", prompt);

            String requestBody = mapper.writeValueAsString(rootNode);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode responseNode = mapper.readTree(response.body());
                return responseNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            } else if (response.statusCode() == 429 && allowRetry && fallbackApiKey != null && !fallbackApiKey.contains("PUT_YOUR_")) {
                System.err.println("Primary API key exhausted quota (429). Switching to fallback key...");
                // Swap keys so future requests use the working one
                String temp = geminiApiKey;
                geminiApiKey = fallbackApiKey;
                fallbackApiKey = temp;
                return callGeminiAPIInternal(prompt, geminiApiKey, false);
            } else {
                System.err.println("Gemini API Error: " + response.body());
                return "⏳ AI quota temporarily exceeded. Please wait try again later.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error communicating with AI service: " + e.getMessage();
        }
    }

    // --- AI SAFETY GATE ---

    public record AiSafetyVerdict(boolean isSafe, String reason) {}

    /**
     * Evaluates whether a proposed script action is safe to execute,
     * based on the current state of all network nodes (Digital Twin snapshot).
     */
    public AiSafetyVerdict evaluateActionSafety(Node faultedNode, TrapAction action, List<Node> allNodes, String scriptContent) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            return new AiSafetyVerdict(true, "AI Gate unavailable (no API key), allowing execution.");
        }

        // Build the Digital Twin snapshot from live node data
        StringBuilder networkState = new StringBuilder();
        for (Node n : allNodes) {
            networkState.append("- ").append(n.getName())
                .append(" (").append(n.getIpAddress()).append(") ")
                .append("[").append(n.getNodeType() != null ? n.getNodeType() : "Unknown").append("] ")
                .append("— Status: ").append(n.getStatus())
                .append("\n");
        }

        String prompt = "You are a Telecom Network Safety AI Agent. "
            + "Your ONLY job is to evaluate whether an automated action is safe to execute.\n\n"
            + "CURRENT NETWORK STATE (Digital Twin):\n" + networkState + "\n"
            + "FAULTED NODE: " + faultedNode.getName() + " (" + faultedNode.getIpAddress() + ")\n"
            + "TRAP RECEIVED: " + action.getTrapName() + " (Severity: " + action.getSeverity() + ")\n"
            + "PROPOSED ACTION: Auto-execute script \"" + action.getTargetPayload() + "\"\n\n"
            + "SCRIPT CONTENTS:\n```bash\n" + scriptContent + "\n```\n\n"
            + "QUESTION: Based on the network topology and the actual script contents, is it safe to execute this script automatically right now? "
            + "Could it cause cascading failures, service outages, or affect other nodes?\n\n"
            + "CRITICAL RULE: The reason MUST be EXTREMELY short (maximum 20 words). Do NOT provide a numbered list. Do NOT over-explain.\n\n"
            + "RESPOND IN VALID JSON ONLY, nothing else: {\"isSafe\": false, \"reason\": \"<max 20 words>\"}";

        try {
            String response = callGeminiAPI(prompt);

            // Smarter JSON extraction: find the first '{' and last '}'
            String cleaned = response;
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start != -1 && end != -1 && end >= start) {
                cleaned = cleaned.substring(start, end + 1);
            }

            JsonNode json = mapper.readTree(cleaned);
            // FAIL-CLOSED DATA PARSING: If the 'isSafe' key is missing, misspelled, or not a boolean, default to FALSE (unsafe).
            boolean isSafe = json.path("isSafe").asBoolean(false);
            String reason = json.path("reason").asText("No reason provided");
            return new AiSafetyVerdict(isSafe, reason);
        } catch (Exception e) {
            System.err.println("AI Safety Gate failed to parse response: " + e.getMessage());
            // FAIL-CLOSED: block execution and explicitly state it was an AI system error
            return new AiSafetyVerdict(false, "AI System Error: Could not parse response from Gemini (" + e.getMessage() + ")");
        }
    }

    // --- NOC CHAT ASSISTANT ---
    public String chatWithNOC(String userMessage, List<Node> allNodes, List<TrapHistory> recentTraps, List<Map<String, String>> conversationHistory) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            return "Error: Gemini API key is not configured. Please add it to `secrets/gemini_api_key.txt` in the project root.";
        }

        StringBuilder networkState = new StringBuilder();
        for (Node n : allNodes) {
            networkState.append("- ID: ").append(n.getId())
                .append(" | Name: ").append(n.getName())
                .append(" (").append(n.getIpAddress()).append(") ")
                .append("[").append(n.getNodeType() != null ? n.getNodeType() : "Unknown").append("] ")
                .append("— Status: ").append(n.getStatus())
                .append("\n");
        }

        StringBuilder trapHistoryStr = new StringBuilder();
        for (TrapHistory t : recentTraps) {
            // Find the node name for this trap
            String nodeName = "Unknown Node (ID " + t.getNodeId() + ")";
            for (Node n : allNodes) {
                if (n.getId().equals(t.getNodeId())) {
                    nodeName = n.getName();
                    break;
                }
            }

            trapHistoryStr.append("- [").append(t.getReceivedAt()).append("] ")
                .append("Node: ").append(nodeName)
                .append(" | Msg: ").append(t.getMessage())
                .append(" | Status: ").append(t.getStatus())
                .append("\n");
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an expert Network Operations Center (NOC) AI Assistant. ")
                     .append("Your job is to answer the engineer's questions, troubleshoot issues, and simulate 'what-if' scenarios based on the live network state.\n\n")
                     .append("CURRENT NETWORK STATE (Digital Twin):\n").append(networkState).append("\n")
                     .append("RECENT ALARM HISTORY (Last 50 traps):\n").append(trapHistoryStr).append("\n")
                     .append("CONVERSATION HISTORY:\n");
                     
        if (conversationHistory != null) {
            for (Map<String, String> msg : conversationHistory) {
                promptBuilder.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
            }
        }
        
        promptBuilder.append("\nENGINEER'S QUESTION:\n").append(userMessage).append("\n\n")
                     .append("Provide a concise, professional, and helpful response. Use Markdown for formatting (bolding, lists, code blocks).");

        return callGeminiAPI(promptBuilder.toString());
    }
}
