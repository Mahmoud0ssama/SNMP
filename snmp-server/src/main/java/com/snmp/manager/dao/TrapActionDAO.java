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

// Data access object for TrapAction definitions in table trap_actions.

public class TrapActionDAO {

    private final DatabaseConnection databaseConnection;

    public TrapActionDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    // Finds a trap action by its primary key.
    public Optional<TrapAction> findById(Long id) throws SQLException {
        String sql = "SELECT id, trap_oid, trap_name, severity, action_name, description, "
                + "auto_resolve, created_at, updated_at FROM trap_actions WHERE id = ?";
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

    // Finds a trap action by its trap OID.
    public Optional<TrapAction> findByOid(String oid) throws SQLException {
        String sql = "SELECT id, trap_oid, trap_name, severity, action_name, description, "
                + "auto_resolve, created_at, updated_at FROM trap_actions WHERE trap_oid = ?";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, oid);
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
        action.setTrapOid(rs.getString("trap_oid"));
        action.setTrapName(rs.getString("trap_name"));
        action.setSeverity(TrapSeverity.valueOf(rs.getString("severity")));
        action.setActionName(rs.getString("action_name"));
        action.setDescription(rs.getString("description"));
        action.setAutoResolve(rs.getBoolean("auto_resolve"));
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
