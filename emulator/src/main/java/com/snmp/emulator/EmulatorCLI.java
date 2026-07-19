package com.snmp.emulator;

public class EmulatorCLI {

    public static void run(String[] args) {

           if (args.length != 5) {
            System.out.println("Invalid arguments");
            System.out.println("Expected arguments : "
                    + "<OperationMode(--cli or --gui)> <NodeType> <TargetIP> <TargetPort>");
            return;
        }
        
        String nodeType = args[1];
        String message = args[2];
        String targetIp = args[3];
        int targetPort;  

        try {
            targetPort = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.out.println("Error: TargetPort must be a valid number (e.g., 162)");
            return;
        }

        if (targetPort < 1 || targetPort > 65535) {
            System.out.println("Error: TargetPort must be between 1 and 65535");
            return;
        }

        String ipRegex = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$";
        if (!targetIp.matches(ipRegex)) {
            System.out.println("Error: TargetIP must be a valid IPv4 address (e.g., 127.0.0.1).");
            return;
        }

        SnmpService snmp = new SnmpService();
        snmp.sendTrap(nodeType, message, targetIp, targetPort);
    }
}
