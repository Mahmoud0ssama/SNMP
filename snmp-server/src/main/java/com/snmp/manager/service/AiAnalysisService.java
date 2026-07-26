package com.snmp.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.snmp.manager.config.DatabaseConnection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AiAnalysisService {
    private final DatabaseConnection databaseConnection;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String geminiApiKey = null;

    public AiAnalysisService(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
        loadApiKey();
    }

    private void loadApiKey() {
        try {
            Path keyPath = Path.of("secrets/gemini_api_key.txt");
            if (!Files.exists(keyPath)) {
                keyPath = Path.of("../secrets/gemini_api_key.txt"); // If running from snmp-server/ directory
            }
            if (Files.exists(keyPath)) {
                this.geminiApiKey = Files.readString(keyPath).trim();
            }
        } catch (Exception e) {
            System.err.println("Failed to load Gemini API key: " + e.getMessage());
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
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

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
            } else {
                System.err.println("Gemini API Error: " + response.body());
                return "Error generating insights from AI. HTTP " + response.statusCode();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error communicating with AI service: " + e.getMessage();
        }
    }
}
