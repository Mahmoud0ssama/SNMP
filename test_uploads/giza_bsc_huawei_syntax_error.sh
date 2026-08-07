#!/bin/bash
# Target Node: giza-bsc-01
# Vendor: Huawei (BSC)
# ERROR: Intentional syntax errors for AI testing

echo "Starting diagnostic on Huawei BSC...

# Checking interface status
if [ -z "$INTERFACE" ]; then
    echo "Interface is down, attempting to bring it up..."
    ip link set eth0 up

# Missing 'fi' here to close the if statement!
# Unclosed quote in the first echo!

echo "Diagnostic complete."
exit 0
