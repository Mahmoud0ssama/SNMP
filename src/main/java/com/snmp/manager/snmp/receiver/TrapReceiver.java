package com.snmp.manager.snmp.receiver;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.smi.UdpAddress;

import java.io.IOException;

/**
 * Opens a UDP socket and listens for incoming SNMP traps.
 *
 * <p>This commit binds the UDP socket via SNMP4J on port 1162, registers a
 * responder that acknowledges received traps, and starts listening.
 * Trap handling is decoupled via listeners in a later commit.</p>
 */
public class TrapReceiver {

    private static final int DEFAULT_PORT = 1162;

    private final int port;

    private Snmp snmp;
    private final Object lock = new Object();
    private volatile boolean running;

    public TrapReceiver() {
        this(DEFAULT_PORT);
    }

    public TrapReceiver(int port) {
        this.port = port;
    }

    /**
     * Opens the UDP transport, starts listening for traps and blocks the
     * calling thread until {@link #stop()} is invoked.
     *
     * @throws IOException if the socket cannot be opened or bound
     */
    public void start() throws IOException {
        System.out.println("Starting SNMP Receiver...");
        System.out.println("Listening on port " + port + "...");

        TransportMapping<?> transport = new DefaultUdpTransportMapping(
                new UdpAddress("0.0.0.0/" + port));
        snmp = new Snmp(transport);
        snmp.addCommandResponder(new CommandResponder() {
            @Override
            public <A extends org.snmp4j.smi.Address> void processPdu(CommandResponderEvent<A> event) {
                System.out.println("Trap received!");
            }
        });
        snmp.listen();
        running = true;

        synchronized (lock) {
            while (running) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Stops the receiver and releases the UDP socket.
     *
     * @throws IOException if the transport cannot be closed
     */
    public void stop() throws IOException {
        running = false;
        synchronized (lock) {
            lock.notifyAll();
        }
        if (snmp != null) {
            snmp.close();
        }
    }
}
