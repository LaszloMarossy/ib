# Kafka Scripts

This directory contains scripts for managing Kafka and Zookeeper for the IB project.

## Setup

1. **Migrate data from old location (if needed):**
   ```
   ./scripts/migrate-kafka-data.sh
   ```

2. **Start Zookeeper (in a separate terminal):**
   ```
   ./scripts/start-zookeeper.sh
   ```

3. **Start Kafka (in a separate terminal):**
   ```
   ./scripts/start-kafka-server.sh
   ```

4. **Create and configure the topic:**
   ```
   ./scripts/create-topic.sh
   ```

5. **Check Kafka status:**
   ```
   ./scripts/check-kafka-status.sh
   ```

## Stopping Kafka

To stop Kafka and Zookeeper, press Ctrl+C in each terminal window where they are running.

## Maintenance Scripts

- **clean-kafka-zk.sh**: Cleans up Kafka broker registration in Zookeeper
- **check-kafka-status.sh**: Interactive script to check the status of Kafka and Zookeeper
- **check-port-8080.sh**: Checks if port 8080 is in use and optionally kills the process
- **setup-default-consumer-group.sh**: Sets up a default consumer group for monitoring
- **cron-setup-kafka-monitor.sh**: Sets up a cron job to monitor Kafka status daily
- **cron-kafka-status.sh**: Script used by cron job to check Kafka status and log results
- **tail-kafka.sh**: Monitors and displays Kafka topic messages (run with --watch --latest)

## Data Location

Kafka data is stored in `/Users/laszlo/kafka/data`
Zookeeper data is stored in `/Users/laszlo/kafka/zookeeper`
Configuration files are stored in `/Users/laszlo/kafka/config`

These locations are permanent and will not be cleaned up by the system, unlike the previous `/tmp` locations.

## Kafka Installation Path

All scripts assume Kafka is installed at `/Users/laszlo/dev/kafka_2.13-3.7.0`. If your Kafka installation is in a different location, you will need to update the `KAFKA_HOME` variable in each script.

## restoring kafka data from backup directory into the topic:

```
cd /Users/laszlo/dev/code/ib && rm -rf /Users/laszlo/kafka/data/bitso-trades-0/* && cp -R /Users/laszlo/kafka/data_backup/bitso-trades-0/* /Users/laszlo/kafka/data/bitso-trades-0/ && echo "Data restored from backup"
```

## getting a particular kafka record in the command line:

```
 ~/dev/kafka_2.13-3.7.0/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic bitso-trades --partition 0 --offset 28143 --max-messages 1
```