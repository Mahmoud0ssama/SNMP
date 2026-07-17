package com.snmp.manager.snmp.receiver;

/**
 * Opens a UDP socket and listens for incoming SNMP traps.
 *
 * <p>This skeleton prints startup messages only. Actual UDP listening
 * using SNMP4J is introduced in a later commit.</p>
 */
public class TrapReceiver {

    private static final int DEFAULT_PORT = 1162;

    private final int port;

    public TrapReceiver() {
        this(DEFAULT_PORT);
    }

    public TrapReceiver(int port) {
        this.port = port;
    }

    /**
     * Starts the receiver.
     */
    public void start() {
        System.out.println("Starting SNMP Receiver...");
        System.out.println("Listening on port " + port + "...");
    }
}
