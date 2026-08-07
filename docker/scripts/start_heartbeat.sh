#!/bin/bash
# Stops any existing daemon and starts a new one

/usr/local/bin/stop_heartbeat.sh
/usr/local/bin/heartbeat_daemon.sh &
echo $! > /tmp/heartbeat.pid
echo "Heartbeat daemon started successfully."
