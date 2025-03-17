#!/bin/bash

# This script fixes permissions for all scripts in the directory
echo "Fixing permissions for all scripts in the directory..."

# Remove extended attributes from all shell scripts
echo "Removing extended attributes..."
xattr -c *.sh

# Make all shell scripts executable
echo "Making all shell scripts executable..."
chmod +x *.sh

# Fix line endings (in case scripts were edited on Windows)
echo "Fixing line endings..."
for file in *.sh; do
    sed -i '' 's/\r$//' "$file"
done

echo "Done! All scripts should now be executable."
echo "To run Zookeeper and Kafka, open two separate terminal windows and run:"
echo "  1. ./start-zookeeper.sh"
echo "  2. ./start-kafka.sh"
echo ""
echo "To check if Kafka is running properly, run:"
echo "  ./check-kafka.sh" 