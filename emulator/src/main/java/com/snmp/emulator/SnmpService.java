package com.snmp.emulator;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SnmpService {

    // Define a base custom Enterprise OID for your project
    private static final String ENTERPRISE_OID = "1.3.6.1.4.1.99999";

    public void sendTrap(String nodeType, String message, String targetIp, int targetPort) {
        try {
            // 1. Setup the UDP Transport
            TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
            transport.listen();

            // 2. Define the Target (Where the trap is going)
            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString("public")); // Default community string
            target.setVersion(SnmpConstants.version2c);
            target.setAddress(new UdpAddress(targetIp + "/" + targetPort));
            target.setRetries(2);
            target.setTimeout(1500);

            // 3. Create the PDU (The actual payload)
            PDU pdu = new PDU();
            pdu.setType(PDU.V2TRAP);

            // Standard SNMPv2c variables: sysUpTime and trapOID
            pdu.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(5000)));
            pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, new OID(ENTERPRISE_OID)));

            // 4. Attach your custom data (Node Type and Message)
            pdu.add(new VariableBinding(new OID(ENTERPRISE_OID + ".1"), new OctetString(nodeType)));
            pdu.add(new VariableBinding(new OID(ENTERPRISE_OID + ".2"), new OctetString(message)));

            // 5. Send the Trap
            Snmp snmp = new Snmp(transport);
            System.out.println("Transmitting SNMP Trap to " + targetIp + ":" + targetPort + "...");
            snmp.send(pdu, target);
            
            // Clean up
            snmp.close();
            System.out.println("Trap successfully sent!");

        } catch (Exception e) {
            System.err.println("Failed to send SNMP Trap: " + e.getMessage());
            e.printStackTrace();
        }
    }
}Explain this code