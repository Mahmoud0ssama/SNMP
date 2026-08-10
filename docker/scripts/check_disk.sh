#!/bin/bash

FLAG_FILE="/tmp/disk_full_alarm.flag"
LOG_DIR="/var/log/telecom"
SIMULATION_THRESHOLD=100
MODE_FILE="/var/snmp/mode.cfg"

BASE_SIZE=42 

# Default Mode is file (Simulation)
MODE="file"
if [ -f "$MODE_FILE" ]; then
    MODE=$(cat "$MODE_FILE")
fi

if [ "$MODE" = "sensor" ] || [ "$MODE" = "real" ]; then
    # Mode 1: Real Hardware Data
    DIR_SIZE=$(df -m / | awk 'NR==2 {print $3}')
    PERCENT=$(df -h / | awk 'NR==2 {print $5}' | tr -d '%')
    
    if [ "$PERCENT" -gt 90 ]; then
        IS_CRITICAL=1
    else
        IS_CRITICAL=0
    fi
else
    # Mode 2: Simulated File Data
    if [ -d "$LOG_DIR" ]; then
        ACTUAL_SIZE=$(du -m "$LOG_DIR" | tail -n1 | awk '{print $1}')
        DIR_SIZE=$((ACTUAL_SIZE + BASE_SIZE))
    else
        DIR_SIZE=$BASE_SIZE
    fi
    
    if [ "$DIR_SIZE" -gt "$SIMULATION_THRESHOLD" ]; then
        IS_CRITICAL=1
    else
        IS_CRITICAL=0
    fi
fi

# Alarm Logic
if [ "$IS_CRITICAL" -eq 1 ]; then
    if [ ! -f "$FLAG_FILE" ]; then
        /usr/local/bin/send_trap.sh "DISK_FULL" "Critical: Storage exceeded threshold (Used: $DIR_SIZE MB)"
        touch "$FLAG_FILE"
    fi
else
    if [ -f "$FLAG_FILE" ]; then
        rm -f "$FLAG_FILE"
    fi
fi

echo "$DIR_SIZE"