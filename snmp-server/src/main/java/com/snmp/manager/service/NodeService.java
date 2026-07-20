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

    /**
     * Looks up a node by its identifier.
     *
     * @param id the node id
     * @return the node if present
     * @throws SQLException on database access error
     */
    public Optional<Node> findById(Long id) throws SQLException {
        return nodeDAO.findById(id);
    }

    /**
     * Looks up a node by its IP address.
     *
     * @param ipAddress the node IP address
     * @return the node if present
     * @throws SQLException on database access error
     */
    public Optional<Node> findByIp(String ipAddress) throws SQLException {
        return nodeDAO.findByIp(ipAddress);
    }

    /**
     * Updates the operational status of an existing node.
     *
     * @param node   the node to update, must have an id
     * @param status the new status
     * @return the number of rows updated
     * @throws SQLException on database access error
     */
    public int updateStatus(Node node, NodeStatus status) throws SQLException {
        node.setStatus(status);
        return nodeDAO.update(node);
    }
}
