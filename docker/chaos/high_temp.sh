#!/bin/bash
# Target Node: Provided via argument $1
# Action: Spikes the temperature to 85C to trigger high temperature SNMP trap

CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi

# Overwrite the temperature file with a critical value
docker exec "$CONTAINER" sh -c 'echo 85 > /var/snmp/temperature.txt'
echo "⚠️ Temperature spiked to 85C on $CONTAINER. check_temp.sh will catch it!"