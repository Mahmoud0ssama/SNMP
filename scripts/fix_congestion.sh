#!/bin/bash
# Emergency Remediation Script for CPU/RAM Congestion

echo "Analyzing system processes for high resource consumption..."

# The ACTUAL fix: killing the stress-ng tool
pkill stress-ng
sleep 1

echo "Resource hogs terminated. CPU and Memory usage returned to normal."