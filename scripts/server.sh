#!/bin/bash

# Change to the project root directory
cd "$(dirname "$0")/.."
PROJECT_ROOT=$(pwd)

# Check if the JAR file exists and when it was last modified
JAR_FILE="$PROJECT_ROOT/ib-server/target/ib-server-0.0.1-SNAPSHOT.jar"
REBUILD_SCRIPT="$PROJECT_ROOT/scripts/rebuild-all.sh"

# Function to check if rebuild is needed
need_rebuild() {
    if [ ! -f "$JAR_FILE" ]; then
        echo "JAR file does not exist. Rebuilding..."
        return 0
    fi
    
    # Check if any source files are newer than the JAR
    find "$PROJECT_ROOT" -name "*.java" -newer "$JAR_FILE" | grep -q . && {
        echo "Source files have been modified since last build. Rebuilding..."
        return 0
    }
    
    return 1
}

# Rebuild if needed
if need_rebuild; then
    echo "Running rebuild-all.sh to ensure latest changes are included..."
    "$REBUILD_SCRIPT"
    
    # Check if rebuild was successful
    if [ $? -ne 0 ]; then
        echo "Rebuild failed. Please check for errors."
        exit 1
    fi
    echo "Rebuild completed successfully."
fi

# Change to the ib-server directory
cd "$PROJECT_ROOT/ib-server"

# Run the Spring Boot server
echo "Running the spring boot server with the latest changes..."
java -jar target/ib-server-0.0.1-SNAPSHOT.jar