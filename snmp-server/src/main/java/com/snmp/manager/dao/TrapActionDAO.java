package com.snmp.manager.dao;

import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.model.TrapAction;
import com.snmp.manager.model.TrapSeverity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

// Data access object for TrapAction definitions in table trap_actions.
public class TrapActionDAO {

    private final DatabaseConnection databaseConnection;

    public TrapActionDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    // Finds a trap action by its primary key.
    public Optional<TrapAction> findById(Long id) throws SQLException {
        String sql = "SELECT id, node_id, trap_oid, trap_name, severity, description, "
                + "auto_resolve, action_type, target_payload, created_at, updated_at "
                + "FROM trap_actions WHERE id = ?";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public List<TrapAction> findByNodeId(Long nodeId) throws SQLException {
        String sql = "SELECT id, node_id, trap_oid, trap_name, severity, description, "
                + "auto_resolve, action_type, target_payload, created_at, updated_at "
                + "FROM trap_actions WHERE node_id = ?";
        List<TrapAction> actions = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    actions.add(mapRow(rs));
                }
            }
        }
        return actions;
    }

    public List<TrapAction> getDistinctTrapTemplates() throws SQLException {
        String sql = "SELECT DISTINCT ON (trap_oid) id, node_id, trap_oid, trap_name, severity, "
                + "auto_resolve, action_type, target_payload, created_at, updated_at "
                + "FROM trap_actions ORDER BY trap_oid, created_at DESC";
        List<TrapAction> templates = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                templates.add(mapRow(rs));
            }
        }
        return templates;
    }

    public List<String> getDistinctActionTypes() throws SQLException {
        String sql = "SELECT DISTINCT action_type FROM trap_actions WHERE action_type IS NOT NULL";
        List<String> types = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                types.add(rs.getString("action_type"));
            }
        }
        return types;
    }

    public void upsert(TrapAction action) throws SQLException {
        String sql = "INSERT INTO trap_actions (node_id, trap_oid, trap_name, severity, action_type, target_payload, auto_resolve) "
                + "VALUES (?, ?, ?, ?::trap_severity, ?, ?, ?) "
                + "ON CONFLICT (node_id, trap_oid) DO UPDATE SET "
                + "trap_name = EXCLUDED.trap_name, "
                + "severity = EXCLUDED.severity, "
                + "action_type = EXCLUDED.action_type, "
                + "target_payload = EXCLUDED.target_payload, "
                + "auto_resolve = EXCLUDED.auto_resolve, "
                + "updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, action.getNodeId());
            ps.setString(2, action.getTrapOid());
            ps.setString(3, action.getTrapName());
            ps.setString(4, action.getSeverity() != null ? action.getSeverity().name() : TrapSeverity.INFO.name());
            ps.setString(5, action.getActionType() != null ? action.getActionType() : "NONE");
            ps.setString(6, action.getTargetPayload());
            ps.setBoolean(7, action.isAutoResolve());
            ps.executeUpdate();
        }
    }

    public void deleteByNodeIdAndOidNotIn(Long nodeId, List<String> oidsToKeep) throws SQLException {
        if (oidsToKeep == null || oidsToKeep.isEmpty()) {
            String sql = "DELETE FROM trap_actions WHERE node_id = ?";
            try (Connection conn = databaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, nodeId);
                ps.executeUpdate();
            }
            return;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < oidsToKeep.size(); i++) {
            placeholders.append("?");
            if (i < oidsToKeep.size() - 1) placeholders.append(",");
        }

        String sql = "DELETE FROM trap_actions WHERE node_id = ? AND trap_oid NOT IN (" + placeholders.toString() + ")";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nodeId);
            for (int i = 0; i < oidsToKeep.size(); i++) {
                ps.setString(i + 2, oidsToKeep.get(i));
            }
            ps.executeUpdate();
        }
    }

    // Finds a trap action strictly by combining the node ID and the trap OID.
    public Optional<TrapAction> findByNodeAndOid(Long nodeId, String oid) throws SQLException {
        String sql = "SELECT id, node_id, trap_oid, trap_name, severity, description, "
                + "auto_resolve, action_type, target_payload, created_at, updated_at "
                + "FROM trap_actions WHERE node_id = ? AND trap_oid = ?";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nodeId);
            ps.setString(2, oid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    private TrapAction mapRow(ResultSet rs) throws SQLException {
        TrapAction action = new TrapAction();
        action.setId(rs.getLong("id"));
        action.setNodeId(rs.getLong("node_id"));
        action.setTrapOid(rs.getString("trap_oid"));
        action.setTrapName(rs.getString("trap_name"));
        action.setSeverity(TrapSeverity.valueOf(rs.getString("severity")));
        action.setDescription(rs.getString("description"));
        action.setAutoResolve(rs.getBoolean("auto_resolve"));
        action.setActionType(rs.getString("action_type"));
        action.setTargetPayload(rs.getString("target_payload"));
        
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            action.setCreatedAt(created.toInstant());
        }
        
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            action.setUpdatedAt(updated.toInstant());
        }
        
        return action;
    }
}