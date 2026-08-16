#!/bin/bash
# This script runs INSIDE the container
echo "Removing error_flood.log to free up disk space..."
rm -f /var/log/telecom/error_flood.log
echo "Disk space cleared."
