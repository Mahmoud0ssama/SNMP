#!/bin/bash
CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi
# Write 500MB of zero bytes to a temp file
docker exec "$CONTAINER" dd if=/dev/zero of=/tmp/fill_disk bs=1M count=500
echo "⚠️ Disk filled with 500MB on $CONTAINER. check_disk.sh will catch it!"
