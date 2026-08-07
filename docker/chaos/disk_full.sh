#!/bin/bash
CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi
# Touch a flag file that check_disk.sh will interpret as 95% full
docker exec "$CONTAINER" touch /var/snmp/disk_full.flag
echo "⚠️ Simulated Disk Full (95%) on $CONTAINER. check_disk.sh will catch it!"
