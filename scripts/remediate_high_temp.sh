#!/bin/bash
# This script runs INSIDE the container
echo "Restoring normal temperature..."
echo 45 > /var/snmp/temperature.txt
echo "Temperature restored."
