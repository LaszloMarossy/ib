#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Create directories if they do not exist
mkdir -p /Users/laszlo/kafka/data /Users/laszlo/kafka/zookeeper

# Start Zookeeper in the foreground
echo "Starting Zookeeper..."
echo "Press Ctrl+C to stop"
$KAFKA_HOME/bin/zookeeper-server-start.sh /Users/laszlo/kafka/config/zookeeper.properties
