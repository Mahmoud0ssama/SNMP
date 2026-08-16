#!/bin/bash
# This script runs INSIDE the container
echo "Terminating stress-ng processes..."
pkill -f stress-ng
echo "CPU congestion relieved."
