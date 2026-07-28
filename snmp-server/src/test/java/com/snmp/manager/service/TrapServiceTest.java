package com.snmp.manager.service;

import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.dao.TrapActionDAO;
import com.snmp.manager.dao.TrapHistoryDAO;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import com.snmp.manager.model.TrapAction;
import com.snmp.manager.model.TrapHistory;
import com.snmp.manager.model.TrapSeverity;
import com.snmp.manager.snmp.model.TrapEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrapServiceTest {

    @Mock
    NodeDAO nodeDAO;
    @Mock
    TrapActionDAO trapActionDAO;
    @Mock
    TrapHistoryDAO trapHistoryDAO;
    @Mock
    DatabaseConnection databaseConnection;
    @Mock
    NodeService nodeService;

    @Test
    void process_nullEvent_throws() throws SQLException {
        TrapService service = new TrapService(nodeDAO, trapActionDAO, trapHistoryDAO, nodeService, databaseConnection);

        assertThrows(IllegalArgumentException.class, () -> service.process(null));
    }

    @Test
    void process_existingNodeAndAction_persistsHistoryAndUpdatesStatus() throws SQLException {
        Node node = new Node();
        node.setId(1L);
        node.setIpAddress("10.0.0.5");
        node.setStatus(NodeStatus.UP);

        TrapAction action = new TrapAction();
        action.setId(10L);
        action.setTrapOid("1.3.6.1.6.3.1.1.5.1");
        action.setTrapName("Link Down");
        action.setSeverity(TrapSeverity.CRITICAL);
        action.setActionType("script");
        action.setTargetPayload("/tmp/test.sh");

        TrapEvent event = new TrapEvent("10.0.0.5/162", "1.3.6.1.6.3.1.1.5.1",
                java.time.Instant.now(), "public", "SNMPv2c", Collections.emptyMap(),
                null, null, null, null);

        when(nodeDAO.findByIp("10.0.0.5")).thenReturn(Optional.of(node));
        when(trapActionDAO.findByNodeAndOid(1L, "1.3.6.1.6.3.1.1.5.1")).thenReturn(Optional.of(action));

        TrapService service = new TrapService(nodeDAO, trapActionDAO, trapHistoryDAO, nodeService, databaseConnection);
        service.process(event);

        verify(trapHistoryDAO).save(any(TrapHistory.class));
        verify(nodeService).updateStatus(node, NodeStatus.DOWN);
    }

    @Test
    void process_unknownNode_autoRegisters() throws SQLException {
        Node registered = new Node();
        registered.setId(2L);
        registered.setIpAddress("10.0.0.99");
        registered.setStatus(NodeStatus.UP);

        TrapEvent event = new TrapEvent("10.0.0.99/162", "1.3.6.1.6.3.1.1.5.1",
                java.time.Instant.now(), "public", "SNMPv2c", Collections.emptyMap(),
                "NewNode", "switch", "10.0.0.99", null);

        when(nodeDAO.findByIp("10.0.0.99")).thenReturn(Optional.empty());
        when(nodeService.registerNode("NewNode", "10.0.0.99", "switch")).thenReturn(registered);

        TrapService service = new TrapService(nodeDAO, trapActionDAO, trapHistoryDAO, nodeService, databaseConnection);
        service.process(event);

        verify(nodeService).registerNode("NewNode", "10.0.0.99", "switch");
        verify(trapHistoryDAO).save(any(TrapHistory.class));
    }

    @Test
    void resolveStatus_criticalMapsToDown() throws SQLException {
        TrapAction critical = new TrapAction();
        critical.setSeverity(TrapSeverity.CRITICAL);

        TrapAction minor = new TrapAction();
        minor.setSeverity(TrapSeverity.MINOR);

        TrapAction info = new TrapAction();
        info.setSeverity(TrapSeverity.INFO);

        TrapService service = new TrapService(nodeDAO, trapActionDAO, trapHistoryDAO, nodeService, databaseConnection);

        // Use reflection to access private method for unit testing
        try {
            java.lang.reflect.Method m = TrapService.class.getDeclaredMethod("resolveStatus", TrapAction.class);
            m.setAccessible(true);
            assertEquals(NodeStatus.DOWN, m.invoke(service, critical));
            assertEquals(NodeStatus.WARNING, m.invoke(service, minor));
            assertEquals(NodeStatus.UP, m.invoke(service, info));
            assertEquals(NodeStatus.WARNING, m.invoke(service, (TrapAction) null));
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }
}
