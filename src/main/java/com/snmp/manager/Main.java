package com.snmp.manager;

import com.snmp.manager.snmp.listener.TrapListener;
import com.snmp.manager.snmp.model.TrapEvent;
import com.snmp.manager.snmp.receiver.TrapReceiver;

import java.io.IOException;
import java.util.Map;

/**
 * Application entry point for the SNMP Manager.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("SNMP Manager Started");

        TrapReceiver receiver = new TrapReceiver();
        receiver.addTrapListener(new ConsoleTrapListener());

        try {
            receiver.start();
        } catch (IOException e) {
            System.err.println("Failed to start SNMP receiver: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Prints received traps to the console in a human-readable format.
     */
    private static class ConsoleTrapListener implements TrapListener {
        @Override
        public void onTrapReceived(TrapEvent event) {
            StringBuilder sb = new StringBuilder();
            sb.append("====================================\n");
            sb.append("Trap Received\n\n");
            sb.append("Source IP : ").append(event.getSourceIp()).append('\n');
            sb.append("Version   : ").append(event.getVersion()).append('\n');
            sb.append("Community : ").append(event.getCommunity()).append('\n');
            sb.append("OID        : ").append(event.getTrapOid()).append('\n');
            sb.append("\nVariables\n");
            for (Map.Entry<String, String> entry : event.getVariableBindings().entrySet()) {
                sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
            }
            sb.append("====================================");
            System.out.println(sb);
        }
    }
}
