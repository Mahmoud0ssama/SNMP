#!/bin/bash
# Target Node: Provided via argument $1
# Action: Generates a massive 200MB log file to consume storage and trigger disk alarm

CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi

echo "Simulating log explosion on node ($CONTAINER)..."

# Create a 200MB dummy file inside the container's log directory
docker exec -i "$CONTAINER" bash -c "mkdir -p /var/log/telecom && dd if=/dev/zero of=/var/log/telecom/error_flood.log bs=1M count=200 status=none"

echo "⚠️ Disk space consumed on $CONTAINER. Node storage is critically high!"