#!/bin/bash

FLAG_FILE="/tmp/congestion_alarm.flag"
CONGESTED=0

if pgrep stress-ng > /dev/null; then
    CONGESTED=1
    if [ ! -f "$FLAG_FILE" ]; then
        /usr/local/bin/send_trap.sh "CONGESTION" "High CPU/RAM usage detected"
        touch "$FLAG_FILE"
    fi
else
    CONGESTED=0
    if [ -f "$FLAG_FILE" ]; then
        rm -f "$FLAG_FILE"
    fi
fi

echo "$CONGESTED"