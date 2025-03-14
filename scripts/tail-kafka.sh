#!/bin/bash

# Kafka Monitor Script
# Usage: ./scripts/tail-kafka.sh [options]

# Default values
TOPIC="bitso-trades"
PARTITION=0
BOOTSTRAP_SERVERS="localhost:9092"
WATCH_MODE=false
FROM_BEGINNING=true
MAX_DISPLAY=30
FILTER_TYPE=""
FILTER_KEY=""
FILTER_VALUE=""

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -t|--topic)
      TOPIC="$2"
      shift 2
      ;;
    -p|--partition)
      PARTITION="$2"
      shift 2
      ;;
    -b|--bootstrap-servers)
      BOOTSTRAP_SERVERS="$2"
      shift 2
      ;;
    -w|--watch)
      WATCH_MODE=true
      shift
      ;;
    -l|--latest)
      FROM_BEGINNING=false
      shift
      ;;
    -m|--max-display)
      MAX_DISPLAY="$2"
      shift 2
      ;;
    --filter-type)
      FILTER_TYPE="$2"
      shift 2
      ;;
    --filter-key)
      FILTER_KEY="$2"
      shift 2
      ;;
    --filter-value)
      FILTER_VALUE="$2"
      shift 2
      ;;
    -h|--help)
      echo "Kafka Monitor - A tool to monitor Kafka topics"
      echo ""
      echo "Usage: ./scripts/tail-kafka.sh [options]"
      echo ""
      echo "Options:"
      echo "  -t, --topic TOPIC            Kafka topic to monitor (default: bitso-trades)"
      echo "  -p, --partition PARTITION    Partition to monitor (default: 0)"
      echo "  -b, --bootstrap-servers SERVERS  Kafka bootstrap servers (default: localhost:9092)"
      echo "  -w, --watch                  Watch mode - continuously monitor for new messages"
      echo "  -l, --latest                 Start from the latest messages instead of beginning"
      echo "  -m, --max-display COUNT      Maximum number of messages to display (default: 30)"
      echo "  --filter-type TYPE           Filter messages by key type (numeric, text, binary, null)"
      echo "  --filter-key PATTERN         Filter messages by key containing pattern"
      echo "  --filter-value PATTERN       Filter messages by value containing pattern"
      echo "  -h, --help                   Show this help message"
      echo ""
      echo "Examples:"
      echo "  ./scripts/tail-kafka.sh --topic my-topic --watch"
      echo "  ./scripts/tail-kafka.sh --latest --filter-type text"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Build command
CMD="java -jar /Users/laszlo/dev/code/ibtrader/kafka-test/target/kafka-test-1.0-SNAPSHOT-jar-with-dependencies.jar"

# Add options
CMD="$CMD --topic $TOPIC --partition $PARTITION --bootstrap-servers $BOOTSTRAP_SERVERS"

if [ "$WATCH_MODE" = true ]; then
  CMD="$CMD --watch"
fi

if [ "$FROM_BEGINNING" = false ]; then
  CMD="$CMD --latest"
fi

CMD="$CMD --max-display $MAX_DISPLAY"

if [ -n "$FILTER_TYPE" ]; then
  CMD="$CMD --filter-type $FILTER_TYPE"
fi

if [ -n "$FILTER_KEY" ]; then
  CMD="$CMD --filter-key $FILTER_KEY"
fi

if [ -n "$FILTER_VALUE" ]; then
  CMD="$CMD --filter-value $FILTER_VALUE"
fi

# Run the command
echo "Running: $CMD"
$CMD 