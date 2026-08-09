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
    private static final String SYS_UPTIME_OID = "1.3.6.1.2.1.1.3.0";
    private static final String CPU_LOAD_OID = "1.3.6.1.4.1.2021.10.1.5.1";
    private static final String MEM_AVAIL_OID = "1.3.6.1.4.1.2021.4.6.0";
    private static final String DISK_STATUS_OID = "1.3.6.1.4.1.99999.1.5.3.1.1.10.100.105.115.107.83.116.97.116.117.115";
    private static final String TEMP_STATUS_OID = "1.3.6.1.4.1.99999.1.6.3.1.1.10.116.101.109.112.83.116.97.116.117.115";
    private static final String CONGESTION_STATUS_OID = "1.3.6.1.4.1.99999.1.7.3.1.1.16.99.111.110.103.101.115.116.105.111.110.83.116.97.116.117.115";

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
            getPdu.add(new VariableBinding(new OID(SYS_UPTIME_OID)));
            getPdu.add(new VariableBinding(new OID(CPU_LOAD_OID)));
            getPdu.add(new VariableBinding(new OID(MEM_AVAIL_OID)));
            getPdu.add(new VariableBinding(new OID(DISK_STATUS_OID)));
            getPdu.add(new VariableBinding(new OID(TEMP_STATUS_OID)));
            getPdu.add(new VariableBinding(new OID(CONGESTION_STATUS_OID)));
            getPdu.setType(PDU.GET);

            ResponseEvent<Address> getResponse = snmp.send(getPdu, target);
            PDU responsePdu = getResponse.getResponse();

            if (responsePdu == null || responsePdu.getErrorStatus() != PDU.noError) {
                return new SnmpGetResult(false, null, null, null, ipAddress, 0, 0, 0, 0, 0, 0);
            }

            String sysName = null;
            String sysDescr = null;
            String nodeInfo = null;
            long uptime = 0;
            int cpuLoad = 0;
            long memAvail = 0;
            int diskUsage = 0;
            int temperature = 0;
            int congestion = 0;

            if (responsePdu.size() >= 9) {
                sysName = responsePdu.get(0).getVariable().toString();
                sysDescr = responsePdu.get(1).getVariable().toString();
                
                if (!responsePdu.get(2).getVariable().isException()) {
                    nodeInfo = responsePdu.get(2).getVariable().toString();
                }
                
                if (!responsePdu.get(3).getVariable().isException()) {
                    uptime = responsePdu.get(3).getVariable().toLong();
                }
                
                if (!responsePdu.get(4).getVariable().isException()) {
                    cpuLoad = responsePdu.get(4).getVariable().toInt();
                }
                
                if (!responsePdu.get(5).getVariable().isException()) {
                    memAvail = responsePdu.get(5).getVariable().toLong();
                }
                
                if (!responsePdu.get(6).getVariable().isException()) {
                    try { diskUsage = Integer.parseInt(responsePdu.get(6).getVariable().toString().trim()); } catch(Exception ignored) {}
                }
                
                if (!responsePdu.get(7).getVariable().isException()) {
                    try { temperature = Integer.parseInt(responsePdu.get(7).getVariable().toString().trim()); } catch(Exception ignored) {}
                }
                
                if (!responsePdu.get(8).getVariable().isException()) {
                    try { congestion = Integer.parseInt(responsePdu.get(8).getVariable().toString().trim()); } catch(Exception ignored) {}
                }
            }

            return new SnmpGetResult(true, sysName, sysDescr, nodeInfo, ipAddress, uptime, cpuLoad, memAvail, diskUsage, temperature, congestion);

        } catch (Exception e) {
            return new SnmpGetResult(false, null, null, null, ipAddress, 0, 0, 0, 0, 0, 0);
        }
    }
}
