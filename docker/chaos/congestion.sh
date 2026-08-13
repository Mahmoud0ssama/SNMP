#!/bin/bash
# Target Node: Provided via argument $1
# Action: Runs stress-ng to spike CPU and RAM usage, simulating network congestion

CONTAINER=$1
if [ -z "$CONTAINER" ]; then 
    echo "Usage: $0 <container_name>"
    exit 1
fi

# Run stress-ng in the background (-d flag for docker exec)
docker exec -d "$CONTAINER" stress-ng --cpu 4 --vm 2 --vm-bytes 128M --timeout 60
echo "⚠️ Congestion (CPU/RAM stress) started on $CONTAINER for 60 seconds."