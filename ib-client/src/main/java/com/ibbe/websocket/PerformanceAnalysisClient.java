package com.ibbe.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.PerformanceData;
import com.ibbe.entity.Trade;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import javafx.application.Platform;

/**
 * WebSocket client for connecting to the PerformanceAnalysisEndpoint.
 * Sends configuration data and receives performance analysis results.
 * Maintains a complete dataset of up to 50,000 records for efficient windowing.
 */
public class PerformanceAnalysisClient extends TextWebSocketHandler {
    private final PerformanceWindow window;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketSession session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService chartUpdater;
    
    // Queue to store data points for throttled processing
    private final Queue<PerformanceData> dataQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingActive = new AtomicBoolean(false);
    
    // Complete dataset storage - thread-safe list with capacity for 50,000 records
    private final List<PerformanceData> completeDataset = Collections.synchronizedList(new ArrayList<>(50000));
    private final AtomicInteger totalRecordsReceived = new AtomicInteger(0);
    
    // Maximum number of records to store in memory
    private static final int MAX_RECORDS = 50000;
    
    // Window size for batch updates to the UI
    private static final int UI_WINDOW_SIZE = 1000;
    
    // Add a configuration for maximum dataset size
    private static final int MAX_DATASET_SIZE = 50000;
    
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
                
                // Clear any existing data
                dataQueue.clear();
                completeDataset.clear();
                totalRecordsReceived.set(0);
                
                // Stop any existing processing
                processingActive.set(false);
                
                // Create a new WebSocket client
                WebSocketClient client = new StandardWebSocketClient();
                
                // Build the WebSocket URL
                String wsUrl = PropertiesUtil.getProperty("server.ws.url");
                String performanceAnalysisEndpoint = wsUrl.replace("/websocket", "/performanceanalysis");
                
                // Connect to the server
                session = client.execute(this, performanceAnalysisEndpoint).get();
                
                // Create a trade configuration
                TradeConfig config = new TradeConfig(RandomString.getRandomString(), ups, downs);
                
                // Send the configuration to the server
                String configJson = objectMapper.writeValueAsString(config);
                session.sendMessage(new TextMessage(configJson));
                
                // Start processing data
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
     * Starts processing data from the queue efficiently.
     * Uses batching to reduce UI updates and improve performance.
     */
    private void startProcessingData() {
        if (chartUpdater != null && !chartUpdater.isShutdown()) {
            chartUpdater.shutdownNow();
        }
        
        chartUpdater = Executors.newSingleThreadScheduledExecutor();
        chartUpdater.scheduleAtFixedRate(() -> {
            try {
                // Process data in batches for better performance
                List<PerformanceData> batch = new ArrayList<>();
                PerformanceData data;
                
                // Collect up to 100 data points in a batch
                while ((data = dataQueue.poll()) != null && batch.size() < 100) {
                    batch.add(data);
                    addToCompleteDataset(data);
                }
                
                if (!batch.isEmpty()) {
                    // Update the UI with the batch of data
                    updateUIWithBatch(batch);
                }
            } catch (Exception e) {
                System.err.println("Error processing data: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Adds a data point to the complete dataset, maintaining the maximum size.
     * 
     * @param data The data point to add
     */
    private void addToCompleteDataset(PerformanceData data) {
        synchronized (completeDataset) {
            completeDataset.add(data);
            
            // Increment the total records counter
            totalRecordsReceived.incrementAndGet();
            
            // Trim the dataset if it exceeds the maximum size
            if (completeDataset.size() > MAX_DATASET_SIZE) {
                // Remove the oldest data points to maintain the maximum size
                int excessPoints = completeDataset.size() - MAX_DATASET_SIZE;
                completeDataset.subList(0, excessPoints).clear();
            }
        }
    }
    
    /**
     * Updates the UI with a batch of data points.
     * 
     * @param batch The batch of data points to update the UI with
     */
    private void updateUIWithBatch(List<PerformanceData> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        // Get the last data point for UI updates
        PerformanceData lastData = batch.get(batch.size() - 1);
        
        // Extract balance information if available
        if (lastData.getFxTradesDisplayData() != null) {
            FxTradesDisplayData displayData = lastData.getFxTradesDisplayData();
            
            // Update balance and profit information
            if (displayData.getCurrencyBalance() != null && 
                displayData.getCoinBalance() != null && 
                displayData.getProfit() != null) {
                
                Platform.runLater(() -> {
                    try {
                        window.updateBalanceDisplay(
                            displayData.getCurrencyBalance(),
                            displayData.getCoinBalance(),
                            displayData.getProfit()
                        );
                    } catch (Exception e) {
                        System.err.println("Error updating balance and profit: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        }
        
        // Update the chart with the new data
        Platform.runLater(() -> {
            try {
                // Notify the window that new data is available
                window.onNewDataAvailable();
            } catch (Exception e) {
                System.err.println("Error updating chart: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Gets a window of data from the complete dataset.
     * 
     * @param startIndex The start index of the window
     * @param windowSize The size of the window
     * @return A list of data points in the specified window
     */
    public List<PerformanceData> getDataWindow(int startIndex, int windowSize) {
        synchronized (completeDataset) {
            if (completeDataset.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Ensure the start index is valid
            int validStartIndex = Math.max(0, Math.min(startIndex, completeDataset.size() - 1));
            
            // Ensure the window size doesn't exceed the available data
            int validWindowSize = Math.min(windowSize, completeDataset.size() - validStartIndex);
            
            // Return a copy of the window to prevent concurrent modification issues
            return new ArrayList<>(completeDataset.subList(validStartIndex, validStartIndex + validWindowSize));
        }
    }
    
    /**
     * Gets the total number of data points in the complete dataset.
     * 
     * @return The total number of data points
     */
    public int getDatasetSize() {
        synchronized (completeDataset) {
            return completeDataset.size();
        }
    }
    
    /**
     * Gets the total number of records received since the start of the analysis.
     * 
     * @return The total number of records received
     */
    public int getTotalRecordsReceived() {
        return totalRecordsReceived.get();
    }
    
    /**
     * Handles incoming WebSocket messages.
     * Parses performance data and updates the window.
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            
            // Try to deserialize the message directly into a PerformanceData object
            try {
                PerformanceData data = objectMapper.readValue(payload, PerformanceData.class);
                
                // Add the data to the queue for processing
                if (data != null) {
                    dataQueue.offer(data);
                }
            } catch (Exception e) {
                // If direct deserialization fails, try to extract fields manually
                System.err.println("Failed to deserialize message directly: " + e.getMessage());
                
                // Extract fields from the JSON message
                JsonNode rootNode = objectMapper.readTree(payload);
                
                PerformanceData data = new PerformanceData();
                
                // Extract basic fields
                if (rootNode.has("sequence")) {
                    data.setSequence(rootNode.get("sequence").asInt());
                }
                
                if (rootNode.has("timestamp")) {
                    data.setTimestamp(rootNode.get("timestamp").asLong());
                }
                
                if (rootNode.has("tradePrice")) {
                    data.setTradePrice(rootNode.get("tradePrice").asDouble());
                }
                
                if (rootNode.has("tradeAmount")) {
                    data.setTradeAmount(rootNode.get("tradeAmount").asDouble());
                }
                
                if (rootNode.has("avgAskPrice")) {
                    data.setAvgAskPrice(rootNode.get("avgAskPrice").asDouble());
                }
                
                if (rootNode.has("avgAskAmount")) {
                    data.setAvgAskAmount(rootNode.get("avgAskAmount").asDouble());
                }
                
                if (rootNode.has("avgBidPrice")) {
                    data.setAvgBidPrice(rootNode.get("avgBidPrice").asDouble());
                }
                
                if (rootNode.has("avgBidAmount")) {
                    data.setAvgBidAmount(rootNode.get("avgBidAmount").asDouble());
                }
                
                // Extract pretend trade if present
                if (rootNode.has("pretendTrade") && !rootNode.get("pretendTrade").isNull()) {
                    JsonNode tradeNode = rootNode.get("pretendTrade");
                    Trade trade = objectMapper.treeToValue(tradeNode, Trade.class);
                    data.setPretendTrade(trade);
                }
                
                // Extract trades display data if present
                if (rootNode.has("tradesDisplayData") && !rootNode.get("tradesDisplayData").isNull()) {
                    JsonNode displayDataNode = rootNode.get("tradesDisplayData");
                    FxTradesDisplayData displayData = objectMapper.treeToValue(displayDataNode, FxTradesDisplayData.class);
                    data.setFxTradesDisplayData(displayData);
                }
                
                // Add the data to the queue for processing
                dataQueue.offer(data);
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
            
            // Update status to show the error
            Platform.runLater(() -> {
                window.updateStatus("Error: " + e.getMessage());
            });
        }
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
     * Handles WebSocket connection establishment.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        window.updateStatus("Connected to server");
    }
    
    /**
     * Handles WebSocket transport errors.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        processingActive.set(false);
        window.updateStatus("Transport error: " + exception.getMessage());
    }
    
    /**
     * Disconnects from the server and cleans up resources.
     * This method should be called when the application is shutting down.
     */
    public void disconnect() {
        try {
            // Stop processing data
            processingActive.set(false);
            
            // Clear the data queue and dataset
            dataQueue.clear();
            completeDataset.clear();
            
            // Close the WebSocket session if it's open
            if (session != null && session.isOpen()) {
                session.close(CloseStatus.NORMAL);
                session = null;
            }
            
            // Shutdown the executor services
            chartUpdater.shutdown();
            try {
                if (!chartUpdater.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    chartUpdater.shutdownNow();
                }
            } catch (InterruptedException e) {
                chartUpdater.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            executor.shutdown();
            try {
                if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            window.updateStatus("Disconnected from server");
        } catch (Exception e) {
            e.printStackTrace();
            window.updateStatus("Error during disconnect: " + e.getMessage());
        }
    }
} 