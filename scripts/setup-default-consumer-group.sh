#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Define consumer group name
CONSUMER_GROUP="bitso-trades-monitor"

echo "Setting up default consumer group: $CONSUMER_GROUP"

# First, make sure the topic exists
echo "Checking if topic exists..."
TOPIC_EXISTS=$($KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep "^bitso-trades$")
if [ -z "$TOPIC_EXISTS" ]; then
    echo "Topic 'bitso-trades' does not exist. Please run create-topic.sh first."
    exit 1
fi

# Create a temporary consumer properties file
TEMP_PROPS=$(mktemp)
cat > $TEMP_PROPS << EOF
bootstrap.servers=localhost:9092
group.id=
enable.auto.commit=true
auto.commit.interval.ms=1000
session.timeout.ms=30000
key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
auto.offset.reset=earliest
EOF

# Start a consumer to create the consumer group and commit offsets
echo "Creating consumer group and committing initial offsets..."
timeout 5s $KAFKA_HOME/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic bitso-trades --consumer.config $TEMP_PROPS || true

# Clean up the temporary file
rm $TEMP_PROPS

# Check if the consumer group was created
echo "Checking if consumer group was created..."
CONSUMER_GROUPS=$($KAFKA_HOME/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list)
if echo "$CONSUMER_GROUPS" | grep -q "$CONSUMER_GROUP"; then
    echo "✅ Consumer group $CONSUMER_GROUP created successfully"
    
    # Show consumer group details
    echo "Consumer group details:"
    $KAFKA_HOME/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group $CONSUMER_GROUP
else
    echo "❌ Failed to create consumer group $CONSUMER_GROUP"
    exit 1
fi

echo "Consumer group setup complete"
