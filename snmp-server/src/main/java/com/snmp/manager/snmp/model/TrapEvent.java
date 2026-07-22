package com.snmp.manager.snmp.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Domain model representing a received SNMP trap.
public class TrapEvent {

    private final String sourceIp;
    private final String trapOid;
    private final Instant timestamp;
    private final String community;
    private final String version;
    private final Map<String, String> variableBindings;
    private final String nodeName;
    private final String nodeType;
    private final String nodeIp;
    private final String details;

    public TrapEvent(String sourceIp,
                     String trapOid,
                     Instant timestamp,
                     String community,
                     String version,
                     Map<String, String> variableBindings,
                     String nodeName,
                     String nodeType,
                     String nodeIp,
                     String details) {
        this.sourceIp = sourceIp;
        this.trapOid = trapOid;
        this.timestamp = timestamp;
        this.community = community;
        this.version = version;
        this.variableBindings = variableBindings == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(variableBindings);
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.nodeIp = nodeIp;
        this.details = details;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getTrapOid() {
        return trapOid;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCommunity() {
        return community;
    }

    public String getVersion() {
        return version;
    }

    // Returns an unmodifiable view of the variable bindings (OID to value).
    public Map<String, String> getVariableBindings() {
        return Collections.unmodifiableMap(variableBindings);
    }

    /**
     * Returns the node name extracted from variable binding OID .1.1.
     * May be empty if not present in the trap.
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Returns the node type extracted from variable binding OID .1.2.
     * May be empty if not present in the trap.
     */
    public String getNodeType() {
        return nodeType;
    }

    /**
     * Returns the simulated node IP extracted from variable binding OID .1.4.
     * May be empty if not present in the trap.
     */
    public String getNodeIp() {
        return nodeIp;
    }

    /**
     * Returns the optional details extracted from variable binding OID .1.3.
     * May be empty if not present in the trap.
     */
    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "TrapEvent{"
                + "sourceIp='" + sourceIp + '\''
                + ", trapOid='" + trapOid + '\''
                + ", timestamp=" + timestamp
                + ", community='" + community + '\''
                + ", version='" + version + '\''
                + ", nodeName='" + nodeName + '\''
                + ", nodeType='" + nodeType + '\''
                + ", nodeIp='" + nodeIp + '\''
                + ", details='" + details + '\''
                + ", variableBindings=" + variableBindings
                + '}';
    }
}
