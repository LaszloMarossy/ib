#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Create directories if they do not exist
mkdir -p /Users/laszlo/kafka/data /Users/laszlo/kafka/zookeeper

echo "Starting Zookeeper in the background..."
$KAFKA_HOME/bin/zookeeper-server-start.sh -daemon /Users/laszlo/kafka/config/zookeeper.properties
sleep 5  # Wait for Zookeeper to start

echo "Starting Kafka in the background..."
$KAFKA_HOME/bin/kafka-server-start.sh -daemon /Users/laszlo/kafka/config/server.properties
sleep 5  # Wait for Kafka to start

echo "Checking if Kafka is running..."
$KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

echo "Kafka environment started. Use the following commands to check status:"
echo "  ps aux | grep zookeeper"
echo "  ps aux | grep kafka"
echo "To stop the services, use:"
echo "  $KAFKA_HOME/bin/kafka-server-stop.sh"
echo "  $KAFKA_HOME/bin/zookeeper-server-stop.sh" 