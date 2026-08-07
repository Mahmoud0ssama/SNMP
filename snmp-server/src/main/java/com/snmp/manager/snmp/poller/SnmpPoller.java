package com.snmp.manager.snmp.poller;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;

/**
 * Service responsible for actively querying network nodes using SNMP protocols.
 * Utilizes SNMP4J to execute GET and GETNEXT requests against specified IP addresses.
 */
public class SnmpPoller {

    private static final String SYS_DESCR_OID = "1.3.6.1.2.1.1.1.0";
    private static final String SYS_NAME_OID = "1.3.6.1.2.1.1.5.0";
    private static final String NODE_INFO_EXACT_OID = "1.3.6.1.4.1.99999.1.1.3.1.2.8.110.111.100.101.73.110.102.111";

    private final Snmp snmp;

    /**
     * Initializes the SNMP Poller by creating and binding a UDP transport mapping.
     * 
     * @throws RuntimeException if the UDP transport fails to initialize.
     */
    public SnmpPoller() {
        try {
            TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
            this.snmp = new Snmp(transport);
            this.snmp.listen();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize SNMP Poller transport layer.", e);
        }
    }

    /**
     * Executes an SNMP query against a specific IP address to gather system and custom telemetry.
     * 
     * @param ipAddress The target IPv4 address.
     * @param port The target SNMP port (typically 161).
     * @param communityString The SNMPv2c community string used for authentication.
     * @return SnmpGetResult containing the aggregated response, or a failure result if unreachable.
     */
    public SnmpGetResult poll(String ipAddress, int port, String communityString) {
        try {
            Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/" + port);
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(communityString));
            target.setAddress(targetAddress);
            target.setRetries(1);
            target.setTimeout(3000);
            target.setVersion(SnmpConstants.version2c);

            PDU getPdu = new PDU();
            getPdu.add(new VariableBinding(new OID(SYS_NAME_OID)));
            getPdu.add(new VariableBinding(new OID(SYS_DESCR_OID)));
            getPdu.add(new VariableBinding(new OID(NODE_INFO_EXACT_OID)));
            getPdu.setType(PDU.GET);

            ResponseEvent<Address> getResponse = snmp.send(getPdu, target);
            PDU responsePdu = getResponse.getResponse();

            if (responsePdu == null || responsePdu.getErrorStatus() != PDU.noError) {
                return new SnmpGetResult(false, null, null, null, ipAddress);
            }

            String sysName = null;
            String sysDescr = null;
            String nodeInfo = null;

            if (responsePdu.size() >= 3) {
                sysName = responsePdu.get(0).getVariable().toString();
                sysDescr = responsePdu.get(1).getVariable().toString();
                if (!responsePdu.get(2).getVariable().isException()) {
                    nodeInfo = responsePdu.get(2).getVariable().toString();
                }
            }

            return new SnmpGetResult(true, sysName, sysDescr, nodeInfo, ipAddress);

        } catch (Exception e) {
            return new SnmpGetResult(false, null, null, null, ipAddress);
        }
    }
}
