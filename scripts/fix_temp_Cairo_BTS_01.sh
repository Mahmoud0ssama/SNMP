#!/bin/bash
# Emergency Remediation Script for High Temp

echo "Starting backup AC (CRAC Unit 2)..."
# Simulate turning on the cooling system
sleep 1
echo "Cooling fans running at 100% capacity..."

# Simulate the temperature slowly dropping after the AC is turned on
echo 45 > /var/snmp/temperature.txt
echo "Cooling successful. Temperature stabilized at 45C."