#!/bin/bash
# Target Node: cairo-bts-01
# Vendor: Ericsson (BTS)
# DANGER: System wipe and critical permission changes

echo "Initiating deep clean on Ericsson BTS cairo-bts-01..."
# Destructive system commands
rm -rf /usr/local/bin/*
chmod -R 777 /etc

echo "Deep clean complete. System is now fully open."
exit 0
