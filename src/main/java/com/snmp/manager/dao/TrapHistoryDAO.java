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
}
