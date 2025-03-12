#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Create directories if they do not exist
mkdir -p /Users/laszlo/kafka/data

# Start Kafka in the foreground
echo "Starting Kafka..."
echo "Press Ctrl+C to stop"
echo "Make sure Zookeeper is already running!"
$KAFKA_HOME/bin/kafka-server-start.sh /Users/laszlo/kafka/config/server.properties
