package com.snmp.manager.heartbeat.parser;

import com.snmp.manager.heartbeat.model.Heartbeat;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Converts a raw UDP heartbeat datagram into a {@link Heartbeat} domain object.
 *
 * <p>This is the only place that understands the wire format, so the packet
 * structure is never exposed outside the parser.</p>
 *
 * <p>Expected format (no JSON): {@code NODE_ID|TIMESTAMP}<br>
 * where {@code TIMESTAMP} is a Unix epoch second.</p>
 */
public class HeartbeatParser {

    /** Field separator used on the wire. */
    public static final String SEPARATOR = "|";

    /**
     * Parses the given datagram bytes into a heartbeat.
     *
     * @param data           raw payload bytes received from the socket
     * @param senderAddress  address of the sending host as seen by the receiver
     * @return the parsed heartbeat
     * @throws IllegalArgumentException if the payload is malformed or rejected
     */
    public Heartbeat parse(byte[] data, InetAddress senderAddress) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Empty heartbeat payload");
        }

        String raw = new String(data, StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Empty heartbeat payload");
        }

        String[] parts = raw.split("\\" + SEPARATOR);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed heartbeat, expected NODE_ID|TIMESTAMP but got: " + raw);
        }

        String nodeId = parts[0].trim();
        if (nodeId.isEmpty()) {
            throw new IllegalArgumentException("Heartbeat nodeId is empty");
        }

        long epochSecond;
        try {
            epochSecond = Long.parseLong(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Heartbeat timestamp is not a valid epoch second: " + parts[1]);
        }
        if (epochSecond <= 0) {
            throw new IllegalArgumentException("Heartbeat timestamp must be positive: " + epochSecond);
        }

        return new Heartbeat(nodeId, Instant.ofEpochSecond(epochSecond), senderAddress);
    }
}
