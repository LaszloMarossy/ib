#!/bin/bash

echo "WARNING: This script will delete all Kafka and Zookeeper data!"
echo "This will remove all topics, messages, and broker registrations."
echo "Press Ctrl+C to cancel or Enter to continue..."
read

# Define data directories
KAFKA_DATA_DIR="/Users/laszlo/kafka/data"
ZOOKEEPER_DATA_DIR="/Users/laszlo/kafka/zookeeper"

# First make sure Kafka and Zookeeper are stopped
echo "Making sure Kafka and Zookeeper are stopped..."
./stop-kafka-zookeeper.sh > /dev/null

# Remove Kafka data
echo "Removing Kafka data from $KAFKA_DATA_DIR..."
rm -rf "$KAFKA_DATA_DIR"/*
echo "✅ Kafka data removed"

# Remove Zookeeper data
echo "Removing Zookeeper data from $ZOOKEEPER_DATA_DIR..."
rm -rf "$ZOOKEEPER_DATA_DIR"/*
echo "✅ Zookeeper data removed"

# Recreate directories
echo "Recreating data directories..."
mkdir -p "$KAFKA_DATA_DIR" "$ZOOKEEPER_DATA_DIR"
echo "✅ Data directories recreated"

echo "Done! All Kafka and Zookeeper data has been cleaned."
echo "You can now restart Zookeeper and Kafka with:"
echo "  1. ./start-zookeeper.sh"
echo "  2. ./start-kafka.sh" 