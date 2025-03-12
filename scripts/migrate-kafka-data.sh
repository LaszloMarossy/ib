#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Check if Kafka and Zookeeper are running
if nc -z localhost 9092 >/dev/null 2>&1 || nc -z localhost 2181 >/dev/null 2>&1; then
  echo "❌ ERROR: Kafka or Zookeeper is still running. Please stop them before migrating data."
  echo "Run the following commands in separate terminals and press Ctrl+C to stop them:"
  echo "  ./stop-kafka.sh"
  exit 1
fi

# Create directories if they do not exist
mkdir -p /Users/laszlo/kafka/data /Users/laszlo/kafka/zookeeper

# Check if old Kafka data exists
if [ -d /tmp/kafka-logs ]; then
  echo "Found old Kafka data in /tmp/kafka-logs"
  echo "Copying data to /Users/laszlo/kafka/data..."
  cp -R /tmp/kafka-logs/* /Users/laszlo/kafka/data/
  echo "✅ Data copied successfully"
else
  echo "❌ No Kafka data found in /tmp/kafka-logs"
fi

# Check if old Zookeeper data exists
if [ -d /tmp/zookeeper ]; then
  echo "Found old Zookeeper data in /tmp/zookeeper"
  echo "Copying data to /Users/laszlo/kafka/zookeeper..."
  cp -R /tmp/zookeeper/* /Users/laszlo/kafka/zookeeper/
  echo "✅ Data copied successfully"
else
  echo "❌ No Zookeeper data found in /tmp/zookeeper"
fi

echo "Migration complete. You can now start Kafka and Zookeeper with the new configuration."
echo "Run the following commands in separate terminals:"
echo "  ./start-zookeeper.sh"
echo "  ./start-kafka-server.sh"
