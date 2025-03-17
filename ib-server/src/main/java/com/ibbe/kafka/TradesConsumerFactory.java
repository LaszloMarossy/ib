package com.ibbe.kafka;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Factory for creating and managing TradesConsumer instances.
 * This allows multiple consumers to be created for different purposes
 * while still providing a singleton consumer for general use.
 */
@Component
public class TradesConsumerFactory {
    
    // Map to store named consumer instances
    private final Map<String, TradesConsumer> namedConsumers = new ConcurrentHashMap<>();
    
    // Singleton instance for general use
    private TradesConsumer singletonConsumer;
    
    /**
     * Gets or creates the singleton consumer instance.
     * This is used for general-purpose consumption where a dedicated instance is not needed.
     * 
     * @return The singleton TradesConsumer instance
     */
    public synchronized TradesConsumer getConsumer() {
        if (singletonConsumer == null) {
            singletonConsumer = new TradesConsumer();
        }
        return singletonConsumer;
    }
    
    /**
     * Gets or creates a consumer instance with the specified name.
     * This allows multiple consumers to be created for different purposes.
     * 
     * @param name The name of the consumer instance
     * @return The TradesConsumer instance for the specified name
     */
    public TradesConsumer getConsumer(String name) {
        return namedConsumers.computeIfAbsent(name, k -> new TradesConsumer());
    }
    
    /**
     * Creates a new consumer instance.
     * This is useful when a dedicated consumer is needed for a specific purpose.
     * 
     * @return A new TradesConsumer instance
     */
    public TradesConsumer createConsumer() {
        return new TradesConsumer();
    }
    
    /**
     * Removes a named consumer instance.
     * This is useful for cleanup when a named consumer is no longer needed.
     * 
     * @param name The name of the consumer instance to remove
     */
    public void removeConsumer(String name) {
        TradesConsumer consumer = namedConsumers.remove(name);
        if (consumer != null && consumer.isRunning()) {
            consumer.stopConsumer();
        }
    }
}