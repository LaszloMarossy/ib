package com.ibbe.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class SimpleConsumer {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "simple-consumer");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.LongDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");

        try (KafkaConsumer<Long, String> consumer = new KafkaConsumer<>(props)) {
            // Subscribe to the topic
            consumer.subscribe(Collections.singletonList("bitso-trades"));
            
            System.out.println("Starting consumer...");
            
            // Poll for messages
            while (true) {
                ConsumerRecords<Long, String> records = consumer.poll(Duration.ofMillis(1000));
                
                if (!records.isEmpty()) {
                    System.out.println("Received " + records.count() + " records");
                    
                    for (ConsumerRecord<Long, String> record : records) {
                        System.out.println("Offset: " + record.offset() + 
                                           ", Key: " + record.key() + 
                                           ", Value: " + record.value());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error consuming messages: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 