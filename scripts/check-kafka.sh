#!/bin/bash

# Define Kafka directory
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

echo "Checking Zookeeper status..."
echo "Running: nc -z localhost 2181"
nc -z localhost 2181
if [ $? -eq 0 ]; then
    echo "✅ Zookeeper is running on port 2181"
else
    echo "❌ Zookeeper is NOT running on port 2181"
fi

echo ""
echo "Checking Kafka status..."
echo "Running: nc -z localhost 9092"
nc -z localhost 9092
if [ $? -eq 0 ]; then
    echo "✅ Kafka is running on port 9092"
else
    echo "❌ Kafka is NOT running on port 9092"
fi

echo ""
echo "Trying to list Kafka topics..."
$KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
if [ $? -eq 0 ]; then
    echo "✅ Successfully connected to Kafka"
else
    echo "❌ Failed to connect to Kafka"
fi

echo ""
echo "Checking Kafka processes..."
ps aux | grep -i 'kafka\.Kafka' | grep -v grep
if [ $? -eq 0 ]; then
    echo "✅ Kafka process is running"
else
    echo "❌ No Kafka process found"
fi

echo ""
echo "Checking Zookeeper processes..."
ps aux | grep -i 'org\.apache\.zookeeper' | grep -v grep
if [ $? -eq 0 ]; then
    echo "✅ Zookeeper process is running"
else
    echo "❌ No Zookeeper process found"
fi 