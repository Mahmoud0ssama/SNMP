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
import com.snmp.manager.model.Node;
import com.snmp.manager.model.User;
import com.snmp.manager.service.NodeService;
import com.snmp.manager.service.TrapService;
import com.snmp.manager.service.AiAnalysisService;
import com.snmp.manager.snmp.listener.TrapListener;
import com.snmp.manager.snmp.model.TrapEvent;
import com.snmp.manager.snmp.receiver.TrapReceiver;
import com.snmp.manager.security.JwtUtil;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import com.snmp.manager.model.TrapAction;
import com.snmp.manager.model.TrapSeverity;
import io.javalin.http.UploadedFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Main {

    // DTOs for incoming API requests
    static class LoginReq {

        public String username;
        public String password;
    }

    static class UserReq {

        public String username;
        public String password;
        public String role;
    }

    static class UpdateReq {

        public String value;
    }

    static class ChatReq {
        public String message;
        public List<Map<String, String>> history;
    }

    static class TrapActionReq {

        public String trapOid;
        public String trapName;
        public String severity;
        public String actionType;
        public String targetPayload;
        public boolean autoResolve;
    }

    static class NodeReq {

        public String name;
        public String ipAddress;
        public String nodeType;
        public List<TrapActionReq> trapActions;
    }

    static class DiscoveryReq {
        public String startIp;
        public String endIp;
    }

    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.out.println("SNMP Manager Started");

        // --- SINGLETON INITIALIZATION ---
        DatabaseConnection db;
        try {
            db = DatabaseConnection.fromResource();
        } catch (IOException e) {
            System.err.println("Fatal error: Could not initialize database connection pool.");
            e.printStackTrace();
            return;
        }
        
        // Cache DAOs to reuse across all requests
        UserDAO userDAO = new UserDAO(db);
        NodeDAO nodeDAO = new NodeDAO(db);
        TrapActionDAO trapActionDAO = new TrapActionDAO(db);
        TrapHistoryDAO trapHistoryDAO = new TrapHistoryDAO(db);
        NodeService nodeService = new NodeService(nodeDAO, trapActionDAO);
        AiAnalysisService aiService = new AiAnalysisService(db);
        TrapService trapService = new TrapService(nodeDAO, trapActionDAO, trapHistoryDAO, nodeService, db);
        com.snmp.manager.snmp.poller.SnmpPoller snmpPoller = new com.snmp.manager.snmp.poller.SnmpPoller();
        com.snmp.manager.service.DiscoveryService discoveryService = new com.snmp.manager.service.DiscoveryService(nodeDAO, snmpPoller);


        // Make sure pool is closed on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(db::close));

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            config.jsonMapper(new JavalinJackson(mapper, true));
        });

        // Security Middleware
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if (path.equals("/api/login")) {
                return;
            }

            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new UnauthorizedResponse("Missing token");
            }
            try {
                DecodedJWT jwt = JwtUtil.verifyToken(authHeader.substring(7));
                ctx.attribute("jwt", jwt);
            } catch (Exception e) {
                throw new UnauthorizedResponse("Invalid token");
            }
        });

        // Auth Login API
        app.post("/api/login", ctx -> {
            LoginReq req = ctx.bodyAsClass(LoginReq.class);

            Optional<User> userOpt = userDAO.findByUsername(req.username);
            if (userOpt.isPresent() && BCrypt.checkpw(req.password, userOpt.get().getPasswordHash())) {
                User u = userOpt.get();
                String token = JwtUtil.generateToken(u.getId(), u.getUsername(), u.getRole());
                ctx.json(Map.of("token", token, "role", u.getRole()));
                return;
            }
            throw new UnauthorizedResponse("Invalid credentials");
        });

        // --- DASHBOARD APIs ---
        app.get("/api/nodes", ctx -> {
            ctx.json(nodeDAO.findAll());
        });

        app.get("/api/traps", ctx -> {
            ctx.json(trapHistoryDAO.findAll());
        });

        app.put("/api/traps/{id}/resolve", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            Long trapId = Long.parseLong(ctx.pathParam("id"));
            boolean success = trapHistoryDAO.resolveTrap(trapId, jwt.getClaim("userId").asLong());
            if (success) {
                ctx.json(Map.of("status", "success"));
            } else {
                ctx.status(400).result("Error resolving trap");
            }
        });

        app.get("/api/ai/insights", ctx -> {
            String result = aiService.generateInsights();
            ctx.json(Map.of("markdown", result));
        });

        app.post("/api/ai/chat", ctx -> {
            ChatReq req = ctx.bodyAsClass(ChatReq.class);
            
            List<Node> allNodes = nodeDAO.findAll();
            List<com.snmp.manager.model.TrapHistory> recentTraps = trapHistoryDAO.findAll();
            if (recentTraps.size() > 50) {
                recentTraps = recentTraps.subList(0, 50);
            }
            
            String response = aiService.chatWithNOC(req.message, allNodes, recentTraps, req.history);
            
            ctx.json(Map.of("response", response));
        });

        // --- USER MANAGEMENT APIs ---
        app.get("/api/users", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (!"ADMIN".equals(jwt.getClaim("role").asString())) {
                throw new UnauthorizedResponse("Admin access required");
            }

            List<User> users = userDAO.findAll();
            List<Map<String, Object>> safeUsers = users.stream()
                    .map(u -> java.util.Map.<String, Object>of(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "role", u.getRole()
            ))
                    .collect(Collectors.toList());
            ctx.json(safeUsers);
        });

        app.post("/api/users", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (!"ADMIN".equals(jwt.getClaim("role").asString())) {
                throw new UnauthorizedResponse("Admin access required");
            }

            UserReq req = ctx.bodyAsClass(UserReq.class);
            User u = new User();
            u.setUsername(req.username);
            u.setPasswordHash(BCrypt.hashpw(req.password, BCrypt.gensalt()));
            u.setRole(req.role != null ? req.role : "SUPPORT");

            try {
                userDAO.save(u);
                ctx.json(Map.of("status", "success"));
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "Username already exists. Please choose another one."));
            }
        });

        app.delete("/api/users/{id}", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (!"ADMIN".equals(jwt.getClaim("role").asString())) {
                throw new UnauthorizedResponse("Admin access required");
            }

            Long targetId = Long.parseLong(ctx.pathParam("id"));
            if (targetId == jwt.getClaim("userId").asLong()) {
                ctx.status(400).json(Map.of("error", "Cannot delete yourself"));
                return;
            }
            userDAO.delete(targetId);
            ctx.json(Map.of("status", "success"));
        });

        app.put("/api/users/{id}", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            Long targetId = Long.parseLong(ctx.pathParam("id"));

            boolean isAdmin = "ADMIN".equals(jwt.getClaim("role").asString());
            boolean isSelf = targetId == jwt.getClaim("userId").asLong();
            if (!isAdmin && !isSelf) {
                throw new UnauthorizedResponse("Unauthorized access");
            }

            UserReq req = ctx.bodyAsClass(UserReq.class);

            String newRole = isAdmin ? req.role : jwt.getClaim("role").asString();

            try {
                if (req.password != null && !req.password.trim().isEmpty()) {
                    userDAO.updateWithPassword(targetId, req.username, BCrypt.hashpw(req.password, BCrypt.gensalt()), newRole);
                } else {
                    userDAO.update(targetId, req.username, newRole);
                }
                ctx.json(Map.of("status", "success"));
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "Username already exists. Please choose another one."));
            }
        });

        // --- TRAP CONFIGURATION APIs ---
        app.get("/api/nodes/{id}/trapActions", ctx -> {
            Long targetId = Long.parseLong(ctx.pathParam("id"));
            ctx.json(trapActionDAO.findByNodeId(targetId));
        });

        app.post("/api/upload-script", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt"); // Requires auth
            UploadedFile uploadedFile = ctx.uploadedFile("file");
            if (uploadedFile == null) {
                ctx.status(400).result("No file uploaded");
                return;
            }

            byte[] fileBytes = uploadedFile.content().readAllBytes();
            String scriptContent = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);

            com.snmp.manager.service.AiAnalysisService aiGate = new com.snmp.manager.service.AiAnalysisService(db);
            com.snmp.manager.service.AiAnalysisService.AiSafetyVerdict verdict = aiGate.evaluateScriptSafety(uploadedFile.filename(), scriptContent);

            if (!verdict.isSafe()) {
                ctx.status(400).result("AI Safety Violation: " + verdict.reason());
                return;
            }

            // Dynamic path resolution
            Path currentDir = Paths.get(System.getProperty("user.dir"));
            Path uploadDir;

            // If running inside 'snmp-server', move one level up to 'SNMP' then to 'scripts'
            if (currentDir.getFileName().toString().equals("snmp-server")) {
                uploadDir = currentDir.resolveSibling("scripts");
            } else {
                // Otherwise assume we are already at the root 'SNMP' level
                uploadDir = currentDir.resolve("scripts");
            }

            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path destPath = uploadDir.resolve(uploadedFile.filename());
            Files.write(destPath, fileBytes);

            // Return only the filename to be stored in the database
            ctx.json(Map.of("status", "success", "fileName", uploadedFile.filename()));
        });

        // --- NODE MANAGEMENT APIs ---
        app.post("/api/discovery", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (!"ADMIN".equals(jwt.getClaim("role").asString())) {
                throw new UnauthorizedResponse("Admin access required");
            }
            DiscoveryReq req = ctx.bodyAsClass(DiscoveryReq.class);
            
            try {
                List<Node> discoveredNodes = discoveryService.discoverAndRegister(req.startIp, req.endIp);
                ctx.json(discoveredNodes);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        app.post("/api/nodes", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (!"ADMIN".equals(jwt.getClaim("role").asString())) {
                throw new UnauthorizedResponse("Admin access required");
            }

            NodeReq req = ctx.bodyAsClass(NodeReq.class);

            List<TrapAction> actions = null;
            if (req.trapActions != null) {
                actions = new ArrayList<>();
                for (TrapActionReq tr : req.trapActions) {
                    TrapAction a = new TrapAction();
                    a.setTrapOid(tr.trapOid);
                    a.setTrapName(tr.trapName);
                    try {
                        a.setSeverity(TrapSeverity.valueOf(tr.severity));
                    } catch (Exception e) {
                        a.setSeverity(TrapSeverity.INFO);
                    }
                    a.setActionType(tr.actionType);
                    a.setTargetPayload(tr.targetPayload);
                    a.setAutoResolve(tr.autoResolve);
                    actions.add(a);
                }
            }
            nodeService.registerNode(req.name, req.ipAddress, req.nodeType, actions);
            ctx.json(Map.of("status", "success"));
        });

        app.put("/api/nodes/{id}", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (!"ADMIN".equals(jwt.getClaim("role").asString())) {
                throw new UnauthorizedResponse("Admin access required");
            }

            Long targetId = Long.parseLong(ctx.pathParam("id"));
            NodeReq req = ctx.bodyAsClass(NodeReq.class);

            List<TrapAction> actions = null;
            if (req.trapActions != null) {
                actions = new ArrayList<>();
                for (TrapActionReq tr : req.trapActions) {
                    TrapAction a = new TrapAction();
                    a.setTrapOid(tr.trapOid);
                    a.setTrapName(tr.trapName);
                    try {
                        a.setSeverity(TrapSeverity.valueOf(tr.severity));
                    } catch (Exception e) {
                        a.setSeverity(TrapSeverity.INFO);
                    }
                    a.setActionType(tr.actionType);
                    a.setTargetPayload(tr.targetPayload);
                    a.setAutoResolve(tr.autoResolve);
                    actions.add(a);
                }
            }
            nodeService.updateNode(targetId, req.name, req.ipAddress, req.nodeType, actions);
            ctx.json(Map.of("status", "success"));
        });
        
        // --- FORCE EXECUTE API ---
        app.post("/api/traps/{id}/force-execute", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            Long trapId = Long.parseLong(ctx.pathParam("id"));
            Long userId = jwt.getClaim("userId").asLong();

            try (java.sql.Connection conn = db.getConnection()) {
                String sql = "SELECT trap_action_id, message, node_id FROM trap_history WHERE id = ?";
                Long actionId = null;
                String oldMessage = "";
                Long nodeId = null;
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, trapId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            actionId = rs.getLong("trap_action_id");
                            oldMessage = rs.getString("message");
                            nodeId = rs.getLong("node_id");
                            if (rs.wasNull()) actionId = null;
                        } else {
                            ctx.status(404).result("Trap not found");
                            return;
                        }
                    }
                }

                if (actionId == null) {
                    ctx.status(400).result("No automated action associated with this trap");
                    return;
                }

                Optional<TrapAction> actionOpt = trapActionDAO.findById(actionId);
                if (actionOpt.isEmpty() || !"SCRIPT".equalsIgnoreCase(actionOpt.get().getActionType())) {
                    ctx.status(400).result("Action is not a script");
                    return;
                }

                String scriptName = actionOpt.get().getTargetPayload();
                
                Optional<Node> nodeOpt = nodeDAO.findById(nodeId);
                if (nodeOpt.isEmpty()) {
                    ctx.status(400).result("Node not found");
                    return;
                }
                String containerName = nodeOpt.get().getName().toLowerCase().replace("_", "-");
                
                Path currentDir = Paths.get(System.getProperty("user.dir"));
                Path scriptDir = currentDir.getFileName().toString().equals("snmp-server") ? currentDir.resolveSibling("scripts") : currentDir.resolve("scripts");
                Path fullPath = scriptDir.resolve(scriptName).normalize();

                com.snmp.manager.util.ScriptExecutor.ExecutionResult result = com.snmp.manager.util.ScriptExecutor.executeRemote(containerName, fullPath.toString());

                if (result.success()) {
                    trapHistoryDAO.resolveTrap(trapId, userId); 
                    
                    String newMsg = oldMessage.replace("[AI BLOCKED:", "[AI OVERRIDDEN (Forced):");
                    trapHistoryDAO.updateMessage(trapId, newMsg);

                    ctx.json(Map.of("status", "success", "message", "Script force-executed successfully"));
                } else {
                    ctx.status(500).json(Map.of("error", result.message()));
                }
            }
        });
        

        app.start(8080);

        // --- Heartbeat Monitoring Subsystem ---
        // Must be initialized BEFORE the blocking TrapReceiver.start() call.
        // Receiver -> Parser -> Heartbeat -> HeartbeatService -> cache -> NodeHealthMonitor -> DB
        System.out.println("========================================");
        System.out.println("  HEARTBEAT SUBSYSTEM INITIALIZING...   ");
        System.out.println("========================================");

        com.snmp.manager.heartbeat.service.HeartbeatService heartbeatService =
                new com.snmp.manager.heartbeat.service.HeartbeatService(nodeDAO);
        try {
            heartbeatService.initializeFromDatabase();
            System.out.println("[HEARTBEAT] Cache seeded from database OK.");
        } catch (Exception e) {
            System.err.println("[HEARTBEAT] Failed to seed heartbeat cache: " + e.getMessage());
            e.printStackTrace();
        }

        com.snmp.manager.heartbeat.monitor.NodeHealthMonitor healthMonitor =
                new com.snmp.manager.heartbeat.monitor.NodeHealthMonitor(heartbeatService, nodeDAO);
        healthMonitor.start();
        System.out.println("[HEARTBEAT] Health monitor started OK.");

        com.snmp.manager.heartbeat.receiver.HeartbeatReceiver heartbeatReceiver =
                new com.snmp.manager.heartbeat.receiver.HeartbeatReceiver(
                        new com.snmp.manager.heartbeat.parser.HeartbeatParser(),
                        heartbeatService::process);
        try {
            heartbeatReceiver.start();
            System.out.println("[HEARTBEAT] UDP receiver started OK on port 1162.");
        } catch (IOException e) {
            System.err.println("[HEARTBEAT] FAILED to start UDP receiver: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("========================================");
        System.out.println("  HEARTBEAT SUBSYSTEM READY             ");
        System.out.println("========================================");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            heartbeatReceiver.close();
            healthMonitor.close();
        }));

        // --- SNMP Receiver Setup ---
        // TrapReceiver.start() blocks the main thread (by design), so it MUST be last.
        TrapReceiver receiver = new TrapReceiver();
        receiver.addTrapListener(new PersistenceTrapListener(trapService));
        try {
            receiver.start();
        } catch (IOException e) {
            System.err.println("Failed to start SNMP Receiver: " + e.getMessage());
        }
    }

    private static class PersistenceTrapListener implements TrapListener {
        private final TrapService trapService;

        public PersistenceTrapListener(TrapService trapService) {
            this.trapService = trapService;
        }

        @Override
        public void onTrapReceived(TrapEvent event) {
            try {
                trapService.process(event);
            } catch (Exception e) {
                System.err.println("Error processing Trap: " + e.getMessage());
            }
        }
    }
}
