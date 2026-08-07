#!/bin/bash
CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi
docker exec "$CONTAINER" ip link set eth0 up
echo "✅ Network interface eth0 is UP on $CONTAINER"
