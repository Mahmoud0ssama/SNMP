package com.snmp.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.json.JavalinJackson;
import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.dao.TrapActionDAO;
import com.snmp.manager.dao.TrapHistoryDAO;
import com.snmp.manager.dao.UserDAO;
import com.snmp.manager.model.User;
import com.snmp.manager.service.NodeService;
import com.snmp.manager.service.TrapService;
import com.snmp.manager.snmp.listener.TrapListener;
import com.snmp.manager.snmp.model.TrapEvent;
import com.snmp.manager.snmp.receiver.TrapReceiver;
import com.snmp.manager.security.JwtUtil;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.mindrot.jbcrypt.BCrypt; // Required for password hash verification

import java.io.IOException;
import java.util.Optional;

/**
 * Application entry point for the SNMP Manager.
 * Initializes the Web API server and the background SNMP Trap Receiver.
 */
public class Main {
    
    /**
     * Data Transfer Object (DTO) for parsing incoming login requests.
     */
    static class LoginRequest {
        public String username;
        public String password;
    }

    public static void main(String[] args) {
        System.out.println("SNMP Manager Started");

        // --- Web Server Setup ---
        Javalin app = Javalin.create(config -> {
            // Serve static files (HTML, CSS, JS) from the "public" folder in resources
            config.staticFiles.add("/public", Location.CLASSPATH);
            
            // Configure Jackson to properly handle Java 8 Dates (Instant) serialization
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); 
            config.jsonMapper(new JavalinJackson(mapper, true));
        });

        // 1. Global Security Middleware for API endpoint protection
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            
            // Exclude public endpoints from JWT validation
            if (path.equals("/api/login") || path.equals("/api/test")) {
                return;
            }

            // Extract Token from Authorization header (Format: "Bearer <token>")
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new UnauthorizedResponse("Unauthorized: Missing or invalid token format");
            }

            try {
                // Verify token authenticity and expiration
                JwtUtil.verifyToken(authHeader.substring(7));
            } catch (Exception e) {
                throw new UnauthorizedResponse("Unauthorized: Invalid or expired token");
            }
        });

        // 2. Authentication Endpoint (Login)
        app.post("/api/login", ctx -> {
            LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
            try {
                DatabaseConnection db = DatabaseConnection.fromResource();
                UserDAO userDAO = new UserDAO(db);
                
                // Fetch user from database by username
                Optional<User> userOpt = userDAO.findByUsername(req.username);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    
                    // Verify the provided plaintext password against the BCrypt hash stored in the database
                    if (BCrypt.checkpw(req.password, user.getPasswordHash())) {
                        // Passwords match -> Generate JWT token containing the user ID
                        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
                        
                        // Return token safely as a JSON object
                        ctx.json(java.util.Map.of("token", token));
                        return;
                    }
                }
                throw new UnauthorizedResponse("Invalid username or password");
            } catch (Exception e) {
                ctx.status(500).result("Database connection error");
            }
        });

        // 3. API: Fetch all network nodes
        app.get("/api/nodes", ctx -> {
            try {
                DatabaseConnection db = DatabaseConnection.fromResource();
                NodeDAO nodeDAO = new NodeDAO(db);
                ctx.json(nodeDAO.findAll());
            } catch (Exception e) {
                ctx.status(500).result("Error fetching nodes");
            }
        });
        
        // 4. API: Fetch trap history
        app.get("/api/traps", ctx -> {
            try {
                DatabaseConnection db = DatabaseConnection.fromResource();
                TrapHistoryDAO trapHistoryDAO = new TrapHistoryDAO(db);
                ctx.json(trapHistoryDAO.findAll());
            } catch (Exception e) {
                ctx.status(500).result("Error fetching traps");
            }
        });

        // 5. API: Resolve a specific trap action
        app.put("/api/traps/{id}/resolve", ctx -> {
            try {
                // Extract user ID from the authenticated JWT token
                String token = ctx.header("Authorization").substring(7);
                DecodedJWT jwt = JwtUtil.verifyToken(token);
                Long userId = jwt.getClaim("userId").asLong();
                
                // Extract the target Trap ID from the URL path parameter
                Long trapId = Long.parseLong(ctx.pathParam("id"));
                
                DatabaseConnection db = DatabaseConnection.fromResource();
                TrapHistoryDAO dao = new TrapHistoryDAO(db);
                
                // Update the trap status to RESOLVED and log the resolving user
                boolean success = dao.resolveTrap(trapId, userId);
                if (success) {
                    ctx.json(java.util.Map.of("status", "success"));
                } else {
                    ctx.status(400).json(java.util.Map.of("status", "error", "message", "Trap not found"));
                }
            } catch (Exception e) {
                ctx.status(500).result("Error resolving trap: " + e.getMessage());
            }
        });

        // Start the Web Server on port 8080
        app.start(8080);

        // --- SNMP Receiver Setup ---
        TrapReceiver receiver = new TrapReceiver();
        receiver.addTrapListener(new PersistenceTrapListener());
        try {
            // Start listening for UDP SNMP Trap packets in the background
            receiver.start(); 
        } catch (IOException e) {
            System.err.println("Failed to start SNMP receiver: " + e.getMessage());
        }
    }

    /**
     * Internal listener class bridging the SNMP network layer with the database persistence layer.
     */
    private static class PersistenceTrapListener implements TrapListener {
        @Override
        public void onTrapReceived(TrapEvent event) {
            try {
                DatabaseConnection db = DatabaseConnection.fromResource();
                NodeDAO nodeDAO = new NodeDAO(db);
                TrapActionDAO trapActionDAO = new TrapActionDAO(db);
                TrapHistoryDAO trapHistoryDAO = new TrapHistoryDAO(db);
                NodeService nodeService = new NodeService(nodeDAO);
                TrapService trapService = new TrapService(nodeDAO, trapActionDAO, trapHistoryDAO, nodeService);

                // Process the raw trap, identify severity, and persist history
                trapService.process(event);
            } catch (Exception e) {
                System.err.println("Failed to process incoming trap: " + e.getMessage());
            }
        }
    }
}