#!/bin/bash

# Script to clean and rebuild all modules in the ib project

echo "Cleaning and rebuilding all modules..."

# Change to the project root directory
cd "$(dirname "$0")/.."

# Build the parent project
echo "Building parent project..."
mvn clean install -U -DskipTests=true
if [ $? -ne 0 ]; then
    echo "Error building parent project"
    exit 1
fi

# Build the common module
echo "Building common module..."
cd ib-common
mvn clean install -U -DskipTests=true
if [ $? -ne 0 ]; then
    echo "Error building common module"
    exit 1
fi

# Build the server module
echo "Building server module..."
cd ../ib-server
mvn clean install -U -DskipTests=true
if [ $? -ne 0 ]; then
    echo "Error building server module"
    exit 1
fi

# Build the client module
echo "Building client module..."
cd ../ib-client
mvn clean install -U -DskipTests=true
if [ $? -ne 0 ]; then
    echo "Error building client module"
    exit 1
fi

echo "All modules built successfully!"
cd .. 