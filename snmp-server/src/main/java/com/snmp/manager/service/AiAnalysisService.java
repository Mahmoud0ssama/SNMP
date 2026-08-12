package com.snmp.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.TrapHistory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class AiAnalysisService {
    private final DatabaseConnection databaseConnection;
    private final ObjectMapper mapper = new ObjectMapper();
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1) 
            .connectTimeout(Duration.ofSeconds(20))
            .build();
            
    private static String itiApiKey = null;
    private static boolean keysLoaded = false;

    private static final String ITI_MODEL_ID = "deepseek.v3.2";
    private static final String ITI_BASE_URL = "http://apiaccess.iti.net.eg/api/v1/student/chat";

    public AiAnalysisService(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
        if (!keysLoaded) {
            loadApiKey();
            keysLoaded = true;
        }
    }

    private void loadApiKey() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("iti_api_key.txt")) {
            if (in != null) {
                itiApiKey = new String(in.readAllBytes()).trim();
            }
        } catch (Exception e) {
            System.err.println("Failed to load ITI API key: " + e.getMessage());
        }
    }

    // --- 1. GENERATE INSIGHTS (RCA) ---
    public String generateInsights() {
        if (itiApiKey == null || itiApiKey.isEmpty()) {
            return "Error: ITI API key is not configured. Please add it to `src/main/resources/iti_api_key.txt`.";
        }

        String trapData = fetchFrequentTraps();
        if (trapData.isEmpty()) {
            return "No recent traps found to analyze. Wait for the system to generate traps.";
        }

        String systemPrompt = "You are an expert telecom and Network Operations Center (NOC) engineer. Provide a professional, structured analysis in Markdown format. Identify potential root causes and provide a step-by-step remediation plan.";
        String userPrompt = "Analyze these frequent SNMP traps logged over the last 7 days:\n\n" + trapData;

        return callItiGateway(systemPrompt, userPrompt, null);
    }

    // --- 2. AI SAFETY GATE ---
    public record AiSafetyVerdict(boolean isSafe, String reason) {}

    public AiSafetyVerdict evaluateScriptSafety(String scriptName, String scriptContent) {
        if (itiApiKey == null || itiApiKey.isEmpty()) {
            return new AiSafetyVerdict(true, "AI Gate unavailable (no API key), allowing upload.");
        }

        String systemPrompt = "You are a strict Telecom Network Safety AI. "
            + "EVALUATE the script. RESPOND WITH EXACTLY ONE JSON OBJECT and absolutely nothing else. No markdown, no explanations.\n"
            + "Format: {\"isSafe\": true/false, \"reason\": \"<max 20 words>\"}";
            
        String userPrompt = "SCRIPT NAME: " + scriptName + "\n"
            + "SCRIPT CONTENTS:\n" + scriptContent;

        try {
            String response = callItiGateway(systemPrompt, userPrompt, null);

            String cleaned = response;
            int attrIndex = cleaned.indexOf("\"isSafe\"");
            if (attrIndex != -1) {
                int start = cleaned.lastIndexOf('{', attrIndex);
                int end = cleaned.lastIndexOf('}');
                if (start != -1 && end != -1 && start < end) {
                    cleaned = cleaned.substring(start, end + 1);
                }
            } else {
                // Fallback
                int start = cleaned.indexOf('{');
                int end = cleaned.lastIndexOf('}');
                if (start != -1 && end != -1 && start < end) {
                    cleaned = cleaned.substring(start, end + 1);
                }
            }

            JsonNode json = mapper.readTree(cleaned);
            boolean isSafe = json.path("isSafe").asBoolean(false);
            String reason = json.path("reason").asText("No reason provided");
            return new AiSafetyVerdict(isSafe, reason);
            
        } catch (Exception e) {
            System.err.println("AI Safety Gate failed to parse response: " + e.getMessage());
            return new AiSafetyVerdict(false, "AI System Error: Could not parse response from ITI Gateway.");
        }
    }

    // --- 3. NOC CHAT ASSISTANT ---
    public String chatWithNOC(String userMessage, List<Node> allNodes, List<TrapHistory> recentTraps, List<Map<String, String>> conversationHistory) {
        if (itiApiKey == null || itiApiKey.isEmpty()) {
            return "Error: ITI API key is not configured.";
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

        String systemPrompt = "You are an expert Network Operations Center (NOC) AI Assistant. Answer the engineer's questions, troubleshoot issues, and simulate 'what-if' scenarios based on the live network state. Use Markdown for formatting.";
        
        String userContext = "CURRENT NETWORK STATE (Digital Twin):\n" + networkState + "\n"
                     + "RECENT ALARM HISTORY (Last 50 traps):\n" + trapHistoryStr + "\n"
                     + "ENGINEER'S QUESTION:\n" + userMessage;

        return callItiGateway(systemPrompt, userContext, conversationHistory);
    }

    // --- CORE API CALL (ITI GATEWAY) ---
    private String callItiGateway(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model_id", ITI_MODEL_ID);
            payload.put("system_prompt", systemPrompt);

            ArrayNode messagesArray = payload.putArray("messages");

            if (history != null) {
                for (Map<String, String> msg : history) {
                    ObjectNode histNode = messagesArray.addObject();
                    histNode.put("role", msg.get("role").equals("user") ? "user" : "assistant");
                    histNode.put("content", msg.get("content"));
                }
            }

            ObjectNode userNode = messagesArray.addObject();
            userNode.put("role", "user");
            userNode.put("content", userMessage);

            String requestBody = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ITI_BASE_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + itiApiKey)
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode responseNode = mapper.readTree(response.body());
                
                if (responseNode.has("output_text")) {
                    return responseNode.path("output_text").asText();
                } 
                // 2. Standard OpenAI Format
                else if (responseNode.has("choices") && responseNode.get("choices").isArray()) {
                    return responseNode.path("choices").get(0).path("message").path("content").asText();
                } 
                // 3. AWS Bedrock Converse Format
                else if (responseNode.has("output") && responseNode.get("output").has("message")) {
                    return responseNode.path("output").path("message").path("content").get(0).path("text").asText();
                } 
                // 4. Direct Message Format
                else if (responseNode.has("message")) {
                    return responseNode.path("message").path("content").asText();
                } 
                // Fallback
                else {
                    return responseNode.toString(); 
                }
            } else {
                System.err.println("ITI Gateway Error: " + response.statusCode() + " - " + response.body());
                return "⏳ API Error: " + response.statusCode() + " (Check backend logs for details).";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error communicating with ITI AI service: " + e.getMessage();
        }
    }

    private String fetchFrequentTraps() {
        String sql = "SELECT trap_oid, message, COUNT(*) as freq FROM trap_history " +
                "WHERE received_at > CURRENT_TIMESTAMP - INTERVAL '7 days' " +
                "GROUP BY trap_oid, message ORDER BY freq DESC LIMIT 20";

        StringBuilder sb = new StringBuilder();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                sb.append("- OID: ").append(rs.getString("trap_oid"))
                  .append("\n  Message: ").append(rs.getString("message"))
                  .append("\n  Frequency: ").append(rs.getInt("freq")).append(" times\n\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}