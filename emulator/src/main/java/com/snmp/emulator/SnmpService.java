package com.snmp.emulator;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

/**
 * The {@code SnmpService} class acts as the core network engine for the SNMP Emulator.
 * It is responsible for constructing SNMPv2c Trap messages (PDUs) and transmitting 
 * them over the network via UDP to a designated central management server.
 */

public class SnmpService {

    /**
     * The base custom Enterprise Object Identifier (OID) allocated for this project.
     * All custom variables sent in the traps will be appended as sub-OIDs to this base tree.
     * Standard prefix (1.3.6.1.4.1) denotes Private Enterprises.
     */
    
    private static final String ENTERPRISE_OID = "1.3.6.1.4.1.99999";

    /**
     * Constructs and transmits an SNMPv2c Trap over UDP.
     * 
     * This method initializes a local network transport, builds a Protocol Data Unit (PDU) 
     * containing standard system uptime and custom variable bindings (Node Type and Message), 
     * and sends it to the specified target listener.
     *
     * @param nodeType   The type or name of the simulated telecom node generating the alarm 
     *                   (e.g., "BTS", "MSC"). Bound to sub-OID .1.
     * @param message    The descriptive text of the alarm or error condition 
     *                   (e.g., "H.D is full", "Congestion on traffic"). Bound to sub-OID .2.
     * @param targetIp   The IPv4 address of the central management server (e.g., "127.0.0.1").
     * @param targetPort The UDP port on which the central server is listening (typically 162).
     */
    
    public void sendTrap(String nodeType, String message, String targetIp, int targetPort) {
        try {
            TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
            transport.listen();

            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString("public"));
            target.setVersion(SnmpConstants.version2c);
            target.setAddress(new UdpAddress(targetIp + "/" + targetPort));
            target.setRetries(2);
            target.setTimeout(1500);

            PDU pdu = new PDU();
            pdu.setType(PDU.TRAP);
            
            pdu.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(5000)));
            pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, new OID(ENTERPRISE_OID)));
            pdu.add(new VariableBinding(new OID(ENTERPRISE_OID + ".1"), new OctetString(nodeType)));
            pdu.add(new VariableBinding(new OID(ENTERPRISE_OID + ".2"), new OctetString(message)));

            Snmp snmp = new Snmp(transport);
            System.out.println("Transmitting SNMP Trap to " + targetIp + ":" + targetPort + "...");
            snmp.send(pdu, target);
            
            snmp.close();
            System.out.println("Trap successfully sent!");

        } catch (Exception e) {
            System.err.println("Failed to send SNMP Trap: " + e.getMessage());
            e.printStackTrace();
        }
    }
}