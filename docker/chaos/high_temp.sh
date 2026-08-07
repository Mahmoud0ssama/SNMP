#!/bin/bash
CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi
docker exec "$CONTAINER" sh -c 'echo 85 > /var/snmp/temperature.txt'
echo "⚠️ Temperature spiked to 85C on $CONTAINER. check_temp.sh will catch it!"
