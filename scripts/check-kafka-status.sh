#!/bin/bash

# Define Kafka directory - update this to your Kafka installation path
KAFKA_HOME="/Users/laszlo/dev/kafka_2.13-3.7.0"

# Check if Zookeeper is running
echo "Checking Zookeeper status..."
if nc -z localhost 2181 >/dev/null 2>&1; then
  echo "✅ Zookeeper is running on port 2181"
else
  echo "❌ Zookeeper is NOT running on port 2181"
fi

# Check if Kafka is running
echo "Checking Kafka status..."
if nc -z localhost 9092 >/dev/null 2>&1; then
  echo "✅ Kafka is running on port 9092"
else
  echo "❌ Kafka is NOT running on port 9092"
fi

# Check Kafka topics
echo "Checking Kafka topics..."
if nc -z localhost 9092 >/dev/null 2>&1; then
  echo "Available topics:"
  $KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
  
  # Check if bitso-trades topic exists
  if $KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 | grep -q "bitso-trades"; then
    echo "✅ bitso-trades topic exists"
    echo "Topic details:"
    $KAFKA_HOME/bin/kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic bitso-trades
    
    # Get message count by examining log files
    echo "Checking message count..."
    LOG_DIR="/Users/laszlo/kafka/data/bitso-trades-0"
    if [ -d "$LOG_DIR" ]; then
      # Find all log segment files
      LOG_FILES=$(ls -1 $LOG_DIR/*.log 2>/dev/null | sort -V)
      LOG_COUNT=$(echo "$LOG_FILES" | wc -l)
      
      if [ -n "$LOG_FILES" ] && [ "$LOG_COUNT" -gt 0 ]; then
        echo "Found $LOG_COUNT log segment files"
        
        # Get the last log file to find the latest offset
        LATEST_LOG=$(echo "$LOG_FILES" | tail -n 1)
        
        # Get the last offset from the latest log file
        LAST_OFFSET=$($KAFKA_HOME/bin/kafka-dump-log.sh --files "$LATEST_LOG" --print-data-log 2>/dev/null | grep "lastOffset:" | head -n 1 | awk '{print $4}')
        
        # Get the base offset from the filename of the first log file
        FIRST_LOG=$(echo "$LOG_FILES" | head -n 1)
        FIRST_BASE_OFFSET=$(basename "$FIRST_LOG" | cut -d '.' -f 1)
        
        if [ -n "$LAST_OFFSET" ] && [ -n "$FIRST_BASE_OFFSET" ]; then
          # Calculate total messages (last offset + 1)
          TOTAL_MESSAGES=$((LAST_OFFSET + 1))
          echo "✅ Topic contains approximately $TOTAL_MESSAGES messages"
          
          # Get a sample message directly from the log file
          echo "Sample message from topic:"
          SAMPLE_MESSAGE=$($KAFKA_HOME/bin/kafka-dump-log.sh --files "$LATEST_LOG" --print-data-log 2>/dev/null | grep -m 1 "payload:" | sed 's/.*payload: //')
          if [ -n "$SAMPLE_MESSAGE" ]; then
            # Print the first 300 characters of the sample message
            echo "${SAMPLE_MESSAGE}"
          else
            echo "Could not read sample message from log file"
          fi
          
          # Show log segment information
          # echo "Log segment information:"
          # for LOG_FILE in $LOG_FILES; do
          #   BASE_OFFSET=$(basename "$LOG_FILE" | cut -d '.' -f 1)
          #   FILE_SIZE=$(du -h "$LOG_FILE" | awk '{print $1}')
          #   echo "  - Segment $BASE_OFFSET: $FILE_SIZE"
          # done
        else
          echo "❌ Could not determine message count"
        fi
      else
        echo "❌ No log files found for topic bitso-trades"
      fi
    else
      echo "❌ No log directory found for topic bitso-trades"
    fi
  else
    echo "❌ bitso-trades topic does NOT exist"
  fi
else
  echo "Cannot check topics because Kafka is not running"
fi

# Check Kafka data directory
# echo "Checking Kafka data directory..."
# if [ -d /Users/laszlo/kafka/data ]; then
#   echo "✅ Kafka data directory exists at /Users/laszlo/kafka/data"
#   echo "Directory contents:"
#   ls -la /Users/laszlo/kafka/data
# else
#   echo "❌ Kafka data directory does NOT exist at /Users/laszlo/kafka/data"
# fi
