#!/bin/bash
# Kill stress-ng to relieve CPU congestion
echo "Terminating stress-ng processes..."
pkill -f stress-ng
echo "CPU congestion relieved."
