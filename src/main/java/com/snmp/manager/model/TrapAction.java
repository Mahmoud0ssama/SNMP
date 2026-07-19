package com.snmp.manager.model;

import java.time.Instant;

// Represents a trap action definition from table trap_actions.
public class TrapAction {

    private Long id;
    private String trapOid;
    private String trapName;
    private TrapSeverity severity;
    private String actionName;
    private String description;
    private boolean autoResolve;
    private Instant createdAt;
    private Instant updatedAt;

    public TrapAction() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTrapOid() {
        return trapOid;
    }

    public void setTrapOid(String trapOid) {
        this.trapOid = trapOid;
    }

    public String getTrapName() {
        return trapName;
    }

    public void setTrapName(String trapName) {
        this.trapName = trapName;
    }

    public TrapSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(TrapSeverity severity) {
        this.severity = severity;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAutoResolve() {
        return autoResolve;
    }

    public void setAutoResolve(boolean autoResolve) {
        this.autoResolve = autoResolve;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
