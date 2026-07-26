package com.snmp.manager.service;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Business logic for monitored nodes.
 *
 * <p>Coordinates {@link NodeDAO} operations. Contains no SQL strings.</p>
 */
public class NodeService {

    private final NodeDAO nodeDAO;

    public NodeService(NodeDAO nodeDAO) {
        this.nodeDAO = nodeDAO;
    }

    // Looks up a node by its identifier.

    public Optional<Node> findById(Long id) throws SQLException {
        return nodeDAO.findById(id);
    }

    // Looks up a node by its IP address.
    public Optional<Node> findByIp(String ipAddress) throws SQLException {
        return nodeDAO.findByIp(ipAddress);
    }

    // Updates the operational status of an existing node.

    public int updateStatus(Node node, NodeStatus status) throws SQLException {
        node.setStatus(status);
        return nodeDAO.update(node);
    }

    // Registers a new node using data extracted from a received trap.
     
    public Node registerNode(String name, String ipAddress, String nodeType) throws SQLException {
        Node node = new Node();
        node.setName(name != null && !name.isEmpty() ? name : "auto-" + ipAddress);
        node.setIpAddress(ipAddress);
        node.setNodeType(nodeType);
        node.setPort(161);
        node.setStatus(NodeStatus.UP);
        nodeDAO.save(node);
        return node;
    }
}
