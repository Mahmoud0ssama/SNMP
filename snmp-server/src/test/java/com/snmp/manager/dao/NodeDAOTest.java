package com.snmp.manager.dao;

import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeDAOTest {

    private static DatabaseConnection db;
    private NodeDAO dao;
    private long testNodeId;

    @BeforeAll
    static void initAll() throws Exception {
        db = DatabaseConnection.fromResource();
    }

    @AfterAll
    static void cleanupAll() throws Exception {
        if (db != null) {
            try (var c = db.getConnection();
                 var s = c.createStatement()) {
                s.executeUpdate("DELETE FROM trap_history WHERE source_ip LIKE 'test-junit-%'");
                s.executeUpdate("DELETE FROM nodes WHERE ip_address LIKE 'test-junit-%'");
            }
        }
    }

    @BeforeEach
    void init() throws SQLException {
        dao = new NodeDAO(db);
        try (var c = db.getConnection();
             var ps = c.prepareStatement(
                     "INSERT INTO nodes (name, ip_address, port, status) VALUES (?,?,?,?::node_status) RETURNING id")) {
            ps.setString(1, "JUnitTest");
            ps.setString(2, "test-junit-" + System.nanoTime());
            ps.setInt(3, 162);
            ps.setString(4, "UP");
            try (var rs = ps.executeQuery()) {
                rs.next();
                testNodeId = rs.getLong(1);
            }
        }
    }

    @AfterEach
    void cleanup() throws SQLException {
        try (var c = db.getConnection();
             var s = c.createStatement()) {
            s.executeUpdate("DELETE FROM nodes WHERE id = " + testNodeId);
        }
    }

    @Test
    void findById_existing_returnsNode() throws SQLException {
        Optional<Node> result = dao.findById(testNodeId);

        assertTrue(result.isPresent());
        assertEquals("JUnitTest", result.get().getName());
        assertEquals(NodeStatus.UP, result.get().getStatus());
    }

    @Test
    void findById_missing_returnsEmpty() throws SQLException {
        Optional<Node> result = dao.findById(999999L);

        assertTrue(result.isEmpty());
    }

    @Test
    void save_insertsAndPopulatesId() throws SQLException {
        Node node = new Node();
        node.setName("SavedNode");
        node.setIpAddress("test-junit-" + System.nanoTime());
        node.setPort(162);
        node.setStatus(NodeStatus.UP);

        long id = dao.save(node);

        assertTrue(id > 0);
        assertNotNull(node.getId());
        assertEquals(id, node.getId());
    }

    @Test
    void update_existingNode_modifiesRow() throws SQLException {
        Node node = dao.findById(testNodeId).orElseThrow();
        node.setStatus(NodeStatus.WARNING);
        int updated = dao.update(node);

        assertEquals(1, updated);

        Node reloaded = dao.findById(testNodeId).orElseThrow();
        assertEquals(NodeStatus.WARNING, reloaded.getStatus());
    }
}
