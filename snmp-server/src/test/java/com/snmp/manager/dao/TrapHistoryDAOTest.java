package com.snmp.manager.dao;

import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import com.snmp.manager.model.TrapHistory;
import com.snmp.manager.model.TrapStatus;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrapHistoryDAOTest {

    private static DatabaseConnection db;
    private TrapHistoryDAO dao;
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
                s.executeUpdate("DELETE FROM trap_history WHERE trap_oid LIKE 'test-junit-%'");
                s.executeUpdate("DELETE FROM nodes WHERE ip_address LIKE 'test-junit-%'");
            }
        }
    }

    @BeforeEach
    void init() throws SQLException {
        dao = new TrapHistoryDAO(db);
        try (var c = db.getConnection();
             var ps = c.prepareStatement(
                     "INSERT INTO nodes (name, ip_address, port, status) VALUES (?,?,?,?::node_status) RETURNING id")) {
            ps.setString(1, "JUnitHistoryNode");
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
            s.executeUpdate("DELETE FROM trap_history WHERE node_id = " + testNodeId);
            s.executeUpdate("DELETE FROM nodes WHERE id = " + testNodeId);
        }
    }

    @Test
    void save_insertsRecordAndPopulatesId() throws SQLException {
        TrapHistory history = new TrapHistory();
        history.setNodeId(testNodeId);
        history.setTrapOid("test-junit-oid");
        history.setSourceIp("10.0.0.1");
        history.setMessage("test");
        history.setStatus(TrapStatus.OPEN);

        long id = dao.save(history);

        assertTrue(id > 0);
        assertNotNull(history.getId());
    }

    @Test
    void findAll_returnsRecords() throws SQLException {
        TrapHistory history = new TrapHistory();
        history.setNodeId(testNodeId);
        history.setTrapOid("test-junit-list");
        history.setSourceIp("10.0.0.2");
        history.setStatus(TrapStatus.OPEN);
        dao.save(history);

        List<TrapHistory> results = dao.findAll();

        assertTrue(results.stream().anyMatch(h -> "test-junit-list".equals(h.getTrapOid())));
    }
}
