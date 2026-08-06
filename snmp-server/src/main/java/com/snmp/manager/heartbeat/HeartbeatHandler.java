package com.snmp.manager.heartbeat;

import com.snmp.manager.heartbeat.model.Heartbeat;

/**
 * Callback contract used by {@code HeartbeatReceiver} to forward parsed
 * heartbeat messages to interested parties without coupling the receiver to
 * any business logic.
 */
@FunctionalInterface
public interface HeartbeatHandler {

    /**
     * Invoked whenever a well-formed heartbeat has been received and parsed.
     *
     * @param heartbeat the parsed heartbeat, never {@code null}
     */
    void onHeartbeat(Heartbeat heartbeat);
}
