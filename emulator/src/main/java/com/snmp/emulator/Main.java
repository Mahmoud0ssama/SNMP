package com.snmp.emulator;


public class Main {

    public static void main(String[] args) {
        
         if (args.length == 0) {
             printHelp();
             return;
         }
 
        if ("--cli".equals(args[0]) || "cli".equals(args[0])) {
            EmulatorCLI.run(args);
        } else {
            System.out.println("Error: Unknown or unsupported mode '" + args[0] + "'");
            printHelp();
        }
    }
    
    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  GUI Mode: java -jar emulator.jar --gui");
        System.out.println("  CLI Mode: java -jar emulator.jar --cli <NodeType> <Message> <TargetIP> <TargetPort>");
    }
}
