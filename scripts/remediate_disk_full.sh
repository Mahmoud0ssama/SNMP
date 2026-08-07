#!/bin/bash
# Remove the simulation flag created by the chaos script
echo "Removing simulated disk full flag to free up disk space..."
rm -f /var/snmp/disk_full.flag
echo "Disk space cleared."
