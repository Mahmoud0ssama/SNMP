#!/bin/bash
# Simulates a temperature sensor check

TEMP_FILE="/var/snmp/temperature.txt"

# Create simulated sensor file if it doesn't exist
if [ ! -f "$TEMP_FILE" ]; then
    mkdir -p /var/snmp
    echo "45" > "$TEMP_FILE"
fi

# Read the current simulated temperature
TEMP=$(cat "$TEMP_FILE")

if [ "$TEMP" -gt 75 ]; then
    # Call the send_trap script if threshold exceeded
    /usr/local/bin/send_trap.sh "HIGH_TEMPERATURE" "Sensor: ${TEMP}C"
fi

# Output current temperature for SNMP GET polling
echo "${TEMP}C"
exit 0
