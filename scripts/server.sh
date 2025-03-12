#!/bin/bash

# Change to the ib-server directory relative to the script location
cd "$(dirname "$0")/../ib-server"

# Run the Spring Boot server
echo "Running the spring boot server..."
java -jar target/ib-server-0.0.1-SNAPSHOT.jar