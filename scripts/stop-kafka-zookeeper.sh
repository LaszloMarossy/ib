#!/bin/bash

# Define Kafka directory
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

echo "Stopping Kafka..."
$KAFKA_HOME/bin/kafka-server-stop.sh
sleep 3

echo "Stopping Zookeeper..."
$KAFKA_HOME/bin/zookeeper-server-stop.sh
sleep 3

echo "Killing any remaining Kafka processes..."
pkill -f kafka.Kafka || echo "No Kafka processes found"
sleep 1

echo "Killing any remaining Zookeeper processes..."
pkill -f org.apache.zookeeper || echo "No Zookeeper processes found"
sleep 1

echo "Checking for remaining processes..."
ps aux | grep -E 'kafka|zookeeper' | grep -v grep

echo "Done! All Kafka and Zookeeper processes should be stopped." 