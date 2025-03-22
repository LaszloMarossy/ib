#!/bin/bash
# Script to run the performance window
# Usage: ./scripts/rp.sh

# Change to the project root directory
cd "$(dirname "$0")/.."
PROJECT_ROOT=$(pwd)

# Define the JavaFX path
JAVAFX_PATH="/Users/laszlo/dev/javafx-sdk-21.0.1"

# Check if the JavaFX modules directory exists
if [ ! -d "$JAVAFX_PATH/lib" ]; then
  echo "Error: JavaFX modules not found at $JAVAFX_PATH/lib"
  echo "Please install JavaFX SDK from: https://gluonhq.com/products/javafx/"
  echo "After downloading, extract it to: $JAVAFX_PATH"
  exit 1
fi

# Check if the JAR file exists and when it was last modified
JAR_FILE="$PROJECT_ROOT/ib-client/target/ibbe-performance-jar-with-dependencies.jar"
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

# Run the Performance Window application with optimized memory settings for large datasets
echo "Running the performance window with optimized settings..."
java \
  -Xmx4096m \
  -Xms2048m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+UseStringDeduplication \
  -XX:+DisableExplicitGC \
  -XX:G1HeapRegionSize=4m \
  -XX:InitiatingHeapOccupancyPercent=35 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=./java_heap_dump.hprof \
  -Xlog:gc*:file=gc.log:time,uptime,level,tags \
  --module-path "$JAVAFX_PATH/lib" \
  --add-modules javafx.controls,javafx.fxml \
  -jar "$JAR_FILE" 