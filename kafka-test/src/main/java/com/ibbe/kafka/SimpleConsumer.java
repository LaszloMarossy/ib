package com.ibbe.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class SimpleConsumer {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicBoolean running = new AtomicBoolean(true);
    
    public static void main(String[] args) {
        // Default values
        String topic = "bitso-trades";
        int partition = 0;
        String bootstrapServers = "localhost:9092";
        boolean watchMode = false;
        boolean fromBeginning = true;
        int maxDisplayCount = 30;
        String filterType = null;
        String filterKey = null;
        String filterValue = null;
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--topic":
                case "-t":
                    if (i + 1 < args.length) topic = args[++i];
                    break;
                case "--partition":
                case "-p":
                    if (i + 1 < args.length) partition = Integer.parseInt(args[++i]);
                    break;
                case "--bootstrap-servers":
                case "-b":
                    if (i + 1 < args.length) bootstrapServers = args[++i];
                    break;
                case "--watch":
                case "-w":
                    watchMode = true;
                    break;
                case "--latest":
                case "-l":
                    fromBeginning = false;
                    break;
                case "--max-display":
                case "-m":
                    if (i + 1 < args.length) maxDisplayCount = Integer.parseInt(args[++i]);
                    break;
                case "--filter-type":
                    if (i + 1 < args.length) filterType = args[++i];
                    break;
                case "--filter-key":
                    if (i + 1 < args.length) filterKey = args[++i];
                    break;
                case "--filter-value":
                    if (i + 1 < args.length) filterValue = args[++i];
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    return;
                default:
                    if (args[i].startsWith("-")) {
                        System.err.println("Unknown option: " + args[i]);
                        printHelp();
                        return;
                    }
            }
        }
        
        // Register shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down Kafka consumer...");
            running.set(false);
        }));
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // Set a very low auto.offset.reset to ensure we don't miss messages
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            // Manually assign to the partition
            TopicPartition partition0 = new TopicPartition(topic, partition);
            consumer.assign(Collections.singleton(partition0));
            
            // Track key types for summary
            Map<String, Integer> keyTypeCount = new HashMap<>();
            Pattern numericPattern = Pattern.compile("^\\d+$");
            Pattern binaryPattern = Pattern.compile("\\^.*");
            
            // Initialize lastOffset
            long lastOffset = -1;
            int totalMessagesRead = 0;
            int heartbeatCounter = 0;
            
            // Seek to the beginning or end based on user preference
            if (fromBeginning) {
                consumer.seekToBeginning(Collections.singleton(partition0));
                System.out.println("Starting from the beginning of the topic");
            } else {
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(Collections.singleton(partition0));
                long endOffset = endOffsets.get(partition0);
                
                if (endOffset > 0) {
                    // In watch mode, start from the current end
                    lastOffset = endOffset - 1;
                    consumer.seek(partition0, endOffset);
                    System.out.println("Starting watch from offset " + endOffset + " (latest position)");
                } else {
                    System.out.println("Topic is empty, waiting for new messages");
                }
            }
            
            System.out.println("\n=== Kafka Monitor ===");
            System.out.println("Topic: " + topic);
            System.out.println("Partition: " + partition);
            System.out.println("Bootstrap Servers: " + bootstrapServers);
            System.out.println("Watch Mode: " + (watchMode ? "Enabled (press Ctrl+C to exit)" : "Disabled"));
            if (filterType != null) System.out.println("Filtering by type: " + filterType);
            if (filterKey != null) System.out.println("Filtering by key: " + filterKey);
            if (filterValue != null) System.out.println("Filtering by value: " + filterValue);
            System.out.println("=====================\n");
            
            if (watchMode) {
                System.out.println("Watching for new messages... (press Ctrl+C to exit)");
            }
            
            do {
                // Check for new messages in watch mode
                if (watchMode) {
                    Map<TopicPartition, Long> endOffsets = consumer.endOffsets(Collections.singleton(partition0));
                    long currentEndOffset = endOffsets.get(partition0);
                    
                    if (currentEndOffset > lastOffset + 1) {
                        // New messages detected
                        long newMessageCount = currentEndOffset - lastOffset - 1;
                        System.out.println("\n" + getCurrentTime() + " - Detected " + newMessageCount + " new message(s)");
                        
                        // Seek to where we left off + 1
                        consumer.seek(partition0, lastOffset + 1);
                    }
                }
                
                // Poll for messages with a timeout
                Duration pollDuration = watchMode ? Duration.ofMillis(1000) : Duration.ofSeconds(10);
                ConsumerRecords<String, String> records = consumer.poll(pollDuration);
                
                if (records.count() > 0) {
                    if (watchMode) {
                        System.out.println(getCurrentTime() + " - Received " + records.count() + " new records");
                        System.out.println("-------------------------------------------------------------");
                    } else {
                        System.out.println("Received " + records.count() + " records");
                        System.out.println("-------------------------------------------------------------");
                    }
                    
                    int displayCount = 0;
                    
                    for (ConsumerRecord<String, String> record : records) {
                        // Update the last offset seen
                        lastOffset = Math.max(lastOffset, record.offset());
                        totalMessagesRead++;
                        
                        String key = record.key() != null ? record.key() : "null";
                        String value = record.value();
                        
                        // Categorize key type
                        String keyType;
                        if (key == null || key.equals("null")) {
                            keyType = "null";
                        } else if (numericPattern.matcher(key).matches()) {
                            keyType = "numeric";
                        } else if (binaryPattern.matcher(key).matches() || key.contains("")) {
                            keyType = "binary";
                        } else if (key.matches("[a-zA-Z0-9-_]+")) {
                            keyType = "text";
                        } else {
                            keyType = "other";
                        }
                        
                        keyTypeCount.put(keyType, keyTypeCount.getOrDefault(keyType, 0) + 1);
                        
                        // Apply filters if specified
                        if (filterType != null && !keyType.equals(filterType)) continue;
                        if (filterKey != null && !key.contains(filterKey)) continue;
                        if (filterValue != null && (value == null || !value.contains(filterValue))) continue;
                        
                        // Don't truncate the value - display the full message
                        String displayValue = value;
                        
                        // Format the output
                        System.out.printf("Offset: %-7d | Key: %-15s | Type: %-8s | Value: %s%n", 
                                         record.offset(), 
                                         key.length() > 15 ? key.substring(0, 12) + "..." : key,
                                         keyType,
                                         displayValue);
                        
                        displayCount++;
                        
                        // Only display a limited number of messages to avoid overwhelming output
                        if (displayCount >= maxDisplayCount && records.count() > maxDisplayCount) {
                            System.out.println("... (showing first " + displayCount + " of " + records.count() + " messages)");
                            break;
                        }
                    }
                    
                    // Display summary of key types
                    // System.out.println("-------------------------------------------------------------");
                    // System.out.println("KEY TYPE SUMMARY:");
                    // for (Map.Entry<String, Integer> entry : keyTypeCount.entrySet()) {
                    //     System.out.printf("%-8s: %d messages (%.1f%%)%n", 
                    //                      entry.getKey(),
                    //                      entry.getValue(),
                    //                      (entry.getValue() * 100.0 / totalMessagesRead));
                    // }
                    
                    // Get the latest offset
                    long endOffset = consumer.endOffsets(Collections.singleton(partition0)).get(partition0);
                    
                    System.out.println("-------------------------------------------------------------");
                    System.out.println("Total messages in topic: " + endOffset + " messages read: " + totalMessagesRead);
                    // System.out.println("No offsets were committed.");
                    
                    if (watchMode) {
                        System.out.println("\nWaiting for new messages... (press Ctrl+C to exit)");
                        heartbeatCounter = 0;
                    }
                } else if (watchMode) {
                    // In watch mode, if no new records, just wait
                    try {
                        // Print a heartbeat dot every second to show we're still watching
                        heartbeatCounter++;
                        if (heartbeatCounter % 5 == 0) {
                            System.out.print(".");
                            System.out.flush();
                        }
                        if (heartbeatCounter >= 60) {
                            // Every minute, print a timestamp
                            System.out.println("\n" + getCurrentTime() + " - Still watching for messages...");
                            heartbeatCounter = 0;
                        }
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
            } while (watchMode && running.get());
            
            if (!watchMode) {
                System.out.println("\nMonitoring completed.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String getCurrentTime() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }
    
    private static void printHelp() {
        System.out.println("Kafka Monitor - A tool to monitor Kafka topics");
        System.out.println("\nUsage: java -jar kafka-test.jar [options]");
        System.out.println("\nOptions:");
        System.out.println("  -t, --topic TOPIC            Kafka topic to monitor (default: bitso-trades)");
        System.out.println("  -p, --partition PARTITION    Partition to monitor (default: 0)");
        System.out.println("  -b, --bootstrap-servers SERVERS  Kafka bootstrap servers (default: localhost:9092)");
        System.out.println("  -w, --watch                  Watch mode - continuously monitor for new messages");
        System.out.println("  -l, --latest                 Start from the latest messages instead of beginning");
        System.out.println("  -m, --max-display COUNT      Maximum number of messages to display (default: 30)");
        System.out.println("  --filter-type TYPE           Filter messages by key type (numeric, text, binary, null)");
        System.out.println("  --filter-key PATTERN         Filter messages by key containing pattern");
        System.out.println("  --filter-value PATTERN       Filter messages by value containing pattern");
        System.out.println("  -h, --help                   Show this help message");
        System.out.println("\nExamples:");
        System.out.println("  java -jar kafka-test.jar --topic my-topic --watch");
        System.out.println("  java -jar kafka-test.jar --latest --filter-type text");
    }
}
