package com.snmp.manager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.json.JavalinJackson;
import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.dao.TrapActionDAO;
import com.snmp.manager.dao.TrapHistoryDAO;
import com.snmp.manager.service.NodeService;
import com.snmp.manager.service.TrapService;
import com.snmp.manager.snmp.listener.TrapListener;
import com.snmp.manager.snmp.model.TrapEvent;
import com.snmp.manager.snmp.receiver.TrapReceiver;
import com.snmp.manager.security.JwtUtil;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.io.IOException;

/**
 * Application entry point for the SNMP Manager.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("SNMP Manager Started");

        // --- Web Server Setup ---
        // --- Web Server Setup ---
        Javalin app = Javalin.create(config -> {
            // Serve static files (HTML, CSS, JS) from the "public" folder in resources
            config.staticFiles.add("/public", Location.CLASSPATH);
            
            // Configure Jackson to handle Java 8 Dates (Instant)
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); 
            config.jsonMapper(new JavalinJackson(mapper, true));
        });



        // 1. JWT Security Middleware for all /api/* routes
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            
            // Exclude specific paths from JWT validation (e.g., login)
            if (path.equals("/api/login") || path.equals("/api/test")) {
                return;
            }

            // Extract Token from Authorization header (Bearer <token>)
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new UnauthorizedResponse("Unauthorized: Missing or invalid token");
            }

            String token = authHeader.substring(7); // Remove "Bearer " prefix
            try {
                JwtUtil.verifyToken(token); // Verify token; throws exception if invalid or expired
            } catch (Exception e) {
                throw new UnauthorizedResponse("Unauthorized: Invalid or expired token");
            }
        });

        // 2. Login Endpoint
        app.post("/api/login", ctx -> {
            // Temporary for testing: Generate a default admin token
            // (Later, this will check credentials against the users table)
            String token = JwtUtil.generateToken("admin", "ADMIN");
            ctx.json("{ \"token\": \"" + token + "\" }");
        });
        // Test API endpoint to verify the web server is running
        app.get("/api/test", ctx -> {
            ctx.result("Web Server is working successfully!");
        });

        // --- NEW API: Get all nodes ---
        app.get("/api/nodes", ctx -> {
            try {
                // Initialize database connection
                DatabaseConnection db = DatabaseConnection.fromResource();
                NodeDAO nodeDAO = new NodeDAO(db);
                
                // Fetch all nodes and return them as JSON
                ctx.json(nodeDAO.findAll());
            } catch (Exception e) {
                ctx.status(500).result("Error fetching nodes: " + e.getMessage());
            }
        });
        
        // --- NEW API: Get all trap history ---
        app.get("/api/traps", ctx -> {
            try {
                DatabaseConnection db = DatabaseConnection.fromResource();
                TrapHistoryDAO trapHistoryDAO = new TrapHistoryDAO(db);
                
                // Fetch all traps and return as JSON
                ctx.json(trapHistoryDAO.findAll());
            } catch (Exception e) {
                ctx.status(500).result("Error fetching traps: " + e.getMessage());
            }
        });

        // Start the web server
        app.start(8080);
        // ------------------------

        TrapReceiver receiver = new TrapReceiver();
        receiver.addTrapListener(new PersistenceTrapListener());

        try {
            // Start listening for incoming SNMP traps in the background
            receiver.start(); 
        } catch (IOException e) {
            System.err.println("Failed to start SNMP receiver: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Bridges received traps to the business/service layer.
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

                trapService.process(event);
                System.out.println("Trap persisted: node=" + event.getSourceIp()
                        + ", oid=" + event.getTrapOid());
            } catch (Exception e) {
                System.err.println("Failed to process trap: " + e.getMessage());
            }
        }
    }
}