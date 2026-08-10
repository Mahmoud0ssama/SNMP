#!/bin/bash

FLAG_FILE="/tmp/high_temp.flag"
MODE_FILE="/var/snmp/mode.cfg"

# Default Mode is file (Simulation)
MODE="file"
if [ -f "$MODE_FILE" ]; then
    MODE=$(cat "$MODE_FILE")
fi

if [ "$MODE" = "sensor" ]; then
    # Mode 1: Read Real Hardware Data
    TEMP=$(sensors 2>/dev/null | grep -i 'Core 0' | awk '{print $3}' | tr -d '+°C' | cut -d. -f1)
    if [ -z "$TEMP" ]; then TEMP=40; fi
else
    # Mode 2: Read Simulated File Data
    if [ ! -f /var/snmp/temperature.txt ]; then
        mkdir -p /var/snmp
        echo 45 > /var/snmp/temperature.txt
    fi
    TEMP=$(cat /var/snmp/temperature.txt)
fi

# Alarm Logic
if [ "$TEMP" -gt 82 ]; then
    if [ ! -f "$FLAG_FILE" ]; then
        /usr/local/bin/send_trap.sh "HIGH_TEMPERATURE" "Temperature is critical: $TEMP"
        touch "$FLAG_FILE"
    fi
else
    if [ -f "$FLAG_FILE" ]; then
        rm -f "$FLAG_FILE"
    fi
fi

echo "$TEMP"