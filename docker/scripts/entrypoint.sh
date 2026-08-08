#!/bin/bash

# Start the heartbeat daemon in the background
/usr/local/bin/start_heartbeat.sh

# Start the fault monitoring daemon in the background
/usr/local/bin/fault_monitor_daemon.sh &

# Start the SNMP agent in the foreground
exec snmpd -f -Lo -C -c /etc/snmp/snmpd.conf
