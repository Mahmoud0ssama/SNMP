#!/bin/bash
# Target Node: giza-bsc-01
# Action: Generates a massive 200MB log file to consume storage

echo "Simulating log explosion on Huawei BSC (giza-bsc-01)..."

# Create a 200MB dummy file inside the container's log directory
docker exec -i giza-bsc-01 bash -c "mkdir -p /var/log/telecom && dd if=/dev/zero of=/var/log/telecom/error_flood.log bs=1M count=200 status=none"

echo "Disk space consumed. Node storage is critically high!"