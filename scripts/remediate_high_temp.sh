#!/bin/bash
# Kill stress-ng to relieve CPU and reduce temperature
echo "Terminating stress-ng processes to cool down..."
pkill -f stress-ng
echo "Cooling down initiated."
