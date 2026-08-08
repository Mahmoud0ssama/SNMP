#!/bin/bash

FLAG_FILE="/tmp/congestion_alarm.flag"

if pgrep stress-ng > /dev/null; then
    if [ ! -f "$FLAG_FILE" ]; then
        /usr/local/bin/send_trap.sh "CONGESTION" "High CPU/RAM usage detected"
        touch "$FLAG_FILE"
    fi
else
    if [ -f "$FLAG_FILE" ]; then
        rm -f "$FLAG_FILE"
    fi
fi