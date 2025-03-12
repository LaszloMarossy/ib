#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Topic name
TOPIC_NAME="bitso-trades"

# Log file
LOG_FILE="/Users/laszlo/kafka/kafka-status.log"

# Function to check Kafka broker status
check_broker_status() {
    if $KAFKA_HOME/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 &>/dev/null; then
        echo "[$(date)] ✅ Kafka broker is running" | tee -a $LOG_FILE
        return 0
    else
        echo "[$(date)] ❌ Kafka broker is not responding" | tee -a $LOG_FILE
        return 1
    fi
}

# Function to check Zookeeper status
check_zookeeper_status() {
    if echo ruok | nc localhost 2181 | grep -q imok; then
        echo "[$(date)] ✅ Zookeeper is running" | tee -a $LOG_FILE
        return 0
    else
        echo "[$(date)] ❌ Zookeeper is not responding" | tee -a $LOG_FILE
        return 1
    fi
}

# Function to check topic existence
check_topic_exists() {
    if $KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic $TOPIC_NAME &>/dev/null; then
        echo "[$(date)] ✅ Topic $TOPIC_NAME exists" | tee -a $LOG_FILE
        return 0
    else
        echo "[$(date)] ❌ Topic $TOPIC_NAME does not exist" | tee -a $LOG_FILE
        return 1
    fi
}

# Function to check topic configuration
check_topic_config() {
    local config=$($KAFKA_HOME/bin/kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name $TOPIC_NAME --describe 2>/dev/null)
    
    echo "[$(date)] Topic configuration:" | tee -a $LOG_FILE
    echo "$config" | tee -a $LOG_FILE
    
    if echo "$config" | grep -q "retention.bytes=5368709120"; then
        echo "[$(date)] ✅ Retention size is correctly set to 5GB" | tee -a $LOG_FILE
    else
        echo "[$(date)] ⚠️ Retention size is not set to 5GB" | tee -a $LOG_FILE
    fi
    
    if echo "$config" | grep -q "segment.bytes=10485760"; then
        echo "[$(date)] ✅ Segment size is correctly set to 10MB" | tee -a $LOG_FILE
    else
        echo "[$(date)] ⚠️ Segment size is not set to 10MB" | tee -a $LOG_FILE
    fi
}

# Function to check topic data
check_topic_data() {
    local size=$(du -sh /Users/laszlo/kafka/data/$TOPIC_NAME-* 2>/dev/null | awk '{print $1}')
    
    if [ -n "$size" ]; then
        echo "[$(date)] ✅ Topic data size: $size" | tee -a $LOG_FILE
    else
        echo "[$(date)] ⚠️ No data found for topic $TOPIC_NAME" | tee -a $LOG_FILE
    fi
}

# Main function
main() {
    echo "=== Kafka Status Check ===" | tee -a $LOG_FILE
    echo "[$(date)] Starting status check" | tee -a $LOG_FILE
    
    check_zookeeper_status
    if [ $? -ne 0 ]; then
        echo "[$(date)] ⚠️ Zookeeper is not running. Cannot proceed with other checks." | tee -a $LOG_FILE
        return 1
    fi
    
    check_broker_status
    if [ $? -ne 0 ]; then
        echo "[$(date)] ⚠️ Kafka broker is not running. Cannot proceed with other checks." | tee -a $LOG_FILE
        return 1
    fi
    
    check_topic_exists
    if [ $? -eq 0 ]; then
        check_topic_config
        check_topic_data
    fi
    
    echo "[$(date)] Status check completed" | tee -a $LOG_FILE
    echo "===========================" | tee -a $LOG_FILE
}

# Run the main function
main
