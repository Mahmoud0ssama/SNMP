#!/bin/bash
# Checks disk usage and triggers a trap if it exceeds 90%

# Get the root partition usage percentage
if [ -f "/var/snmp/disk_full.flag" ]; then
    # Simulated disk full via chaos script
    DISK_USAGE=95
else
    # Actual disk usage
    DISK_USAGE=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')
fi

if [ "$DISK_USAGE" -gt 90 ]; then
    # Call the send_trap script if threshold exceeded
    /usr/local/bin/send_trap.sh "DISK_FULL" "/ at ${DISK_USAGE}%"
fi

# Output current percentage for SNMP GET polling
echo "${DISK_USAGE}%"
exit 0
