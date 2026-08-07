#!/bin/bash
# Read /etc/node/hardware_specs.cfg (mounted via volume)
# Output key-value pairs for SNMP GET response

CONFIG_FILE="/etc/node/hardware_specs.cfg"

if [ -f "$CONFIG_FILE" ]; then
    cat "$CONFIG_FILE"
else
    echo "NODE_TYPE=UNKNOWN"
    echo "NODE_NAME=UNKNOWN"
    echo "ERROR=CONFIG_NOT_FOUND"
fi
exit 0
