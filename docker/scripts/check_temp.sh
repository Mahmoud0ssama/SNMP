#!/bin/bash

TEMP=$(cat /var/snmp/temperature.txt)
FLAG_FILE="/tmp/high_temp.flag"

if [ "$TEMP" -gt 75 ]; then
    if [ ! -f "$FLAG_FILE" ]; then
        /usr/local/bin/send_trap.sh "HIGH_TEMP" "Temperature is critical: $TEMP"
        touch "$FLAG_FILE"
    fi
else
    if [ -f "$FLAG_FILE" ]; then
        rm -f "$FLAG_FILE"
    fi
fi