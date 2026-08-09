package com.snmp.manager.snmp.poller;

/**
 * Encapsulates the results of an SNMP GET request executed against a network node.
 * 
 * @param reachable Indicates if the node responded to the SNMP query.
 * @param sysName The standard SNMP sysName (1.3.6.1.2.1.1.5.0) of the target device.
 * @param sysDescr The standard SNMP sysDescr (1.3.6.1.2.1.1.1.0) containing system metadata.
 * @param nodeInfo Custom enterprise metadata returned via snmpd extension.
 * @param ipAddress The IPv4 address of the target node.
 */
public record SnmpGetResult(
    boolean reachable,
    String sysName,
    String sysDescr,
    String nodeInfo,
    String ipAddress,
    long uptime,
    int cpuLoad,
    long memAvail,
    int diskUsage,
    int temperature,
    int congestion
) {}
