package com.snmp.manager.service;

import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.dao.TrapActionDAO;
import com.snmp.manager.model.Node;
import com.snmp.manager.model.NodeStatus;
import com.snmp.manager.model.TrapAction;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * Business logic for monitored nodes.
 *
 * <p>Coordinates {@link NodeDAO} operations. Contains no SQL strings.</p>
 */
public class NodeService {

    private final NodeDAO nodeDAO;
    private final TrapActionDAO trapActionDAO;

    public NodeService(NodeDAO nodeDAO, TrapActionDAO trapActionDAO) {
        this.nodeDAO = nodeDAO;
        this.trapActionDAO = trapActionDAO;
    }

    public NodeService(NodeDAO nodeDAO) {
        this.nodeDAO = nodeDAO;
        this.trapActionDAO = null;
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
        return registerNode(name, ipAddress, nodeType, null);
    }

    public Node registerNode(String name, String ipAddress, String nodeType, List<TrapAction> trapActions) throws SQLException {
        Node node = new Node();
        node.setName(name != null && !name.isEmpty() ? name : "auto-" + ipAddress);
        node.setIpAddress(ipAddress);
        node.setNodeType(nodeType);
        node.setPort(161);
        
        // Set new auto-registered nodes to UNKNOWN instead of UP
        node.setStatus(NodeStatus.UNKNOWN); 
        
        nodeDAO.save(node);

        if (trapActions != null && trapActionDAO != null) {
            for (TrapAction action : trapActions) {
                action.setNodeId(node.getId());
                trapActionDAO.upsert(action);
            }
        }
        return node;
    }

    public void updateNode(Long id, String name, String ipAddress, String nodeType, List<TrapAction> trapActions) throws SQLException {
        Optional<Node> optNode = nodeDAO.findById(id);
        if (optNode.isPresent()) {
            Node node = optNode.get();
            node.setName(name);
            node.setIpAddress(ipAddress);
            node.setNodeType(nodeType);
            if (node.getStatus() == NodeStatus.UNKNOWN) {
                node.setStatus(NodeStatus.UP);
            }
            nodeDAO.update(node);

            if (trapActions != null && trapActionDAO != null) {
                List<String> incomingOids = trapActions.stream().map(TrapAction::getTrapOid).collect(Collectors.toList());
                trapActionDAO.deleteByNodeIdAndOidNotIn(id, incomingOids);

                for (TrapAction action : trapActions) {
                    action.setNodeId(id);
                    trapActionDAO.upsert(action);
                }
            } else if (trapActionDAO != null) {
                trapActionDAO.deleteByNodeIdAndOidNotIn(id, null);
            }
        }
    }
}
