package com.snmp.emulator;


public class Main {

    public static void main(String[] args) {
        
         if (args.length == 0) {
             System.out.println("Error: This emulator requires arguments");
         }
        
        if (args.length != 5) {
            System.out.println("Invalid arguments");
            System.out.println("Expected arguments : "
                    + "<OperationMode(--cli or --gui)> <NodeType> <TargetIP> <TargetPort>");
            return;
        }
        
        if ("--cli".equals(args[0]) || "cli".equals(args[0])) {
            EmulatorCLI.run(args);
        }
    }
}
