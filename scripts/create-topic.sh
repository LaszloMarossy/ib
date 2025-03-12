#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Create the bitso-trades topic if it does not exist
echo "Creating bitso-trades topic if it does not exist..."
$KAFKA_HOME/bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1 --topic bitso-trades --if-not-exists

# Configure the topic for long-term retention
echo "Configuring topic for long-term retention..."
$KAFKA_HOME/bin/kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name bitso-trades --alter --add-config retention.bytes=5368709120,segment.bytes=10485760,retention.ms=-1

# List the topic to confirm it exists
echo "Listing topics to confirm creation:"
$KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Show topic details
echo "Topic details:"
$KAFKA_HOME/bin/kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic bitso-trades
