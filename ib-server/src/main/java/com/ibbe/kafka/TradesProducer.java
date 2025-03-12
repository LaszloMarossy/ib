package com.ibbe.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.BitsoDataAggregator;
import com.ibbe.entity.TradeWs;
import com.ibbe.util.PropertiesUtil;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Component responsible for sending trades to Kafka.
 * Enhanced with automatic reconnection capabilities for handling Kafka broker unavailability.
 */
@Component
public class TradesProducer {
  private static final Logger LOGGER = LoggerFactory.getLogger(TradesProducer.class);
  private static final String kafkaUrl = PropertiesUtil.getProperty("kafka.url");
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final int MAX_RECONNECT_ATTEMPTS = 10;
  private static final long RECONNECT_BACKOFF_MS = 1000; // Start with 1 second
  
  // Thread pool for asynchronous operations
  private final ExecutorService executorService;
  
  // Kafka producer
  private Producer<String, String> producer;
  
  // Reconnection tracking
  private final AtomicBoolean reconnecting = new AtomicBoolean(false);
  private final AtomicInteger failedMessages = new AtomicInteger(0);
  private static final int MAX_FAILED_MESSAGES = 5;
  
  // Kafka properties
  private final Properties props;
  
  @Autowired
  private BitsoDataAggregator bitsoDataAggregator;

  /**
   * Constructor initializes the Kafka producer and thread pool
   */
  public TradesProducer() {
    props = kafkaProps(kafkaUrl);
    initializeProducer();
    executorService = Executors.newFixedThreadPool(3);
  }
  
  /**
   * Initialize the Kafka producer with retry logic
   */
  private synchronized void initializeProducer() {
    if (producer != null) {
      try {
        producer.close();
      } catch (Exception e) {
        LOGGER.warn("Error closing existing producer: {}", e.getMessage());
      }
    }
    
    try {
      producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
      LOGGER.info("Successfully connected to Kafka at {}", kafkaUrl);
    } catch (Exception e) {
      LOGGER.error("Failed to initialize Kafka producer: {}", e.getMessage());
      throw e; // Rethrow to allow caller to handle initialization failure
    }
  }

  /**
   * Configure Kafka producer properties
   */
  private Properties kafkaProps(String kafkaUrl) {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafkaUrl);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("acks", "all");
    props.put("enable.idempotence", true);
    props.put("retries", 10); // Increased from 2 to 10
    props.put("max.in.flight.requests.per.connection", 4);
    props.put("reconnect.backoff.ms", 1000); // 1 second initial backoff
    props.put("reconnect.backoff.max.ms", 30000); // 30 seconds max backoff
    props.put("retry.backoff.ms", 1000);
    props.put("request.timeout.ms", 30000); // 30 seconds request timeout
    props.put("delivery.timeout.ms", 120000); // 2 minutes delivery timeout

    return props;
  }

  /**
   * Asynchronously sends a trade to Kafka to be saved, package with the orderbook payload.
   * If Kafka is unavailable, it will attempt to reconnect.
   * 
   * @param tradeWs The trade to send to Kafka
   */
  public void saveToKafka(TradeWs tradeWs) {
    Callable<String> call = () -> {
      try {
        // Check if we need to reconnect
        if (failedMessages.get() >= MAX_FAILED_MESSAGES && !reconnecting.get()) {
          reconnectToKafka();
        }
        
        // Send the message
        produceKafkaMessage(tradeWs);
        
        // Reset failed messages counter on success
        failedMessages.set(0);
        
        // Clean up to avoid memory leaks
        tradeWs.setObp(null);
      } catch (Exception e) {
        LOGGER.error("Error sending trade to Kafka", e);
        
        // Increment failed messages counter
        int failed = failedMessages.incrementAndGet();
        LOGGER.warn("Failed to send message to Kafka. Failed messages count: {}", failed);
        
        // Trigger reconnection if threshold reached
        if (failed >= MAX_FAILED_MESSAGES && !reconnecting.get()) {
          reconnectToKafka();
        }
        
        throw new RuntimeException(e);
      }
      LOGGER.info("> > > {} to kafka", tradeWs.getTid());
      return "sent trade to Kafka";
    };
    executorService.submit(call);
  }

  /**
   * Produces a Kafka message with the trade data
   * @todo investigate if this method would also work with Trade as an input object; as then we could also parse into
   * trade when we read stuff out
   */
  public void produceKafkaMessage(TradeWs tradeWs) {
    String messageKey = String.valueOf(tradeWs.getTid());
    String messageValue;
    
    try {
      messageValue = objectMapper.writeValueAsString(tradeWs);
    } catch (JsonProcessingException e) {
      LOGGER.error("Error serializing message to JSON: {}", e.getMessage());
      throw new RuntimeException("Error serializing message", e);
    }
    
    boolean messageSent = false;
    int attempts = 0;
    
    while (!messageSent && attempts < 3) {
      attempts++;
      
      try {
        producer.send(new ProducerRecord<>("bitso-trades", messageKey, messageValue), 
            (metadata, exception) -> {
              if (exception != null) {
                LOGGER.error("Error sending message to Kafka: {}", exception.getMessage());
                handleProducerException(exception);
              } else {
                LOGGER.debug("Message sent successfully to {}-{} at offset {}", 
                    metadata.topic(), metadata.partition(), metadata.offset());
              }
            });
        
        messageSent = true;
      } catch (TimeoutException | NetworkException e) {
        LOGGER.warn("Network error sending message to Kafka (attempt {}): {}", attempts, e.getMessage());
        
        if (attempts < 3) {
          // Try to reconnect before the next attempt
          boolean reconnected = reconnect();
          if (!reconnected) {
            LOGGER.error("Failed to reconnect to Kafka, will retry message later");
            break;
          }
        }
      } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
        // We can't recover from these exceptions, so our only option is to close the producer and exit.
        LOGGER.error("Fatal error sending message to Kafka: {}", e.getMessage());
        
        try {
          producer.close();
        } catch (Exception closeEx) {
          LOGGER.warn("Error closing producer: {}", closeEx.getMessage());
        }
        
        // Try to reinitialize the producer
        initializeProducer();
        break;
      } catch (KafkaException e) {
        // For all other exceptions, just abort the transaction and try again.
        LOGGER.error("Error sending message to Kafka: {}", e.getMessage());
        
        try {
          producer.abortTransaction();
        } catch (Exception abortEx) {
          LOGGER.warn("Error aborting transaction: {}", abortEx.getMessage());
        }
        
        if (attempts < 3) {
          try {
            Thread.sleep(1000 * attempts); // Backoff before retry
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
    
    if (!messageSent) {
      LOGGER.error("Failed to send message after multiple attempts: {}", messageKey);
      throw new RuntimeException("Failed to send message after multiple attempts");
    }
  }
  
  /**
   * Handle producer exceptions and trigger reconnection if needed
   */
  private void handleProducerException(Exception exception) {
    if (exception instanceof TimeoutException || 
        exception instanceof NetworkException ||
        exception.getCause() instanceof TimeoutException || 
        exception.getCause() instanceof NetworkException) {
      
      LOGGER.warn("Network-related exception detected, attempting to reconnect");
      reconnect();
    }
  }
  
  /**
   * Attempts to reconnect to Kafka with exponential backoff
   */
  private synchronized boolean reconnect() {
    if (!reconnecting.compareAndSet(false, true)) {
      LOGGER.info("Reconnection already in progress, skipping duplicate attempt");
      return false;
    }
    
    try {
      LOGGER.info("Attempting to reconnect to Kafka...");
      
      long backoff = RECONNECT_BACKOFF_MS;
      boolean connected = false;
      int attempts = 0;
      
      while (!connected && attempts < MAX_RECONNECT_ATTEMPTS) {
        attempts++;
        try {
          LOGGER.info("Reconnection attempt {} of {}", attempts, MAX_RECONNECT_ATTEMPTS);
          
          // Close the old producer if it exists
          if (producer != null) {
            try {
              producer.close();
            } catch (Exception e) {
              LOGGER.warn("Error closing producer during reconnect: {}", e.getMessage());
            }
          }
          
          // Create a new producer
          producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
          
          // Test the connection by sending a heartbeat message
          producer.send(new ProducerRecord<>("bitso-trades", "heartbeat", "reconnection-test")).get();
          
          connected = true;
          LOGGER.info("Successfully reconnected to Kafka after {} attempts", attempts);
        } catch (Exception e) {
          LOGGER.warn("Reconnection attempt {} failed: {}", attempts, e.getMessage());
          
          if (attempts < MAX_RECONNECT_ATTEMPTS) {
            LOGGER.info("Waiting {}ms before next reconnection attempt", backoff);
            Thread.sleep(backoff);
            backoff = Math.min(backoff * 2, 30000); // Exponential backoff, max 30 seconds
          }
        }
      }
      
      if (!connected) {
        LOGGER.error("Failed to reconnect to Kafka after {} attempts", MAX_RECONNECT_ATTEMPTS);
        return false;
      }
      
      return true;
    } catch (Exception e) {
      LOGGER.error("Error during reconnection process", e);
      return false;
    } finally {
      reconnecting.set(false);
    }
  }
  
  /**
   * Attempts to reconnect to Kafka.
   * This method is synchronized to prevent multiple reconnection attempts at the same time.
   */
  public synchronized void reconnectToKafka() {
    reconnect();
  }
  
  /**
   * Checks if the producer is connected to Kafka
   */
  public boolean isConnected() {
    if (producer == null) {
      return false;
    }
    
    try {
      // Try to send a test message to check connection
      producer.send(new ProducerRecord<>("bitso-trades", "heartbeat", "connection-test")).get();
      return true;
    } catch (Exception e) {
      LOGGER.warn("Connection check failed: {}", e.getMessage());
      return false;
    }
  }
  
  /**
   * Manually triggers a reconnection to Kafka.
   */
  public void triggerReconnect() {
    reconnectToKafka();
  }
  
  /**
   * Closes the producer and executor service
   */
  public void shutdown() {
    if (producer != null) {
      try {
        producer.close();
        LOGGER.info("Kafka producer closed");
      } catch (Exception e) {
        LOGGER.warn("Error closing Kafka producer: {}", e.getMessage());
      }
    }
    
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
      LOGGER.info("Executor service shutdown");
    }
  }
}
