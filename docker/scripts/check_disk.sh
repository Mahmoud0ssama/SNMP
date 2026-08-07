#!/bin/bash
# Checks disk usage and triggers a trap if it exceeds 90%

# Get the root partition usage percentage
DISK_USAGE=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')

if [ "$DISK_USAGE" -gt 90 ]; then
    # Call the send_trap script if threshold exceeded
    /usr/local/bin/send_trap.sh "DISK_FULL" "/ at ${DISK_USAGE}%"
fi

# Output current percentage for SNMP GET polling
echo "${DISK_USAGE}%"
exit 0
