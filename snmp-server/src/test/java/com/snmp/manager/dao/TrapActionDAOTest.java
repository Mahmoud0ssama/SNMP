package com.snmp.manager.dao;

import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.model.TrapAction;
import com.snmp.manager.model.TrapSeverity;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrapActionDAOTest {

    private static DatabaseConnection db;
    private TrapActionDAO dao;
    private long testNodeId;
    private long testActionId;

    @BeforeAll
    static void initAll() throws Exception {
        db = DatabaseConnection.fromResource();
    }

    @AfterAll
    static void cleanupAll() throws Exception {
        if (db != null) {
            try (var c = db.getConnection();
                 var s = c.createStatement()) {
                s.executeUpdate("DELETE FROM trap_history WHERE trap_oid LIKE 'test-junit-%'");
                s.executeUpdate("DELETE FROM trap_actions WHERE trap_oid LIKE 'test-junit-%'");
                s.executeUpdate("DELETE FROM nodes WHERE ip_address LIKE 'test-junit-%'");
            }
        }
    }

    @BeforeEach
    void init() throws SQLException {
        dao = new TrapActionDAO(db);
        try (var c = db.getConnection();
             var ps = c.prepareStatement(
                     "INSERT INTO nodes (name, ip_address, port, status) VALUES (?,?,?,?::node_status) RETURNING id")) {
            ps.setString(1, "JUnitActionNode");
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
            s.executeUpdate("DELETE FROM trap_actions WHERE id = " + testActionId);
            s.executeUpdate("DELETE FROM nodes WHERE id = " + testNodeId);
        }
    }

    @Test
    void findById_existing_returnsAction() throws SQLException {
        insertAction("1.3.6.1.6.3.1.1.5.99", "TestTrap");

        Optional<TrapAction> result = dao.findById(testActionId);

        assertTrue(result.isPresent());
        assertEquals("TestTrap", result.get().getTrapName());
        assertEquals(TrapSeverity.CRITICAL, result.get().getSeverity());
    }

    @Test
    void findByNodeAndOid_matchesNodeAndOid() throws SQLException {
        insertAction("1.3.6.1.6.3.1.1.5.100", "NodeTrap");

        Optional<TrapAction> result = dao.findByNodeAndOid(testNodeId, "1.3.6.1.6.3.1.1.5.100");

        assertTrue(result.isPresent());
        assertEquals("NodeTrap", result.get().getTrapName());
    }

    @Test
    void findByNodeAndOid_wrongNode_returnsEmpty() throws SQLException {
        insertAction("1.3.6.1.6.3.1.1.5.101", "WrongNodeTrap");

        Optional<TrapAction> result = dao.findByNodeAndOid(999999L, "1.3.6.1.6.3.1.1.5.101");

        assertTrue(result.isEmpty());
    }

    private void insertAction(String oid, String name) throws SQLException {
        try (var c = db.getConnection();
             var ps = c.prepareStatement(
                     "INSERT INTO trap_actions (trap_oid, trap_name, severity, action_type, node_id) VALUES (?,?,?::trap_severity,?,?) RETURNING id")) {
            ps.setString(1, oid);
            ps.setString(2, name);
            ps.setString(3, "CRITICAL");
            ps.setString(4, "script");
            ps.setLong(5, testNodeId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                testActionId = rs.getLong(1);
            }
        }
    }
}
