#!/bin/bash

# Define Kafka directory
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

echo "This script will attempt to fix the NodeExistsException by removing only the broker registration"
echo "without deleting your topics and messages."
echo "Press Ctrl+C to cancel or Enter to continue..."
read

# First make sure Kafka and Zookeeper are stopped
echo "Making sure Kafka and Zookeeper are stopped..."
./stop-kafka-zookeeper.sh > /dev/null

# Start Zookeeper temporarily to clean up broker registrations
echo "Starting Zookeeper temporarily..."
$KAFKA_HOME/bin/zookeeper-server-start.sh -daemon /Users/laszlo/kafka/config/zookeeper.properties
sleep 5

# Use Zookeeper CLI to delete the broker registration
echo "Removing broker registration..."
$KAFKA_HOME/bin/zookeeper-shell.sh localhost:2181 <<EOF
rmr /brokers/ids/0
quit
EOF

# Stop Zookeeper
echo "Stopping Zookeeper..."
$KAFKA_HOME/bin/zookeeper-server-stop.sh
sleep 3

echo "Done! The broker registration has been removed."
echo "You can now restart Zookeeper and Kafka with:"
echo "  1. ./start-zookeeper.sh"
echo "  2. ./start-kafka.sh" 