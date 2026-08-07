#!/bin/bash
# Kills the heartbeat daemon to intentionally make the node go offline

if [ -f /tmp/heartbeat.pid ]; then
    kill $(cat /tmp/heartbeat.pid) 2>/dev/null
    rm /tmp/heartbeat.pid
else
    # Fallback to killall if PID file is missing
    killall heartbeat_daemon.sh 2>/dev/null
fi
echo "Heartbeat daemon stopped. The node will now appear offline in the dashboard after 30 seconds."
