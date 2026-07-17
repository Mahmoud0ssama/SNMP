package com.snmp.manager;

import com.snmp.manager.snmp.receiver.TrapReceiver;

/**
 * Application entry point for the SNMP Manager.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("SNMP Manager Started");
        new TrapReceiver().start();
    }
}
