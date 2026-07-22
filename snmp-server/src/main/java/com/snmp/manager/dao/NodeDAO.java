package com.snmp.manager.dao;

import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Optional;

// Data access object for Node entities in table nodes.
public class NodeDAO {

    private final DatabaseConnection databaseConnection;

    public NodeDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    // Finds a node by its primary key
    public Optional<Node> findById(Long id) throws SQLException {
        String sql = "SELECT id, name, node_type, ip_address, port, location, description, status, created_at "
                + "FROM nodes WHERE id = ?";
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

    // Finds a node by its IP address.
    public Optional<Node> findByIp(String ipAddress) throws SQLException {
        String sql = "SELECT id, name, node_type, ip_address, port, location, description, status, created_at "
                + "FROM nodes WHERE ip_address = ?";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ipAddress);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }
    
    /**
     * Retrieves all nodes from the database for the dashboard overview.
     * @return List of all nodes
     * @throws SQLException on database access error
     */
    public java.util.List<Node> findAll() throws SQLException {
        String sql = "SELECT id, name, node_type, ip_address, port, location, description, status, created_at "
                   + "FROM nodes ORDER BY name";
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                nodes.add(mapRow(rs));
            }
        }
        return nodes;
    }

    // Inserts a new node and populates its generated id.
    public long save(Node node) throws SQLException {
        String sql = "INSERT INTO nodes (name, node_type, ip_address, port, location, description, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?::node_status)";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, node.getName());
            setNullableString(ps, 2, node.getNodeType());
            ps.setString(3, node.getIpAddress());
            ps.setInt(4, node.getPort());
            setNullableString(ps, 5, node.getLocation());
            setNullableString(ps, 6, node.getDescription());
            ps.setString(7, node.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    node.setId(id);
                    return id;
                }
                throw new SQLException("No generated key returned for inserted node");
            }
        }
    }

    //Updates an existing node's mutable columns.

    public int update(Node node) throws SQLException {
        String sql = "UPDATE nodes SET name = ?, node_type = ?, ip_address = ?, port = ?, location = ?, "
                + "description = ?, status = ?::node_status WHERE id = ?";
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, node.getName());
            setNullableString(ps, 2, node.getNodeType());
            ps.setString(3, node.getIpAddress());
            ps.setInt(4, node.getPort());
            setNullableString(ps, 5, node.getLocation());
            setNullableString(ps, 6, node.getDescription());
            ps.setString(7, node.getStatus().name());
            ps.setLong(8, node.getId());
            return ps.executeUpdate();
        }
    }

    private Node mapRow(ResultSet rs) throws SQLException {
        Node node = new Node();
        node.setId(rs.getLong("id"));
        node.setName(rs.getString("name"));
        node.setNodeType(rs.getString("node_type"));
        node.setIpAddress(rs.getString("ip_address"));
        node.setPort(rs.getInt("port"));
        node.setLocation(rs.getString("location"));
        node.setDescription(rs.getString("description"));
        node.setStatus(NodeStatus.valueOf(rs.getString("status")));
        node.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        return node;
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
