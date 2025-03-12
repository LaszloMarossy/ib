#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Check if Kafka broker is running
echo "Checking if Kafka broker is running..."
KAFKA_PID=$(pgrep -f "kafka.Kafka")

if [ -n "$KAFKA_PID" ]; then
    echo "Stopping Kafka broker (PID: $KAFKA_PID)..."
    kill $KAFKA_PID
    
    # Wait for Kafka to stop
    echo "Waiting for Kafka broker to stop..."
    for i in {1..15}; do
        if ! ps -p $KAFKA_PID > /dev/null; then
            break
        fi
        sleep 1
    done
    
    # Force kill if still running
    if ps -p $KAFKA_PID > /dev/null; then
        echo "Kafka broker still running. Force killing..."
        kill -9 $KAFKA_PID
        sleep 2
    fi
fi

# Check if Zookeeper is running
echo "Checking if Zookeeper is running..."
if ! nc -z localhost 2181; then
    echo "Error: Zookeeper is not running. Please start Zookeeper first."
    exit 1
fi

# Create a temporary file for ZooKeeper commands
TEMP_ZK_CMDS=$(mktemp)
cat > $TEMP_ZK_CMDS << EOF
deleteall /brokers/ids/0
quit
EOF

# Execute ZooKeeper commands
$KAFKA_HOME/bin/zookeeper-shell.sh localhost:2181 < $TEMP_ZK_CMDS

# Clean up
rm $TEMP_ZK_CMDS

echo "Kafka broker registration cleaned up. You can now start Kafka broker again."
echo "To start Kafka broker, run: ./start-kafka-server.sh"
