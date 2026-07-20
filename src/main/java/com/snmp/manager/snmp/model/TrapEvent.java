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

    public TrapEvent(String sourceIp,
                     String trapOid,
                     Instant timestamp,
                     String community,
                     String version,
                     Map<String, String> variableBindings) {
        this.sourceIp = sourceIp;
        this.trapOid = trapOid;
        this.timestamp = timestamp;
        this.community = community;
        this.version = version;
        this.variableBindings = variableBindings == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(variableBindings);
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

    @Override
    public String toString() {
        return "TrapEvent{"
                + "sourceIp='" + sourceIp + '\''
                + ", trapOid='" + trapOid + '\''
                + ", timestamp=" + timestamp
                + ", community='" + community + '\''
                + ", version='" + version + '\''
                + ", variableBindings=" + variableBindings
                + '}';
    }
}
