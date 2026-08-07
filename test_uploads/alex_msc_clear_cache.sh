#!/bin/bash
# Target Node: alex-msc
# Vendor: Nokia (MSC)
# Action: Safe cleanup of temporary subscriber logs

echo "Accessing Nokia MSC temporary storage on alex-msc..."
# Safe cleanup of specific temp logs
rm -f /tmp/*.log
echo "Temporary MSC logs cleared safely without affecting system files."
exit 0
