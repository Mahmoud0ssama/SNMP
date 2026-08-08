#!/bin/bash
# Continuously monitor all node health metrics every 10 seconds

while true; do
    # Run all checks silently in the background
    /usr/local/bin/check_temp.sh > /dev/null 2>&1
    /usr/local/bin/check_disk.sh > /dev/null 2>&1
    /usr/local/bin/check_congestion.sh > /dev/null 2>&1
    
    # Wait 10 seconds before the next sweep
    sleep 10
done
