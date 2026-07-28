package com.snmp.manager.service;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeServiceTest {

    @Mock
    NodeDAO nodeDAO;

    @Test
    void findById_existingNode_returnsNode() throws SQLException {
        Node node = new Node();
        node.setId(5L);
        node.setStatus(NodeStatus.UP);
        when(nodeDAO.findById(5L)).thenReturn(Optional.of(node));

        NodeService service = new NodeService(nodeDAO);
        Optional<Node> result = service.findById(5L);

        assertTrue(result.isPresent());
        assertEquals(NodeStatus.UP, result.get().getStatus());
        verify(nodeDAO).findById(5L);
    }

    @Test
    void findById_missingNode_returnsEmpty() throws SQLException {
        when(nodeDAO.findById(99L)).thenReturn(Optional.empty());

        NodeService service = new NodeService(nodeDAO);
        Optional<Node> result = service.findById(99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void updateStatus_updatesNodeAndPersists() throws SQLException {
        Node node = new Node();
        node.setId(7L);
        node.setStatus(NodeStatus.UP);
        when(nodeDAO.update(node)).thenReturn(1);

        NodeService service = new NodeService(nodeDAO);
        int rows = service.updateStatus(node, NodeStatus.DOWN);

        assertEquals(1, rows);
        assertEquals(NodeStatus.DOWN, node.getStatus());
        verify(nodeDAO).update(node);
    }
}
