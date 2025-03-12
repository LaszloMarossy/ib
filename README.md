# IB Project

This is a multi-module Maven project for the IB application.

## Project Structure

The project is organized into the following modules:

- **ib-common**: Contains shared classes used by both client and server modules
- **ib-client**: Contains the JavaFX client application
- **ib-server**: Contains the Spring Boot server application

## Building the Project

To build the entire project:

```bash
mvn clean install
#OR
cd ib-server && mvn clean package | cat
cd ib-client && mvn clean package | cat
#OR
#For the client, if we run with mvn 'javafx:run...' afterwards:
mvn clean compile -U

```

## Running the Applications

### Running the Server

```bash
cd ib-server
mvn spring-boot:run

# OR 

./scripts/server.sh
```




### Running the Client

Using Maven:
```bash
cd ib-client
mvn javafx:run -Djavafx.mainClass=com.ibbe.fx.TradingWindow
```

Using the scripts:
```bash
# Build the client
./scripts/rb.sh

# Run the Trading Window
./scripts/rt.sh

# Run the Chart Window
./scripts/rc.sh

# Run the Performance Window
./scripts/rp.sh
```

Alternatively, you can run the packaged client applications:

```bash
java --module-path /Users/laszlo/dev/javafx-sdk-21.0.1/lib --add-modules javafx.controls,javafx.fxml -jar target/ibbe-trading-jar-with-dependencies.jar
```

or

```bash
java --module-path /Users/laszlo/dev/javafx-sdk-21.0.1/lib --add-modules javafx.controls,javafx.fxml -jar target/ibbe-chart-jar-with-dependencies.jar
```

## Module Details

### ib-common

Contains entity classes and other shared components used by both the client and server.

### ib-client

Contains the JavaFX client application with trading and chart windows.

### ib-server

Contains the Spring Boot server application that provides WebSocket endpoints and other backend services.

### building with changes taking effect steps - back-end changes in the server module

```bash
cd /Users/laszlo/dev/code/ib/ib-server 
mvn clean package -U | cat
# verify byte code for changes, if necessary:
javap -c -p target/classes/com/ibbe/executor/TradingExecutor.class | grep -A 20 refreshDisplayWithNewTrade | cat
java -jar target/ib-server-0.0.1-SNAPSHOT.jar
```

### kafka configuration

```bash
# create a Kafka configuration file to set up proper data retention and session timeout:
mkdir -p /tmp/kafka-config
# a script to start Kafka with our custom configuration:
chmod +x /Users/laszlo/dev/code/ib/start-kafka.sh

# kafka config in /tmp/kafka-config/server.properties
# create-persistent-topic.sh to create durable topic
# monitor-kafka-health.sh to monitor the Kafka topic during periods of inactivity
```

```java properties:/tmp/kafka-config/server.properties
// ... existing code ...

# The minimum age of a log file to be eligible for deletion due to age
# Setting to a very high value (100 years) to effectively disable time-based deletion
log.retention.hours=876000

# A size-based retention policy for logs - this will create a circular buffer effect
# 5GB retention limit as requested
log.retention.bytes=5368709120

# The maximum size of a log segment file. When this size is reached a new log segment will be created
# 10MB segments as requested for more granular retention
log.segment.bytes=10485760

# The interval at which log segments are checked to see if they can be deleted
# Check every day to reduce overhead
log.retention.check.interval.ms=86400000


# Prevent data loss during long periods of inactivity
offsets.retention.minutes=525600  # 1 year
offsets.retention.check.interval.ms=86400000  # 1 day
offsets.commit.timeout.ms=10000
offsets.topic.replication.factor=1
offsets.topic.num.partitions=1
offsets.topic.segment.bytes=104857600
```

```java properties:/tmp/kafka-config/zookeeper.properties
# Set extremely high session timeout limits (1 day to 30 days)
minSessionTimeout=86400000
maxSessionTimeout=2592000000

# Autopurge settings - keep more snapshots and purge less frequently
autopurge.snapRetainCount=10
autopurge.purgeInterval=24

# Increase tick time for better handling of long periods of inactivity
tickTime=6000

# The directory where the snapshot is stored
dataDir=/tmp/zookeeper

```

### shell script:/Users/laszlo/dev/code/ib/create-persistent-topic.sh

### shell script:/Users/laszlo/dev/code/ib/setup-default-consumer-group.sh

### a script to monitor the Kafka topic during periods of inactivity: /Users/laszlo/dev/code/ib/monitor-kafka-health.sh

### a cron job script to periodically check Kafka health during long periods of inactivity: /Users/laszlo/dev/code/ib/setup-kafka-monitor-cron.sh

## Kafka Configuration for Long Periods of Inactivity

I've updated the Kafka configuration to handle long periods of inactivity (days or weeks) and ensure your topic data is preserved with a 5GB retention limit and 10MB segments as requested. Here's a summary of the changes:

### 1. Zookeeper Configuration (`/tmp/kafka-config/zookeeper.properties`)

- **Increased Session Timeouts**: Set to 1 day minimum (86,400,000 ms) and 30 days maximum (2,592,000,000 ms)
- **Increased Tick Time**: Set to 6,000 ms (6 seconds) for better handling of inactivity
- **Improved Autopurge Settings**: Retain 10 snapshots and purge every 24 hours

### 2. Kafka Broker Configuration (`/tmp/kafka-config/server.properties`)

- **Extended Time-Based Retention**: Set to 100 years (876,000 hours) to effectively disable time-based deletion
- **Increased Size-Based Retention**: Set to 5GB (5,368,709,120 bytes) as requested
- **Reduced Segment Size**: Set to 10MB (10,485,760 bytes) as requested
- **Extended Offset Retention**: Set to 1 year (525,600 minutes) to preserve consumer offsets during inactivity
- **Improved Shutdown Settings**: More retries and longer backoff for clean shutdowns

### 3. Topic Configuration (in `create-persistent-topic.sh`)

- **Size-Based Retention**: Set to 5GB (5,368,709,120 bytes)
- **Segment Size**: Set to 10MB (10,485,760 bytes)
- **Disabled Time-Based Retention**: Set to -1 (never expire based on time)
- **Added Cleanup Settings**: Improved cleanup ratio and retention settings

### 4. Monitoring Tools

1. **Status Check Script** (`check-kafka-status.sh`):
   - Interactive script for checking Kafka and Zookeeper status
   - Verifies topic existence and configuration
   - Monitors topic data size and message count

2. **Automated Monitoring** (`cron-kafka-status.sh`):
   - Checks Zookeeper and Kafka broker status
   - Verifies topic existence and configuration
   - Monitors topic data size
   - Logs all checks to `/Users/laszlo/kafka/kafka-status.log`

3. **Cron Job Setup** (`cron-setup-kafka-monitor.sh`):
   - Sets up a daily status check at 8 AM
   - Helps detect issues during periods of inactivity

## How to Use This Configuration

1. **Start Zookeeper**:

   ```bash
   cd /Users/laszlo/dev/code/ib
   ./scripts/start-zookeeper.sh
   ```

2. **Start Kafka Broker** (in another terminal):

   ```bash
   cd /Users/laszlo/dev/code/ib
   ./scripts/start-kafka-server.sh
   ```

3. **Create/Configure the Topic**:

   ```bash
   cd /Users/laszlo/dev/code/ib
   ./scripts/create-topic.sh
   ```

4. **Set Up Health Monitoring** (optional):

   ```bash
   cd /Users/laszlo/dev/code/ib
   ./scripts/cron-setup-kafka-monitor.sh
   ```

5. **Check Kafka Status** anytime:

   ```bash
   cd /Users/laszlo/dev/code/ib
   ./scripts/check-kafka-status.sh
   ```

## What This Configuration Ensures

1. **Data Persistence**: Your Kafka topic will maintain data even after long periods of inactivity (days or weeks).

2. **Circular Buffer**: The topic will act as a 5GB circular buffer, keeping the most recent data and automatically removing the oldest data when the limit is reached.

3. **Granular Retention**: With 10MB segments, Kafka can more precisely manage which data to keep and which to remove.

4. **No Time-Based Deletion**: Data will never be deleted based on time, only based on size.

5. **Session Resilience**: Extended session timeouts ensure connections are maintained even during long periods of inactivity.

This configuration should address your specific needs for maintaining the Kafka topic during long periods of inactivity while ensuring it operates as a circular buffer with a 5GB retention limit and 10MB segments.

# recreating kafka after losing configuration, in a permanenet place

```
mkdir -p ~/kafka/config ~/kafka/data ~/kafka/zookeeper

# ~/kafka/config/zookeeper.properties

# create the Kafka server configuration file in the permanent location:
# ~/kafka/config/server.properties 

# copy your existing Kafka data to the new permanent location:
cp -R /tmp/kafka-logs/* ~/kafka/data/

#  create a script to start Zookeeper and Kafka with the new configuration: ~/start-kafka.sh

# script to stop Kafka and Zookeeper: ~/stop-kafka.sh
```
