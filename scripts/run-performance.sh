#!/bin/bash

# Define the JavaFX path directly to match what's used in rp.sh
JAVAFX_PATH="/Users/laszlo/dev/javafx-sdk-21.0.1"

# Check if JavaFX modules directory exists
if [ ! -d "$JAVAFX_PATH/lib" ]; then
  echo "Error: JavaFX modules not found at $JAVAFX_PATH/lib"
  echo "Please install JavaFX SDK from: https://gluonhq.com/products/javafx/"
  echo "After downloading, extract it to: $JAVAFX_PATH"
  exit 1
fi

# Run the Performance Window application with optimized memory settings for large datasets
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
  -jar ../ib-client/target/ibbe-performance-jar-with-dependencies.jar 