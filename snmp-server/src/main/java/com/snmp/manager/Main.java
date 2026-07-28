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

    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.out.println("SNMP Manager Started");

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
            DatabaseConnection db = DatabaseConnection.fromResource();
            UserDAO userDAO = new UserDAO(db);

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
            ctx.json(new NodeDAO(DatabaseConnection.fromResource()).findAll());
        });

        app.get("/api/traps", ctx -> {
            ctx.json(new TrapHistoryDAO(DatabaseConnection.fromResource()).findAll());
        });

        app.put("/api/traps/{id}/resolve", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            Long trapId = Long.parseLong(ctx.pathParam("id"));
            boolean success = new TrapHistoryDAO(DatabaseConnection.fromResource()).resolveTrap(trapId, jwt.getClaim("userId").asLong());
            if (success) {
                ctx.json(Map.of("status", "success"));
            } else {
                ctx.status(400).result("Error resolving trap");
            }
        });

        app.get("/api/ai/insights", ctx -> {
            AiAnalysisService aiService = new AiAnalysisService(DatabaseConnection.fromResource());
            String result = aiService.generateInsights();
            ctx.json(Map.of("markdown", result));
        });

        // --- USER MANAGEMENT APIs ---
        app.get("/api/users", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (!"ADMIN".equals(jwt.getClaim("role").asString())) {
                throw new UnauthorizedResponse("Admin access required");
            }

            List<User> users = new UserDAO(DatabaseConnection.fromResource()).findAll();
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
                new UserDAO(DatabaseConnection.fromResource()).save(u);
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
            new UserDAO(DatabaseConnection.fromResource()).delete(targetId);
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
            UserDAO dao = new UserDAO(DatabaseConnection.fromResource());

            String newRole = isAdmin ? req.role : jwt.getClaim("role").asString();

            try {
                if (req.password != null && !req.password.trim().isEmpty()) {
                    dao.updateWithPassword(targetId, req.username, BCrypt.hashpw(req.password, BCrypt.gensalt()), newRole);
                } else {
                    dao.update(targetId, req.username, newRole);
                }
                ctx.json(Map.of("status", "success"));
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "Username already exists. Please choose another one."));
            }
        });

        // --- TRAP CONFIGURATION APIs ---
        app.get("/api/nodes/{id}/trapActions", ctx -> {
            Long targetId = Long.parseLong(ctx.pathParam("id"));
            ctx.json(new TrapActionDAO(DatabaseConnection.fromResource()).findByNodeId(targetId));
        });

        app.post("/api/upload-script", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt"); // Requires auth
            UploadedFile uploadedFile = ctx.uploadedFile("file");
            if (uploadedFile == null) {
                ctx.status(400).result("No file uploaded");
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
            Files.copy(uploadedFile.content(), destPath, StandardCopyOption.REPLACE_EXISTING);

            // Return only the filename to be stored in the database
            ctx.json(Map.of("status", "success", "fileName", uploadedFile.filename()));
        });

        // --- NODE MANAGEMENT APIs ---
        app.post("/api/nodes", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (!"ADMIN".equals(jwt.getClaim("role").asString())) {
                throw new UnauthorizedResponse("Admin access required");
            }

            NodeReq req = ctx.bodyAsClass(NodeReq.class);
            DatabaseConnection db = DatabaseConnection.fromResource();
            NodeService nodeService = new NodeService(new NodeDAO(db), new TrapActionDAO(db));

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
            DatabaseConnection db = DatabaseConnection.fromResource();
            NodeService nodeService = new NodeService(new NodeDAO(db), new TrapActionDAO(db));

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

        app.start(8080);

        // --- SNMP Receiver Setup ---
        TrapReceiver receiver = new TrapReceiver();
        receiver.addTrapListener(new PersistenceTrapListener());
        try {
            receiver.start();
        } catch (IOException e) {
            System.err.println("Failed to start SNMP Receiver: " + e.getMessage());
        }
    }

    private static class PersistenceTrapListener implements TrapListener {

        @Override
        public void onTrapReceived(TrapEvent event) {
            try {
                DatabaseConnection db = DatabaseConnection.fromResource();
                new TrapService(new NodeDAO(db), new TrapActionDAO(db), new TrapHistoryDAO(db), new NodeService(new NodeDAO(db)), db).process(event);
            } catch (Exception e) {
                System.err.println("Error processing Trap: " + e.getMessage());
            }
        }
    }
}
