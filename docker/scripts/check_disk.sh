#!/bin/bash

FLAG_FILE="/tmp/disk_full_alarm.flag"
LOG_DIR="/var/log/telecom"

if [ -d "$LOG_DIR" ]; then
    DIR_SIZE=$(du -m "$LOG_DIR" | tail -n1 | awk '{print $1}')
else
    DIR_SIZE=0
fi

if [ "$DIR_SIZE" -gt 100 ]; then
    if [ ! -f "$FLAG_FILE" ]; then
        /usr/local/bin/send_trap.sh "DISK_FULL" "Critical: Storage exceeded threshold ($DIR_SIZE MB used)"
        touch "$FLAG_FILE"
    fi
else
    if [ -f "$FLAG_FILE" ]; then
        rm -f "$FLAG_FILE"
    fi
fi

echo "$DIR_SIZE"