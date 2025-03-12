package com.ibbe.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeWs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka consumer for processing trade messages from the Bitso trades topic.
 * This consumer always starts from the beginning of the topic and processes all messages.
 * It deserializes trade data from Kafka messages and provides it to registered handlers.
 * Enhanced with automatic reconnection capabilities for handling Kafka broker unavailability.
 */
@Component
public class TradesConsumer {
    private static final Logger logger = LoggerFactory.getLogger(TradesConsumer.class);
    private static final String TOPIC = "bitso-trades";
    private static final int PARTITION = 0;
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long RECONNECT_BACKOFF_MS = 1000; // Start with 1 second
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private Thread consumerThread;
    private Consumer<String, String> consumer;
    private long lastOffset = -1;
    
    // Simplified message handler interface
    public interface MessageHandler {
        boolean handleMessage(Trade trade);
    }
    
    private MessageHandler messageHandler;
    
    /**
     * Registers a message handler to receive Trade objects.
     * 
     * @param handler The message handler to register
     */
    public void registerMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }
    
    /**
     * Starts the consumer in a separate thread.
     * If the consumer is already running, this method does nothing.
     * 
     * @return true if the consumer was started, false if it was already running
     */
    public boolean startConsumer() {
        if (running.get()) {
            logger.info("Consumer is already running");
            return false;
        }
        
        consumerThread = new Thread(this::consumeMessages);
        consumerThread.start();
        return true;
    }
    
    /**
     * Stops the consumer if it's running.
     * 
     * @return true if the consumer was stopped, false if it wasn't running
     */
    public boolean stopConsumer() {
        if (!running.get()) {
            logger.info("Consumer is not running");
            return false;
        }
        
        running.set(false);
        
        // Wake up the consumer if it's blocked in a poll
        if (consumer != null) {
            try {
                consumer.wakeup();
            } catch (Exception e) {
                logger.warn("Error waking up consumer: {}", e.getMessage());
            }
        }
        
        try {
            consumerThread.join(5000); // Wait up to 5 seconds for the thread to finish
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for consumer thread to finish", e);
            Thread.currentThread().interrupt();
        }
        return true;
    }
    
    /**
     * Creates Kafka consumer properties with optimized settings for reliability
     * and automatic reconnection.
     * 
     * @return Properties object with Kafka consumer configuration
     */
    private Properties createConsumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "bitso-analytics-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Add reconnection and reliability settings
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, "1000"); // 1 second initial backoff
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, "30000"); // 30 seconds max backoff
        props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, "1000");
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000"); // 30 seconds request timeout
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "45000"); // 45 seconds session timeout
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "10000"); // 10 seconds heartbeat interval
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000"); // 5 minutes max poll interval
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500"); // Max 500 records per poll
        
        return props;
    }
    
    /**
     * Attempts to reconnect to Kafka with exponential backoff
     * 
     * @return true if reconnection was successful, false otherwise
     */
    private boolean reconnect() {
        if (!reconnecting.compareAndSet(false, true)) {
            logger.info("Reconnection already in progress, skipping duplicate attempt");
            return false;
        }
        
        try {
            logger.info("Attempting to reconnect to Kafka...");
            
            long backoff = RECONNECT_BACKOFF_MS;
            boolean connected = false;
            int attempts = reconnectAttempts.incrementAndGet();
            
            if (attempts > MAX_RECONNECT_ATTEMPTS) {
                logger.error("Exceeded maximum reconnection attempts ({})", MAX_RECONNECT_ATTEMPTS);
                return false;
            }
            
            logger.info("Reconnection attempt {} of {}", attempts, MAX_RECONNECT_ATTEMPTS);
            
            // Close the old consumer if it exists
            if (consumer != null) {
                try {
                    consumer.close(Duration.ofSeconds(5));
                } catch (Exception e) {
                    logger.warn("Error closing consumer during reconnect: {}", e.getMessage());
                }
            }
            
            // Create a new consumer
            try {
                consumer = new KafkaConsumer<>(createConsumerProperties());
                
                // Manually assign to the partition
                TopicPartition partition = new TopicPartition(TOPIC, PARTITION);
                consumer.assign(Collections.singleton(partition));
                
                // If we have a last offset, seek to it; otherwise start from the beginning
                if (lastOffset >= 0) {
                    logger.info("Seeking to last processed offset: {}", lastOffset + 1);
                    consumer.seek(partition, lastOffset + 1);
                } else {
                    logger.info("Starting from the beginning of the topic");
                    consumer.seekToBeginning(Collections.singleton(partition));
                }
                
                // Test the connection with a poll
                consumer.poll(Duration.ofMillis(100));
                
                connected = true;
                logger.info("Successfully reconnected to Kafka after {} attempts", attempts);
                reconnectAttempts.set(0); // Reset the counter on successful reconnection
            } catch (Exception e) {
                logger.warn("Reconnection attempt {} failed: {}", attempts, e.getMessage());
                
                // Exponential backoff
                try {
                    logger.info("Waiting {}ms before next reconnection attempt", backoff);
                    Thread.sleep(backoff);
                    backoff = Math.min(backoff * 2, 30000); // Exponential backoff, max 30 seconds
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Reconnection interrupted");
                    return false;
                }
            }
            
            return connected;
        } finally {
            reconnecting.set(false);
        }
    }
    
    /**
     * Main consumer loop that processes messages from Kafka.
     * This method runs in a separate thread.
     */
    private void consumeMessages() {
        running.set(true);
        logger.info("Starting Kafka consumer for topic: {}", TOPIC);
        
        try {
            consumer = new KafkaConsumer<>(createConsumerProperties());
            
            // Manually assign to the partition and seek to the beginning
            TopicPartition partition = new TopicPartition(TOPIC, PARTITION);
            consumer.assign(Collections.singleton(partition));
            consumer.seekToBeginning(Collections.singleton(partition));
            
            logger.info("Consumer assigned to partition: {}-{}", TOPIC, PARTITION);
            logger.info("Starting from the beginning of the topic");
            
            // Statistics counter
            int totalMessages = 0;
            
            // Process messages until stopped
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    
                    if (records.count() > 0) {
                        logger.info("Received {} records", records.count());
                        
                        // going through the messages in a loop
                        for (ConsumerRecord<String, String> record : records) {
                            // Update last offset for recovery purposes
                            lastOffset = record.offset();
                            
                            totalMessages++;
                            Trade trade = unpackTrade(record);
                            
                            // Notify message handler if registered
                            if (messageHandler != null && trade != null) {
                                boolean continueProcessing = messageHandler.handleMessage(trade);
                                if (!continueProcessing) {
                                    logger.info("Message handler requested to stop processing");
                                    running.set(false);
                                    break;
                                }
                            }
                        }
                    }
                } catch (WakeupException e) {
                    // Ignore exception if closing
                    if (!running.get()) {
                        logger.info("Consumer woken up as part of shutdown");
                    } else {
                        logger.warn("Unexpected wakeup exception", e);
                    }
                } catch (AuthorizationException e) {
                    logger.error("Authorization error: {}", e.getMessage());
                    running.set(false);
                } catch (KafkaException e) {
                    logger.error("Kafka error: {}", e.getMessage());
                    
                    // Try to reconnect
                    if (running.get()) {
                        logger.info("Attempting to reconnect due to Kafka error");
                        boolean reconnected = reconnect();
                        if (!reconnected && reconnectAttempts.get() >= MAX_RECONNECT_ATTEMPTS) {
                            logger.error("Failed to reconnect after {} attempts, stopping consumer", MAX_RECONNECT_ATTEMPTS);
                            running.set(false);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Unexpected error in consumer loop: {}", e.getMessage(), e);
                    
                    // Try to reconnect on unexpected errors
                    if (running.get()) {
                        logger.info("Attempting to reconnect due to unexpected error");
                        boolean reconnected = reconnect();
                        if (!reconnected && reconnectAttempts.get() >= MAX_RECONNECT_ATTEMPTS) {
                            logger.error("Failed to reconnect after {} attempts, stopping consumer", MAX_RECONNECT_ATTEMPTS);
                            running.set(false);
                        }
                    }
                }
            }
            
            logger.info("Consumer stopped. Processed {} total messages", totalMessages);
        } catch (Exception e) {
            logger.error("Error initializing Kafka consumer", e);
        } finally {
            running.set(false);
            
            // Close the consumer
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    logger.warn("Error closing consumer: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Unpacks a Kafka record into a Trade object by deserializing the JSON value.
     * 
     * @param record The Kafka record to process
     * @return The deserialized Trade object, or null if processing failed
     */
    private Trade unpackTrade(ConsumerRecord<String, String> record) {
        try {
            String key = record.key();
            String value = record.value();
            
            // Log basic record info
            logger.info("Processing record from {}-{} at offset {} with key: {}", 
                    record.topic(), record.partition(), record.offset(), key);
            
            // Skip test messages
            if ("dummy-key".equals(key) || "testkey".equals(key) || "heartbeat".equals(key)) {
                logger.info("Skipping test/heartbeat message with key: {}", key);
                return null;
            }
            
            // Try to parse as a Trade object
            try {
                // For numeric keys, we expect JSON with trade data
                if (key != null && key.matches("\\d+")) {
                    // Extract the orderbook payload from the JSON
                    if (value.contains("\"obp\"")) {
                        // Log the first part of the JSON for debugging
                        logger.info("Processing JSON message: {}", value.length() > 100 ? value.substring(0, 100) + "..." : value);
                        
                        // Directly deserialize to TradeWs
                        TradeWs tradeWs = objectMapper.readValue(value, TradeWs.class);
                        
                        if (tradeWs != null) {
                            // Log trade details
                            logger.info("Processed Trade ID: {} with price: {} and amount: {}", 
                                    tradeWs.getTid(), 
                                    tradeWs.getPrice(),
                                    tradeWs.getAmount());
                            
                            return tradeWs;
                        } else {
                            logger.warn("Deserialized TradeWs is null");
                        }
                    } else {
                        logger.info("Message does not contain orderbook payload");
                    }
                } else {
                    logger.info("Message with key {} doesn't match expected format", key);
                }
            } catch (Exception e) {
                logger.error("Error processing record value: {}", value, e);
            }
            
        } catch (Exception e) {
            logger.error("Error processing Kafka record", e);
        }
        
        return null;
    }
    
    /**
     * Returns whether the consumer is currently running.
     * 
     * @return true if the consumer is running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Returns whether the consumer is currently connected to Kafka.
     * 
     * @return true if the consumer is connected, false otherwise
     */
    public boolean isConnected() {
        if (consumer == null) {
            return false;
        }
        
        try {
            // Try to poll with a very short timeout to check connection
            consumer.poll(Duration.ofMillis(1));
            return true;
        } catch (Exception e) {
            logger.warn("Connection check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Manually triggers a reconnection attempt.
     * 
     * @return true if reconnection was successful, false otherwise
     */
    public boolean triggerReconnect() {
        if (!running.get()) {
            logger.info("Consumer is not running, cannot reconnect");
            return false;
        }
        
        return reconnect();
    }
}
