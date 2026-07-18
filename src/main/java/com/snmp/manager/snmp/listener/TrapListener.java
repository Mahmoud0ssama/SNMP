package com.snmp.manager.snmp.listener;

/**
 * Notified when an SNMP trap is received by the {@link com.snmp.manager.snmp.receiver.TrapReceiver}.
 */
public interface TrapListener {

    /**
     * Invoked when a trap has been received.
     */
    void onTrapReceived();
}
