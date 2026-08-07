#!/bin/bash
# Sends an SNMP trap to the NMS Server
# Usage: send_trap.sh <ALARM_TYPE> "<DETAILS>"

ALARM_TYPE=$1
DETAILS=$2

# Define OIDs based on the Java Enum (AlarmType.java)
case "$ALARM_TYPE" in
    "DISK_FULL")         TRAP_OID="1.3.6.1.4.1.99999.0.1" ;;
    "POWER_FAILURE")     TRAP_OID="1.3.6.1.4.1.99999.0.2" ;;
    "LINK_DOWN")         TRAP_OID="1.3.6.1.4.1.99999.0.3" ;;
    "CONGESTION")        TRAP_OID="1.3.6.1.4.1.99999.0.4" ;;
    "HIGH_TEMPERATURE")  TRAP_OID="1.3.6.1.4.1.99999.0.5" ;;
    "MEMORY_EXHAUSTION") TRAP_OID="1.3.6.1.4.1.99999.0.6" ;;
    "CONFIG_ERROR")      TRAP_OID="1.3.6.1.4.1.99999.0.7" ;;
    *)                   TRAP_OID="1.3.6.1.4.1.99999.0.99" ;; # Unknown
esac

# Ensure NMS_SERVER_IP is set (passed from docker-compose)
if [ -z "$NMS_SERVER_IP" ]; then
    NMS_SERVER_IP="172.20.0.2" # Default fallback
fi

# Node information from environment variables (passed from docker-compose)
NODE_NAME=${NODE_NAME:-"Unknown_Node"}
NODE_TYPE=${NODE_TYPE:-"Unknown_Type"}
NODE_IP=$(ip -4 addr show eth0 | grep inet | awk '{print $2}' | cut -d/ -f1)

# Execute snmptrap command
snmptrap -v 2c -c public $NMS_SERVER_IP:162 '' $TRAP_OID \
    1.3.6.1.4.1.99999.1.1 s "$NODE_NAME" \
    1.3.6.1.4.1.99999.1.2 s "$NODE_TYPE" \
    1.3.6.1.4.1.99999.1.3 s "$DETAILS" \
    1.3.6.1.4.1.99999.1.4 s "$NODE_IP"

echo "Trap $ALARM_TYPE sent to $NMS_SERVER_IP"
exit 0
