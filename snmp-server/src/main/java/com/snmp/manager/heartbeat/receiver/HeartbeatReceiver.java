package com.snmp.manager.heartbeat.receiver;

import com.snmp.manager.heartbeat.HeartbeatHandler;
import com.snmp.manager.heartbeat.model.Heartbeat;
import com.snmp.manager.heartbeat.parser.HeartbeatParser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opens a dedicated UDP socket and continuously listens for heartbeat packets.
 *
 * <p>The receiver performs no SQL and no business logic: each received
 * datagram is handed to the {@link HeartbeatParser} and the resulting
 * {@link Heartbeat} is forwarded to the configured {@link HeartbeatHandler}.
 * Networking and processing remain strictly separated.</p>
 */
public class HeartbeatReceiver implements AutoCloseable {

    /** Default UDP port the heartbeat server listens on. */
    public static final int DEFAULT_PORT = 1162;

    private static final int MAX_PACKET_SIZE = 512;

    private final int port;
    private final HeartbeatParser parser;
    private final HeartbeatHandler handler;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "heartbeat-receiver");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean running;
    private DatagramSocket socket;

    /**
     * @param port    UDP port to bind
     * @param parser  parser used to decode datagrams
     * @param handler handler that receives parsed heartbeats
     */
    public HeartbeatReceiver(int port, HeartbeatParser parser, HeartbeatHandler handler) {
        this.port = port;
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
    }

    public HeartbeatReceiver(HeartbeatParser parser, HeartbeatHandler handler) {
        this(DEFAULT_PORT, parser, handler);
    }

    /**
     * Starts listening for heartbeats on a background thread. This method
     * returns immediately; call {@link #close()} to stop receiving.
     *
     * @throws IOException if the socket cannot be opened or bound
     */
    public void start() throws IOException {
        if (running) {
            return;
        }
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            throw new IOException("Unable to bind heartbeat receiver to port " + port, e);
        }
        running = true;
        System.out.println("Heartbeat receiver listening on UDP port " + port + "...");
        executor.submit(this::receiveLoop);
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        while (running && !socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Heartbeat receiver error: " + e.getMessage());
                }
                continue;
            }
            if (!running) {
                break;
            }
            forward(packet);
        }
    }

    private void forward(DatagramPacket packet) {
        try {
            byte[] payload = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), payload, 0, packet.getLength());
            Heartbeat heartbeat = parser.parse(payload, packet.getAddress());
            handler.onHeartbeat(heartbeat);
        } catch (IllegalArgumentException e) {
            System.err.println("Rejected malformed heartbeat: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to process heartbeat: " + e.getMessage());
        }
    }

    /** Stops the receiver and releases the UDP socket. */
    @Override
    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        executor.shutdownNow();
        System.out.println("Heartbeat receiver stopped.");
    }
}
