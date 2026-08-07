#!/bin/bash
# Continuously sends UDP heartbeats to the server every 10 seconds

NMS_SERVER_IP=${NMS_SERVER_IP:-"172.25.0.1"}

while true; do
    NODE_IP=$(ip -4 addr show eth0 | grep inet | awk '{print $2}' | cut -d/ -f1)
    if [ -n "$NODE_IP" ]; then
        # Payload format: NODE_IP|TIMESTAMP
        echo "$NODE_IP|$(date +%s)" > /dev/udp/$NMS_SERVER_IP/1162
    fi
    sleep 10
done
