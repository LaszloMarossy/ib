#!/bin/bash

echo "Building project with interface changes..."

# Navigate to project root
cd "$(dirname "$0")/.."

# Build the common module first
echo "Building common module..."
cd ib-common
mvn clean install
cd ..

# Build the client module
echo "Building client module..."
cd ib-client
mvn clean package
cd ..

echo "Build completed. Run ./scripts/rc.sh for chart application or ./scripts/rt.sh for trading application."
