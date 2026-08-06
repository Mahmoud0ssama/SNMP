package com.snmp.manager.heartbeat.sender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically emits lightweight UDP heartbeat datagrams to a central server.
 *
 * <p>Responsibilities are intentionally minimal: this class only builds and
 * sends packets. It performs no parsing, holds no database connection and
 * contains no business logic. Networking is kept fully separate from
 * processing.</p>
 *
 * <p>Packet format (no JSON): {@code NODE_ID|TIMESTAMP}<br>
 * Example: {@code 5|1754512300} or {@code 10.0.0.5|1754512300}</p>
 */
public class HeartbeatSender implements AutoCloseable {

    /** Default interval between heartbeats, in seconds. */
    public static final int DEFAULT_INTERVAL_SECONDS = 10;

    /** Default destination UDP port of the heartbeat server. */
    public static final int DEFAULT_SERVER_PORT = 1162;

    /** Default destination host of the heartbeat server. */
    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";

    private final String nodeId;
    private final String serverHost;
    private final int serverPort;
    private final long intervalSeconds;

    private final ScheduledExecutorService scheduler;

    private volatile boolean running;

    /**
     * @param nodeId        identifier broadcast in every heartbeat (database id or IP)
     * @param serverHost    destination host of the central server
     * @param serverPort    destination UDP port of the central server
     * @param intervalSeconds seconds between consecutive heartbeats
     */
    public HeartbeatSender(String nodeId, String serverHost, int serverPort, long intervalSeconds) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.serverHost = Objects.requireNonNull(serverHost, "serverHost must not be null");
        if (serverPort <= 0 || serverPort > 65535) {
            throw new IllegalArgumentException("serverPort must be between 1 and 65535");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }
        this.serverPort = serverPort;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-sender-" + this.nodeId);
            t.setDaemon(true);
            return t;
        });
    }

    public HeartbeatSender(String nodeId) {
        this(nodeId, DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT, DEFAULT_INTERVAL_SECONDS);
    }

    /**
     * Starts the periodic heartbeat transmission. Safe to call once.
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler.scheduleAtFixedRate(this::sendOnce, 0, intervalSeconds, TimeUnit.SECONDS);
        System.out.println("Heartbeat sender started for node '" + nodeId
                + "' -> " + serverHost + ":" + serverPort + " every " + intervalSeconds + "s");
    }

    /**
     * Stops the sender. In a real deployment this simulates node failure
     * because the server will no longer receive heartbeats and will time out.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        scheduler.shutdownNow();
        System.out.println("Heartbeat sender stopped for node '" + nodeId + "'");
    }

    /** Sends a single heartbeat datagram. */
    private void sendOnce() {
        if (!running) {
            return;
        }
        String payload = nodeId + "|" + Instant.now().getEpochSecond();
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(serverHost);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, serverPort);
            socket.send(packet);
        } catch (UnknownHostException e) {
            System.err.println("Heartbeat sender: unknown host " + serverHost);
        } catch (SocketException e) {
            System.err.println("Heartbeat sender: socket error - " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Heartbeat sender: failed to send heartbeat - " + e.getMessage());
        }
    }

    @Override
    public void close() {
        stop();
    }
}
