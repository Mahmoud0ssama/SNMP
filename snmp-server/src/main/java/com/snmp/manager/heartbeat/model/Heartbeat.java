package com.snmp.manager.heartbeat.model;

import java.net.InetAddress;
import java.time.Instant;

/**
 * Plain data object representing a single heartbeat message received from a
 * monitored node.
 *
 * <p>A heartbeat is a lightweight UDP datagram that proves a node is alive.
 * It is completely independent from SNMP traps.</p>
 */
public class Heartbeat {

    /** Identifier of the sending node (database id or IP address). */
    private String nodeId;

    /** Epoch second at which the sender generated the heartbeat. */
    private Instant timestamp;

    /** Network address of the sender as observed by the receiver. */
    private InetAddress senderAddress;

    public Heartbeat() {
    }

    public Heartbeat(String nodeId, Instant timestamp, InetAddress senderAddress) {
        this.nodeId = nodeId;
        this.timestamp = timestamp;
        this.senderAddress = senderAddress;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public InetAddress getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(InetAddress senderAddress) {
        this.senderAddress = senderAddress;
    }

    @Override
    public String toString() {
        return "Heartbeat{nodeId='" + nodeId + "', timestamp=" + timestamp
                + ", senderAddress=" + senderAddress + '}';
    }
}
