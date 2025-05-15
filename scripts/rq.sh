#!/bin/bash
cd "/Users/laszlo/dev/code/ibtrader/ib-client"

# Set memory options for the JVM to prevent OutOfMemoryError
# -Xms512m: Minimum heap size of 512MB
# -Xmx2g: Maximum heap size of 2GB
# -XX:+UseG1GC: Use the G1 garbage collector (better for large heaps)
# -XX:+ExplicitGCInvokesConcurrent: Make System.gc() calls concurrent
# -XX:MaxMetaspaceSize=256m: Limit metaspace to prevent memory leaks
JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+ExplicitGCInvokesConcurrent -XX:MaxMetaspaceSize=256m -XX:MaxGCPauseMillis=50"

# Run the QuickReplayWindow application
echo "Running the QuickReplayWindow application with increased memory settings..."
java $JAVA_OPTS --module-path /Users/laszlo/dev/javafx-sdk-21.0.1/lib --add-modules javafx.controls,javafx.fxml -jar target/ibbe-quick-replay-jar-with-dependencies.jar 