package com.snmp.manager;

import com.snmp.manager.config.DatabaseConnection;
import com.snmp.manager.dao.NodeDAO;
import com.snmp.manager.dao.TrapActionDAO;
import com.snmp.manager.dao.TrapHistoryDAO;
import com.snmp.manager.service.NodeService;
import com.snmp.manager.service.TrapService;
import com.snmp.manager.snmp.listener.TrapListener;
import com.snmp.manager.snmp.model.TrapEvent;
import com.snmp.manager.snmp.receiver.TrapReceiver;

import java.io.IOException;

/**
 * Application entry point for the SNMP Manager.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("SNMP Manager Started");

        TrapReceiver receiver = new TrapReceiver();
        receiver.addTrapListener(new PersistenceTrapListener());

        try {
            receiver.start();
        } catch (IOException e) {
            System.err.println("Failed to start SNMP receiver: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Bridges received traps to the business/service layer.
     */
    private static class PersistenceTrapListener implements TrapListener {
        @Override
        public void onTrapReceived(TrapEvent event) {
            try {
                DatabaseConnection db = DatabaseConnection.fromResource();
                NodeDAO nodeDAO = new NodeDAO(db);
                TrapActionDAO trapActionDAO = new TrapActionDAO(db);
                TrapHistoryDAO trapHistoryDAO = new TrapHistoryDAO(db);
                NodeService nodeService = new NodeService(nodeDAO);
                TrapService trapService = new TrapService(nodeDAO, trapActionDAO, trapHistoryDAO, nodeService);

                trapService.process(event);
                System.out.println("Trap persisted: node=" + event.getSourceIp()
                        + ", oid=" + event.getTrapOid());
            } catch (Exception e) {
                System.err.println("Failed to process trap: " + e.getMessage());
            }
        }
    }
}
