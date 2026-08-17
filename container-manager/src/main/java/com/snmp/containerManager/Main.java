package com.snmp.containerManager;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import com.snmp.containerManager.service.DockerService;
import com.snmp.containerManager.service.ComposeService;
import com.snmp.containerManager.service.ChaosService;

public class Main {
    public static void main(String[] args) {
        System.out.println("Container Manager Started on Port 9091");

        DockerService dockerService = new DockerService();
        ComposeService composeService = new ComposeService();
        ChaosService chaosService = new ChaosService();

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("src/main/resources/public", Location.EXTERNAL);
        }).start(9091);

        // API Routes
        
        // --- Containers ---
        app.get("/api/containers", ctx -> {
            ctx.json(dockerService.listContainers());
        });
        
        app.get("/api/containers/{name}", ctx -> {
            String name = ctx.pathParam("name");
            ctx.json(dockerService.getContainerInfo(name));
        });

        app.post("/api/containers/{name}/start", ctx -> {
            String name = ctx.pathParam("name");
            ctx.json(dockerService.startContainer(name));
        });

        app.post("/api/containers/{name}/stop", ctx -> {
            String name = ctx.pathParam("name");
            ctx.json(dockerService.stopContainer(name));
        });

        app.post("/api/containers/{name}/restart", ctx -> {
            String name = ctx.pathParam("name");
            ctx.json(dockerService.restartContainer(name));
        });

        app.delete("/api/containers/{name}", ctx -> {
            String name = ctx.pathParam("name");
            ctx.json(dockerService.removeContainer(name));
        });

        app.get("/api/containers/{name}/logs", ctx -> {
            String name = ctx.pathParam("name");
            String lines = ctx.queryParamAsClass("lines", String.class).getOrDefault("100");
            ctx.json(dockerService.getContainerLogs(name, lines));
        });

        app.post("/api/containers/{name}/exec", ctx -> {
            String name = ctx.pathParam("name");
            // Assuming the client sends {"command": "..."}
            String cmd = ctx.bodyAsClass(ExecRequest.class).command;
            ctx.json(dockerService.execCommand(name, cmd));
        });

        app.post("/api/containers/{name}/mode", ctx -> {
            String name = ctx.pathParam("name");
            String mode = ctx.bodyAsClass(ModeRequest.class).mode;
            ctx.json(dockerService.setSimulatorMode(name, mode));
        });

        app.get("/api/containers/{name}/mode", ctx -> {
            String name = ctx.pathParam("name");
            ctx.json(dockerService.getSimulatorMode(name));
        });

        // --- Compose / Nodes ---
        app.get("/api/compose/services", ctx -> {
            ctx.json(composeService.listDefinedServices());
        });

        app.post("/api/compose/add-node", ctx -> {
            AddNodeRequest req = ctx.bodyAsClass(AddNodeRequest.class);
            ctx.json(composeService.addNode(req.serviceName, req.ip, req.nodeType, req.nodeName, req.region, req.vendor));
        });

        app.delete("/api/compose/{name}", ctx -> {
            String name = ctx.pathParam("name");
            ctx.json(composeService.removeNode(name));
        });

        app.get("/api/compose/next-ip", ctx -> {
            ctx.json(composeService.getNextAvailableIp());
        });

        // --- Chaos ---
        app.post("/api/chaos/{type}/{container}", ctx -> {
            String type = ctx.pathParam("type");
            String container = ctx.pathParam("container");
            ctx.json(chaosService.triggerChaos(type, container));
        });
        
        // Handle SPA routing if needed (though we have multiple pages now)
        // Since we serve static files directly, Javalin will find index.html, detail.html, add-node.html automatically.
    }
    
    // DTOs
    public static class ExecRequest {
        public String command;
    }
    
    public static class AddNodeRequest {
        public String serviceName;
        public String nodeName;
        public String nodeType;
        public String region;
        public String ip;
        public String vendor;
    }
    
    public static class ModeRequest {
        public String mode;
    }
}
