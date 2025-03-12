package com.ibbe.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * A simple test consumer to verify we can read from the bitso-trades topic.
 */
public class SimpleConsumerTest {

    public static void main(String[] args) {
        // Configure consumer properties
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10"); // Limit to 10 records per poll
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        
        // Create consumer
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            // Subscribe to the topic
            consumer.subscribe(Collections.singletonList("bitso-trades"));
            
            System.out.println("Starting to consume from bitso-trades topic...");
            
            // Poll for records
            int messageCount = 0;
            int maxMessages = 20; // Limit to 20 messages total
            
            while (messageCount < maxMessages) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000));
                
                if (records.isEmpty()) {
                    System.out.println("No records received in this poll. Trying again...");
                    continue;
                }
                
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("Offset: " + record.offset() + 
                                      ", Key: " + record.key() + 
                                      ", Value length: " + (record.value() != null ? record.value().length() : 0));
                    
                    // Print a sample of the value (first 100 chars)
                    if (record.value() != null && record.value().length() > 0) {
                        String sample = record.value().length() > 100 ? 
                                       record.value().substring(0, 100) + "..." : 
                                       record.value();
                        System.out.println("Value sample: " + sample);
                    }
                    
                    messageCount++;
                    if (messageCount >= maxMessages) {
                        break;
                    }
                }
                
                System.out.println("Processed " + messageCount + " messages so far");
            }
            
            System.out.println("Finished consuming messages");
        } catch (Exception e) {
            System.err.println("Error consuming from Kafka: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 