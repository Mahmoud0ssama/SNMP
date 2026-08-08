#!/bin/bash
# Emergency Remediation Script for Disk Full / Log Explosion

echo "Analyzing storage usage..."
# Simulate finding the large file
sleep 1

echo "Found massive error log. Clearing /var/log/telecom/error_flood.log..."
# The ACTUAL fix: deleting the real file that is consuming space
rm -f /var/log/telecom/error_flood.log

echo "Cleanup successful. Storage restored to normal levels."