#!/bin/bash
# Target Node: Provided via argument $1
# Action: Brings the eth0 network interface back UP to resolve link failure

CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi

# Enable the main network interface
docker exec "$CONTAINER" ip link set eth0 up
echo "✅ Network interface eth0 is UP on $CONTAINER"