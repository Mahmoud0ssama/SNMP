package com.snmp.manager;

import com.snmp.manager.snmp.receiver.TrapReceiver;

import java.io.IOException;

/**
 * Application entry point for the SNMP Manager.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("SNMP Manager Started");

        TrapReceiver receiver = new TrapReceiver();
        try {
            receiver.start();
        } catch (IOException e) {
            System.err.println("Failed to start SNMP receiver: " + e.getMessage());
            System.exit(1);
        }
    }
}
