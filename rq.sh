#!/bin/bash

# rq.sh - Script to run QuickReplayWindow with optimized memory settings

# Navigate to project directory
cd "$(dirname "$0")"

# Clean and build the project (comment the next line if already built)
# mvn clean install -DskipTests

# Set memory options for the JVM
# -Xms512m: Minimum heap size of 512MB
# -Xmx2g: Maximum heap size of 2GB
# -XX:+UseG1GC: Use the G1 garbage collector (better for large heaps)
# -XX:+ExplicitGCInvokesConcurrent: Make System.gc() calls concurrent
# -XX:+AlwaysPreTouch: Pre-touch memory pages (better startup performance)

JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+ExplicitGCInvokesConcurrent -XX:+AlwaysPreTouch -XX:MaxGCPauseMillis=50"

# Run QuickReplayWindow with the memory settings
java $JAVA_OPTS -cp ib-client/target/ib-client-0.0.1-SNAPSHOT.jar:ib-client/target/dependency/* com.ibbe.fx.QuickReplayWindow

# If the above doesn't work, try with the full classpath including all dependencies
# mvn exec:java -Dexec.mainClass="com.ibbe.fx.QuickReplayWindow" -Dexec.args="" -Dexec.classpathScope=runtime \
#   -Dexec.vmArgs="$JAVA_OPTS"

# Exit with the same code as the Java process
exit $? 