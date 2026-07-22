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
     * Standard prefix (1.3.6.1.4.1) denotes Private Enterprises.
     *
     * <p>OID tree structure:
     * <ul>
     *   <li>{@code .0.x} — Notifications (Trap OIDs identifying the event type)</li>
     *   <li>{@code .1.x} — Objects (Variable Bindings carrying contextual data)</li>
     * </ul>
     */
    
    private static final String ENTERPRISE_OID = "1.3.6.1.4.1.99999";

    /**
     * Constructs and transmits an SNMPv2c Trap over UDP.
     * 
     * This method initializes a local network transport, builds a Protocol Data Unit (PDU) 
     * containing standard system uptime, a unique trap OID per alarm type, and custom 
     * variable bindings (Node Name, Node Type, and optional Details), then sends it 
     * to the specified target listener.
     *
     * @param nodeName   The name of the simulated telecom node generating the alarm 
     *                   (e.g., "Cairo_BTS_01"). Bound to sub-OID .1.1.
     * @param nodeType   The type of the simulated telecom node 
     *                   (e.g., "BTS", "MSC"). Bound to sub-OID .1.2.
     * @param nodeIp     The simulated IP address of the node (e.g., "10.0.0.5"). Bound to sub-OID .1.4.
     * @param alarmType  The predefined alarm type, which determines the unique trap OID.
     * @param details    Optional free-text details about the alarm 
     *                   (e.g., "/dev/sda1 at 98%"). Bound to sub-OID .1.3.
     * @param targetIp   The IPv4 address of the central management server (e.g., "127.0.0.1").
     * @param targetPort The UDP port on which the central server is listening (typically 162).
     */
    
    public void sendTrap(String nodeName, String nodeType, String nodeIp, AlarmType alarmType, String details, String targetIp, int targetPort) {
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
            pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, new OID(alarmType.getOid())));
            pdu.add(new VariableBinding(new OID(ENTERPRISE_OID + ".1.1"), new OctetString(nodeName)));
            pdu.add(new VariableBinding(new OID(ENTERPRISE_OID + ".1.2"), new OctetString(nodeType)));
            pdu.add(new VariableBinding(new OID(ENTERPRISE_OID + ".1.4"), new OctetString(nodeIp)));
            if (details != null && !details.trim().isEmpty()) {
                pdu.add(new VariableBinding(new OID(ENTERPRISE_OID + ".1.3"), new OctetString(details)));
            }

            Snmp snmp = new Snmp(transport);
            System.out.println("Transmitting SNMP Trap [" + alarmType.getDisplayName() + "] to " + targetIp + ":" + targetPort + "...");
            snmp.send(pdu, target);
            
            snmp.close();
            System.out.println("Trap successfully sent!");

        } catch (Exception e) {
            System.err.println("Failed to send SNMP Trap: " + e.getMessage());
            e.printStackTrace();
        }
    }
}