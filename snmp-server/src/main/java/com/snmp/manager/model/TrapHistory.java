package com.snmp.manager.model;

import java.time.Instant;

public class TrapHistory {

    private Long id;
    private Long nodeId;
    private Long trapActionId;
    private String trapOid;
    private String sourceIp;
    private String message;
    private TrapStatus status;
    private Instant receivedAt;
    private Instant resolvedAt;
    
    private String resolvedByUsername;

    public TrapHistory() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }

    public Long getTrapActionId() { return trapActionId; }
    public void setTrapActionId(Long trapActionId) { this.trapActionId = trapActionId; }

    public String getTrapOid() { return trapOid; }
    public void setTrapOid(String trapOid) { this.trapOid = trapOid; }

    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public TrapStatus getStatus() { return status; }
    public void setStatus(TrapStatus status) { this.status = status; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolvedByUsername() { return resolvedByUsername; }
    public void setResolvedByUsername(String resolvedByUsername) { this.resolvedByUsername = resolvedByUsername; }
}