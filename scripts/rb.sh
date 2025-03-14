#!/bin/bash
cd "/Users/laszlo/dev/code/ibtrader/ib-client"

# Build the application
echo "Building the client application..."
mvn clean package
echo "Build completed. Run ./scripts/rc.sh to start the chart application or ./scripts/rt.sh to start the trading application."
