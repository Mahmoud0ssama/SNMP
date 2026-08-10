#!/bin/bash

NODE=""
MODE=""

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --node) NODE="$2"; shift ;;
        --mode) MODE="$2"; shift ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

if [ -z "$NODE" ] || [ -z "$MODE" ]; then
    echo "Usage: ./simulator.sh --node <container_name | all> --mode <sensor|file>"
    echo "Example 1: ./simulator.sh --node cairo-bts-01 --mode sensor"
    echo "Example 2: ./simulator.sh --node all --mode file"
    exit 1
fi

if [ "$MODE" != "sensor" ] && [ "$MODE" != "file" ]; then
    echo "Error: Mode must be 'sensor' or 'file'"
    exit 1
fi

update_node() {
    local target_node=$1
    docker exec "$target_node" sh -c "mkdir -p /var/snmp && echo '$MODE' > /var/snmp/mode.cfg"
    if [ $? -eq 0 ]; then
        echo "✅ Success: Node [$target_node] dynamically switched to [$MODE] mode."
    else
        echo "❌ Failed to update node [$target_node]. Is it running?"
    fi
}

if [ "$NODE" = "all" ]; then
    echo "🔄 Updating all running telecom nodes..."
    # Finds running containers whose names match our telecom nodes pattern
    CONTAINERS=$(docker ps --format '{{.Names}}' | grep -E 'bts|bsc|msc|sgsn|node' || true)
    
    if [ -z "$CONTAINERS" ]; then
        echo "⚠️ No running telecom nodes found."
        exit 1
    fi
    
    for c in $CONTAINERS; do
        update_node "$c"
    done
else
    update_node "$NODE"
fi
