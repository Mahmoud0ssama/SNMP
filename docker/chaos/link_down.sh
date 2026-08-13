#!/bin/bash
# Target Node: Provided via argument $1
# Action: Brings the eth0 network interface DOWN to simulate link failure/isolation

CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi

# Disable the main network interface
docker exec "$CONTAINER" ip link set eth0 down
echo "⚠️ Network interface eth0 is DOWN on $CONTAINER"