package com.ibbe.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.TradeConfig;
import com.ibbe.fx.PerformanceWindow;
import com.ibbe.util.PropertiesUtil;
import com.ibbe.util.RandomString;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket client for connecting to the PerformanceAnalysisEndpoint.
 * Sends configuration data and receives performance analysis results.
 */
public class PerformanceAnalysisClient extends TextWebSocketHandler {
    private final PerformanceWindow window;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketSession session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService chartUpdater = Executors.newSingleThreadScheduledExecutor();
    
    // Queue to store data points for throttled processing
    private final Queue<PerformanceData> dataQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingActive = new AtomicBoolean(false);
    
    /**
     * Creates a new PerformanceAnalysisClient.
     * 
     * @param window The PerformanceWindow to update with analysis results
     */
    public PerformanceAnalysisClient(PerformanceWindow window) {
        this.window = window;
    }
    
    /**
     * Starts the performance analysis by connecting to the server.
     * 
     * @param ups The ups value for the configuration
     * @param downs The downs value for the configuration
     */
    public void startPerformanceAnalysis(String ups, String downs) {
        executor.submit(() -> {
            try {
                // Close existing session if any
                if (session != null && session.isOpen()) {
                    session.close();
                }
                
                // Clear any existing data in the queue
                dataQueue.clear();
                
                // Stop any existing processing
                processingActive.set(false);
                
                // Create a new WebSocket client
                WebSocketClient client = new StandardWebSocketClient();
                
                // Build the WebSocket URL
                String wsUrl = PropertiesUtil.getProperty("server.ws.url");
                String performanceAnalysisEndpoint = wsUrl.replace("/websocket", "/performanceanalysis");
                
                System.out.println("Connecting to: " + performanceAnalysisEndpoint);
                
                // Connect to the server
                session = client.execute(this, performanceAnalysisEndpoint).get();
                
                // Create a trade configuration
                TradeConfig config = new TradeConfig(RandomString.getRandomString(), ups, downs);
                
                // Send the configuration to the server
                String configJson = objectMapper.writeValueAsString(config);
                session.sendMessage(new TextMessage(configJson));
                
                // Start processing data at a rate of 3 records per second
                processingActive.set(true);
                startProcessingData();
                
                // Update status
                window.updateStatus("Connected and analyzing...");
                
            } catch (Exception e) {
                e.printStackTrace();
                window.updateStatus("Error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Starts processing data from the queue as fast as possible.
     */
    private void startProcessingData() {
        chartUpdater.scheduleAtFixedRate(() -> {
            if (processingActive.get()) {
                // Process all available data in the queue in batches
                int processedCount = 0;
                int maxBatchSize = 50; // Process up to 50 records per batch to maintain UI responsiveness
                
                while (processingActive.get() && processedCount < maxBatchSize) {
                    PerformanceData data = dataQueue.poll();
                    if (data == null) {
                        break; // No more data in queue
                    }
                    
                    // Debug output to check values before sending to window
                    System.out.println("Processing from queue - Trade Price: " + data.tradePrice + 
                            ", Ask: " + data.avgAskPrice + ", Bid: " + data.avgBidPrice + 
                            ", Amount: " + (data.amountMissing ? "MISSING" : data.tradeAmount));
                    
                    // Only skip if price is null - removed the low value check
                    if (data.tradePrice == null) {
                        System.out.println("Skipping queued data point with null trade price");
                        continue;
                    }
                    
                    try {
                        window.addDataPoint(
                                data.tradePrice,
                                data.tradeAmount,
                                data.avgAskPrice,
                                data.avgAskAmount,
                                data.avgBidPrice,
                                data.avgBidAmount,
                                data.timestamp,
                                data.amountMissing
                        );
                        System.out.println("Successfully added data point to window with price: " + data.tradePrice);
                    } catch (Exception e) {
                        System.err.println("Error adding data point to window: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    processedCount++;
                }
                
                if (processedCount > 0) {
                    System.out.println("Processed " + processedCount + " data points in this batch. Queue size: " + dataQueue.size());
                }
            }
        }, 0, 10, TimeUnit.MILLISECONDS); // Run every 10ms for maximum throughput while maintaining UI responsiveness
    }
    
    /**
     * Handles incoming WebSocket messages.
     * Parses performance data and updates the window.
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            // Parse the JSON message
            JsonNode node = objectMapper.readTree(message.getPayload());
            
            // Check if it's an error message
            if (node.has("error") && node.get("error").asBoolean()) {
                String errorMessage = node.has("message") ? node.get("message").asText() : "Unknown error";
                window.updateStatus("Error: " + errorMessage);
                return;
            }
            
            // Extract performance data
            BigDecimal tradePrice = getBigDecimal(node, "tradePrice");
            BigDecimal tradeAmount = getBigDecimal(node, "tradeAmount");
            BigDecimal avgAskPrice = getBigDecimal(node, "avgAskPrice");
            BigDecimal avgAskAmount = getBigDecimal(node, "avgAskAmount");
            BigDecimal avgBidPrice = getBigDecimal(node, "avgBidPrice");
            BigDecimal avgBidAmount = getBigDecimal(node, "avgBidAmount");
            
            // Extract timestamp (defaults to current time if not available)
            long timestamp = node.has("timestamp") ? node.get("timestamp").asLong() : System.currentTimeMillis();
            
            // Extract amountMissing flag
            boolean amountMissing = node.has("amountMissing") && node.get("amountMissing").asBoolean();
            
            // Debug output to check raw values from JSON
            System.out.println("Received from server - Trade Price: " + tradePrice + 
                    ", Ask: " + avgAskPrice + ", Bid: " + avgBidPrice + 
                    ", Amount: " + (amountMissing ? "MISSING" : tradeAmount));
            
            // Only skip if price is null - removed the low value check
            if (tradePrice == null) {
                System.out.println("Client skipping data point with null trade price");
                return;
            }
            
            // Create a data object and add it to the queue for throttled processing
            PerformanceData data = new PerformanceData(
                    tradePrice,
                    tradeAmount,
                    avgAskPrice,
                    avgAskAmount,
                    avgBidPrice,
                    avgBidAmount,
                    timestamp,
                    amountMissing
            );
            
            dataQueue.add(data);
            System.out.println("Added data point to queue. Queue size: " + dataQueue.size());
            
            // Update status with the latest trade ID
            if (node.has("tradeId")) {
                window.updateStatus("Received Trade ID: " + node.get("tradeId").asLong() + 
                        " (Queued: " + dataQueue.size() + ")");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            window.updateStatus("Error processing message: " + e.getMessage());
        }
    }
    
    /**
     * Safely extracts a BigDecimal from a JSON node.
     */
    private BigDecimal getBigDecimal(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            try {
                BigDecimal value = new BigDecimal(node.get(fieldName).asText());
                System.out.println("Extracted " + fieldName + ": " + value);
                return value;
            } catch (Exception e) {
                System.err.println("Error parsing " + fieldName + ": " + e.getMessage());
                return null;
            }
        }
        System.out.println("Field " + fieldName + " is null or missing");
        return null;
    }
    
    /**
     * Handles WebSocket connection closure.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        processingActive.set(false);
        window.updateStatus("Disconnected: " + status.getReason());
    }
    
    /**
     * Handles WebSocket transport errors.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        processingActive.set(false);
        window.updateStatus("Connection error: " + exception.getMessage());
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Data class for performance data.
     */
    private static class PerformanceData {
        private final BigDecimal tradePrice;
        private final BigDecimal tradeAmount;
        private final BigDecimal avgAskPrice;
        private final BigDecimal avgAskAmount;
        private final BigDecimal avgBidPrice;
        private final BigDecimal avgBidAmount;
        private final long timestamp;
        private final boolean amountMissing;
        
        public PerformanceData(
                BigDecimal tradePrice,
                BigDecimal tradeAmount,
                BigDecimal avgAskPrice,
                BigDecimal avgAskAmount,
                BigDecimal avgBidPrice,
                BigDecimal avgBidAmount,
                long timestamp,
                boolean amountMissing) {
            this.tradePrice = tradePrice;
            this.tradeAmount = tradeAmount;
            this.avgAskPrice = avgAskPrice;
            this.avgAskAmount = avgAskAmount;
            this.avgBidPrice = avgBidPrice;
            this.avgBidAmount = avgBidAmount;
            this.timestamp = timestamp;
            this.amountMissing = amountMissing;
        }
    }
} 