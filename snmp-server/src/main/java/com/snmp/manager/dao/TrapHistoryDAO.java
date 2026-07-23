package com.snmp.manager.dao;

import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.model.TrapHistory;
import com.snmp.manager.model.TrapStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

// Data access object for {@link TrapHistory} records (table {@code trap_history}).
public class TrapHistoryDAO {

    private final DatabaseConnection databaseConnection;

    public TrapHistoryDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    // Inserts a trap history record and populates its generated id.
    public long save(TrapHistory history) throws SQLException {
        String sql = "INSERT INTO trap_history (node_id, trap_action_id, trap_oid, source_ip, "
                + "message, status) VALUES (?, ?, ?, ?, ?, ?::trap_status)";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, history.getNodeId());
            setNullableLong(ps, 2, history.getTrapActionId());
            ps.setString(3, history.getTrapOid());
            ps.setString(4, history.getSourceIp());
            setNullableString(ps, 5, history.getMessage());
            ps.setString(6, history.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    history.setId(id);
                    return id;
                }
                throw new SQLException("No generated key returned for inserted trap history");
            }
        }
    }
    /**
     * Retrieves all trap history records from the database, ordered by newest first.
     * @return List of traps
     * @throws SQLException on database access error
     */
    public java.util.List<TrapHistory> findAll() throws SQLException {
        String sql = "SELECT id, node_id, trap_action_id, trap_oid, source_ip, message, status, received_at, resolved_at "
                   + "FROM trap_history ORDER BY received_at DESC";
        java.util.List<TrapHistory> traps = new java.util.ArrayList<>();
        
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                TrapHistory history = new TrapHistory();
                history.setId(rs.getLong("id"));
                history.setNodeId(rs.getLong("node_id"));
                
                long actionId = rs.getLong("trap_action_id");
                if (!rs.wasNull()) history.setTrapActionId(actionId);
                
                history.setTrapOid(rs.getString("trap_oid"));
                history.setSourceIp(rs.getString("source_ip"));
                history.setMessage(rs.getString("message"));
                history.setStatus(com.snmp.manager.model.TrapStatus.valueOf(rs.getString("status")));
                history.setReceivedAt(rs.getTimestamp("received_at").toInstant());
                
                java.sql.Timestamp resolvedAt = rs.getTimestamp("resolved_at");
                if (resolvedAt != null) {
                    history.setResolvedAt(resolvedAt.toInstant());
                }
                
                traps.add(history);
            }
        }
        return traps;
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
    
    public boolean resolveTrap(Long trapId, Long userId) throws SQLException {
        String sql = "UPDATE trap_history SET status = 'RESOLVED'::trap_status, resolved_at = CURRENT_TIMESTAMP, resolved_by = ? WHERE id = ?";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, trapId);
            return ps.executeUpdate() > 0;
        }
    }
    
}
