#!/bin/bash

# Start the heartbeat daemon in the background
/usr/local/bin/start_heartbeat.sh

# Start the SNMP agent in the foreground
exec snmpd -f -Lo -C -c /etc/snmp/snmpd.conf
