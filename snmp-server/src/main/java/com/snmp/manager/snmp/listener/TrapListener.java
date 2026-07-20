package com.snmp.manager.snmp.listener;

import com.snmp.manager.snmp.model.TrapEvent;

/**
 * Notified when an SNMP trap is received by the {@link com.snmp.manager.snmp.receiver.TrapReceiver}.
 */
public interface TrapListener {

    /**
     * Invoked when a trap has been received and parsed.
     *
     * @param event the parsed trap event, never {@code null}
     */
    void onTrapReceived(TrapEvent event);
}
