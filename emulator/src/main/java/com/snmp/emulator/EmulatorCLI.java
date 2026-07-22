package com.snmp.emulator;

public class EmulatorCLI {

    public static void run(String[] args) {

        if (args.length != 7 && args.length != 8) {
            System.out.println("Invalid arguments");
            System.out.println("Usage: <--cli> <NodeName> <NodeType> <NodeIP> <AlarmType> [Details] <TargetIP> <TargetPort>");
            System.out.println();
            System.out.println("Valid AlarmTypes: DISK_FULL, POWER_FAILURE, LINK_DOWN, CONGESTION,");
            System.out.println("                 HIGH_TEMPERATURE, MEMORY_EXHAUSTION, CONFIG_ERROR");
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  Without details: --cli Cairo_BTS_01 BTS 10.0.0.5 DISK_FULL 127.0.0.1 162");
            System.out.println("  With details:    --cli Cairo_BTS_01 BTS 10.0.0.5 DISK_FULL \"/dev/sda1 at 98%\" 127.0.0.1 162");
            return;
        }

        String nodeName = args[1];
        String nodeType = args[2];
        String nodeIp = args[3];
        String alarmTypeName = args[4];

        AlarmType alarmType;
        try {
            alarmType = AlarmType.valueOf(alarmTypeName);
        } catch (IllegalArgumentException e) {
            System.out.println("Error: Unknown AlarmType '" + alarmTypeName + "'");
            System.out.println("Valid values: DISK_FULL, POWER_FAILURE, LINK_DOWN, CONGESTION,");
            System.out.println("             HIGH_TEMPERATURE, MEMORY_EXHAUSTION, CONFIG_ERROR");
            return;
        }

        String details;
        String targetIp;
        int targetPort;

        if (args.length == 8) {
            // With details: --cli NodeName NodeType NodeIP AlarmType Details TargetIP TargetPort
            details = args[5];
            targetIp = args[6];
            try {
                targetPort = Integer.parseInt(args[7]);
            } catch (NumberFormatException e) {
                System.out.println("Error: TargetPort must be a valid number (e.g., 162)");
                return;
            }
        } else {
            // Without details: --cli NodeName NodeType NodeIP AlarmType TargetIP TargetPort
            details = "";
            targetIp = args[5];
            try {
                targetPort = Integer.parseInt(args[6]);
            } catch (NumberFormatException e) {
                System.out.println("Error: TargetPort must be a valid number (e.g., 162)");
                return;
            }
        }

        if (targetPort < 1 || targetPort > 65535) {
            System.out.println("Error: TargetPort must be between 1 and 65535");
            return;
        }

        String ipRegex = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$";
        if (!targetIp.matches(ipRegex) || !nodeIp.matches(ipRegex)) {
            System.out.println("Error: NodeIP and TargetIP must be valid IPv4 addresses (e.g., 10.0.0.5).");
            return;
        }

        SnmpService snmp = new SnmpService();
        snmp.sendTrap(nodeName, nodeType, nodeIp, alarmType, details, targetIp, targetPort);
    }
}
