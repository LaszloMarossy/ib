package com.ibbe.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.PerformanceData;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.entity.ChunkInfo;
import com.ibbe.fx.PerformanceWindowInterface;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private final PerformanceWindowInterface window;
    private final ObjectMapper objectMapper;
    private WebSocketSession session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService chartUpdater;
    
    // Queue to store data points for throttled processing
    private final Queue<PerformanceData> dataQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingActive = new AtomicBoolean(false);
    
    // Complete dataset storage - thread-safe list with capacity for 50,000 records
    private final List<PerformanceData> completeDataset = Collections.synchronizedList(new ArrayList<>(50000));
    private final AtomicInteger totalRecordsReceived = new AtomicInteger(0);
    
    // Track the last used configuration ID
    private String currentConfigId = null;
    
    // Maximum number of records to store in memory
    private static final int MAX_RECORDS = 50000;
    
    // Window size for batch updates to the UI
    private static final int UI_WINDOW_SIZE = 1000;
    
    // Add a configuration for maximum dataset size
    private static final int MAX_DATASET_SIZE = 50000;
    
    /**
     * Custom deserializer for ChunkInfo to handle deserialization from the serialized format
     */
    private static class ChunkInfoDeserializer extends StdDeserializer<ChunkInfo> {
        
        public ChunkInfoDeserializer() {
            super(ChunkInfo.class);
        }
        
        @Override
        public ChunkInfo deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            
            int chunkNumber = node.get("chunkNumber").asInt();
            BigDecimal profit = new BigDecimal(node.get("profit").asText());
            BigDecimal startingTradePrice = new BigDecimal(node.get("startingTradePrice").asText());
            BigDecimal endingTradePrice = new BigDecimal(node.get("endingTradePrice").asText());
            int tradeCount = node.get("tradeCount").asInt();
            long startTimeMillis = node.get("startTimeMillis").asLong();
            long endTimeMillis = node.get("endTimeMillis").asLong();
            
            // Create ChunkInfo using constructor with millisecond timestamps
            return new ChunkInfo(
                    chunkNumber,
                    profit,
                    startingTradePrice,
                    endingTradePrice,
                    tradeCount,
                    startTimeMillis,
                    endTimeMillis
            );
        }
    }
    
    /**
     * Creates a new PerformanceAnalysisClient.
     * 
     * @param window The window to update with analysis results
     */
    public PerformanceAnalysisClient(PerformanceWindowInterface window) {
        this.window = window;
        // Initialize standard ObjectMapper with custom deserializer for ChunkInfo
        this.objectMapper = new ObjectMapper();
        
        // Configure ObjectMapper to ignore unknown properties
        // This will prevent errors when new fields like totalChunkCount are added to the JSON but not to the class
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Register the custom deserializer for ChunkInfo
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ChunkInfo.class, new ChunkInfoDeserializer());
        this.objectMapper.registerModule(module);
    }
    
    /**
     * Returns the ID of the currently active configuration
     * 
     * @return The current configuration ID, or null if no configuration is active
     */
    public String getCurrentConfigId() {
        return currentConfigId;
    }
    
    /**
     * Starts the performance analysis by connecting to the server.
     * 
     * @param ups The ups value for the configuration
     * @param downs The downs value for the configuration
     */
    public void startPerformanceAnalysis(String ups, String downs) {
        // Call the expanded method with default values (false) for all criteria
        startPerformanceAnalysis(ups, downs, false, false, false, false);
    }
    
    /**
     * Starts the performance analysis by connecting to the server with expanded criteria options.
     * 
     * @param ups The ups value for the configuration
     * @param downs The downs value for the configuration
     * @param useAvgBidVsAvgAsk Whether to use average bid vs average ask in trading decisions
     * @param useShortVsLongMovAvg Whether to use short vs long moving average in trading decisions
     * @param useSumAmtUpVsDown Whether to use sum amount up vs down in trading decisions
     * @param useTradePriceCloserToAskVsBuy Whether to use trade price closer to ask vs buy in trading decisions
     */
    public void startPerformanceAnalysis(String ups, String downs, 
                                       boolean useAvgBidVsAvgAsk,
                                       boolean useShortVsLongMovAvg,
                                       boolean useSumAmtUpVsDown,
                                       boolean useTradePriceCloserToAskVsBuy) {
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
                
                // First, create the TradeConfig using the REST POST endpoint
                String configId = RandomString.getRandomString();
                TradeConfig tradeConfig = new TradeConfig(configId, ups, downs, useAvgBidVsAvgAsk,
                    useShortVsLongMovAvg, useSumAmtUpVsDown, useTradePriceCloserToAskVsBuy);
                
                // Create JSON representation
                String configJson = objectMapper.writeValueAsString(tradeConfig);
                
                // Send the configuration to the REST endpoint first
                String serverUrl = PropertiesUtil.getProperty("server.rest.url");
                String configEndpoint = serverUrl + "/configuration";
                
                // Update status to show we're creating the configuration
                window.updateStatus("Creating trading configuration...");
                
                // Send the POST request
                try {
                    // Create an HTTP client
                    HttpClient httpClient = HttpClient.newHttpClient();
                    
                    // Create the request
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(configEndpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(configJson))
                        .build();
                    
                    // Send the request
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    // Check the response
                    if (response.statusCode() != 201) {
                        throw new Exception("Error creating configuration: " + response.body());
                    }
                    
                    window.updateStatus("Configuration created successfully, connecting to WebSocket...");
                } catch (Exception e) {
                    throw new Exception("Error sending configuration: " + e.getMessage(), e);
                }
                
                // Now connect to the WebSocket to monitor the configuration
                session = client.execute(this, performanceAnalysisEndpoint).get();
                
                // Send the configuration to the WebSocket server
                session.sendMessage(new TextMessage(configJson));
                
                // Start processing data
                processingActive.set(true);
                startProcessingData();
                
                // Update status
                window.updateStatus("Connected and analyzing...");
                
                // Update the current config ID
                currentConfigId = configId;
                
            } catch (Exception e) {
                e.printStackTrace();
                window.updateStatus("Error: " + e.getMessage(), true);
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
        
        System.out.println("Starting data processing thread...");
        chartUpdater = Executors.newSingleThreadScheduledExecutor();
        
        // Set processing active flag
        processingActive.set(true);
        
        chartUpdater.scheduleAtFixedRate(() -> {
            try {
                if (!processingActive.get()) {
                    System.out.println("Processing thread stopped - processingActive is false, restarting...");
                    // Try to restart processing since we have an active session but processing stopped
                    if (session != null && session.isOpen()) {
                        processingActive.set(true);
                        System.out.println("Session is still active, resuming processing");
                    } else {
                        System.out.println("Session is closed, not resuming processing");
                        return;
                    }
                }
                
                // Process data in batches for better performance
                List<PerformanceData> batch = new ArrayList<>();
                PerformanceData data;
                
                // Collect up to 100 data points in a batch
                while ((data = dataQueue.poll()) != null && batch.size() < 100) {
                    batch.add(data);
                }
                
                if (!batch.isEmpty()) {
                    System.out.println("Processing batch of " + batch.size() + " data points. Queue size: " + dataQueue.size());
                    // Update the UI with the batch of data
                    updateUIWithBatch(batch);
                }
            } catch (Exception e) {
                System.err.println("Error processing data: " + e.getMessage());
                e.printStackTrace();
                // Don't let the thread die due to errors
                if (session != null && session.isOpen()) {
                    System.out.println("Encountered error but session is active, continuing processing");
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        
        System.out.println("Data processing thread started successfully");
    }
    
    /**
     * Restarts the data processing thread if it was stopped.
     * This can be called when new data arrives but processing is inactive.
     */
    private void ensureProcessingActive() {
        if (!processingActive.get() && session != null && session.isOpen()) {
            System.out.println("Processing was inactive but session is open, restarting processing thread");
            startProcessingData();
        }
    }
    
    /**
     * Adds a data point to the complete dataset, respecting the maximum size limit.
     * Memory optimization to prevent OOM errors.
     *
     * @param data The data point to add.
     */
    private void addToCompleteDataset(PerformanceData data) {
        synchronized (completeDataset) {
            // Add the new data point
            completeDataset.add(data);
            
            // If we have too many records, remove the oldest ones
            // Instead of constant pruning, only trim when significantly over limit
            if (completeDataset.size() > MAX_RECORDS + 500) {
                int removeCount = completeDataset.size() - MAX_RECORDS;
                completeDataset.subList(0, removeCount).clear();
            }
        }
        totalRecordsReceived.incrementAndGet();
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
                
                System.out.println("Updating balance display - Currency: " + displayData.getCurrencyBalance() + 
                    ", Coin: " + displayData.getCoinBalance() + ", Profit: " + displayData.getProfit());
                
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
        
        // Ensure data is added to the complete dataset before updating UI
        synchronized (completeDataset) {
            for (PerformanceData data : batch) {
                if (!completeDataset.contains(data)) {
                    completeDataset.add(data);
                }
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
     * Retrieves a window of data from the complete dataset.
     * Optimized to handle large datasets efficiently.
     *
     * @param startIndex The start index.
     * @param windowSize The size of the window.
     * @return A list containing the data points in the requested window.
     */
    public List<PerformanceData> getDataWindow(int startIndex, int windowSize) {
        synchronized (completeDataset) {
            if (completeDataset.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Validate the start index
            int validStartIndex = Math.min(Math.max(0, startIndex), completeDataset.size() - 1);
            
            // For Quick Replay mode (mode 2), we always want ALL data
            if (window.getMode() == 2) {
                return new ArrayList<>(completeDataset);
            }
            
            // For slider view requests, limit window size to improve performance
            // For full dataset requests (windowSize == MAX_DATASET_SIZE), return a reasonably sized chunk
            int validWindowSize;
            if (windowSize >= MAX_DATASET_SIZE) {
                // For trade history, limit to last 100 records with trades
                validWindowSize = Math.min(2000, completeDataset.size() - validStartIndex);
            } else {
                // For normal chart view, use requested window size
                validWindowSize = Math.min(windowSize, completeDataset.size() - validStartIndex);
            }
            
            // Return a copy of the data window
            try {
                return new ArrayList<>(completeDataset.subList(validStartIndex, validStartIndex + validWindowSize));
            } catch (OutOfMemoryError e) {
                // Fallback to a smaller window if we hit memory issues
                System.gc();
                return new ArrayList<>(completeDataset.subList(validStartIndex, validStartIndex + Math.min(500, validWindowSize)));
            }
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
     * Gets the total number of pretend trades in the dataset.
     * 
     * @return The number of pretend trades
     */
    public int getPretendTradeCount() {
        synchronized (completeDataset) {
            int count = 0;
            for (PerformanceData data : completeDataset) {
                if (data.getPretendTrade() != null) {
                    count++;
                }
            }
            return count;
        }
    }
    
    /**
     * Handles incoming WebSocket messages.
     * Parses performance data and updates the window.
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
//            System.out.println("Received WebSocket message: " + payload.substring(0, Math.min(200, payload.length())) + "...");
            
            // If processing is not active but we're receiving data, restart it
            if (!processingActive.get()) {
                System.out.println("Received data but processing is inactive, restarting processing thread");
                ensureProcessingActive();
            }
            
            // Try to deserialize the message directly into a PerformanceData object
            try {
                PerformanceData data = objectMapper.readValue(payload, PerformanceData.class);
                
                // Add the data to the queue for processing
                if (data != null) {
                    dataQueue.offer(data);
//                    System.out.println("Successfully processed message and added to queue. Queue size: " + dataQueue.size());
                } else {
                    System.err.println("Deserialized data was null");
                }
            } catch (Exception e) {
                // If direct deserialization fails, try to extract fields manually
                System.err.println("Failed to deserialize message directly: " + payload);
                e.printStackTrace();
                
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
                
                // Extract chunk profits if present
                if (rootNode.has("chunkProfits") && !rootNode.get("chunkProfits").isNull()) {
                    JsonNode profitsNode = rootNode.get("chunkProfits");
                    List<BigDecimal> chunkProfits = new ArrayList<>();
                    if (profitsNode.isArray()) {
                        for (JsonNode profitNode : profitsNode) {
                            chunkProfits.add(new BigDecimal(profitNode.asText()));
                        }
                    }
                    data.setChunkProfits(chunkProfits);
                }
                
                // Extract chunks list if present
                if (rootNode.has("chunks") && !rootNode.get("chunks").isNull()) {
                    JsonNode chunksNode = rootNode.get("chunks");
                    List<ChunkInfo> chunks = new ArrayList<>();
                    if (chunksNode.isArray()) {
                        for (JsonNode chunkNode : chunksNode) {
                            ChunkInfo chunk = objectMapper.treeToValue(chunkNode, ChunkInfo.class);
                            chunks.add(chunk);
                        }
                    }
                    data.setChunks(chunks);
                    System.out.println("Extracted " + chunks.size() + " chunks from message");
                }
                
                // Extract current chunk if present
                if (rootNode.has("currentChunk") && !rootNode.get("currentChunk").isNull()) {
                    JsonNode currentChunkNode = rootNode.get("currentChunk");
                    ChunkInfo currentChunk = objectMapper.treeToValue(currentChunkNode, ChunkInfo.class);
                    data.setCurrentChunk(currentChunk);
                    System.out.println("Extracted current chunk from message");
                }
                
                // Extract totalChunkCount if present
                if (rootNode.has("totalChunkCount")) {
                    int totalChunkCount = rootNode.get("totalChunkCount").asInt();
                    data.setTotalChunkCount(totalChunkCount);
                    System.out.println("Extracted totalChunkCount: " + totalChunkCount);
                }
                
                // Add the data to the queue for processing
                dataQueue.offer(data);
                System.out.println("Successfully processed message manually and added to queue. Queue size: " + dataQueue.size());
                
                // Make sure processing is active for this new data
                ensureProcessingActive();
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
     * Disconnect from the server and clean up resources.
     */
    public void disconnect() {
        if (chartUpdater != null) {
            chartUpdater.shutdownNow();
            chartUpdater = null;
        }
        
        processingActive.set(false);
        
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Checks if the client is connected to the server.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return session != null && session.isOpen();
    }
    
    /**
     * Ends the performance analysis for a given configuration ID by removing it from the server.
     * This should be called before starting a new performance analysis to clean up resources.
     * 
     * @param configId The ID of the configuration to remove
     */
    public void endPerformanceAnalysis(String configId) {
        executor.submit(() -> {
            try {
                if (configId == null || configId.trim().isEmpty()) {
                    window.updateStatus("No configuration ID provided to remove", true);
                    return;
                }
                
                // Update status
                window.updateStatus("Removing configuration: " + configId + "...");
                
                // Get the server URL from properties
                String serverUrl = PropertiesUtil.getProperty("server.rest.url");
                String removeEndpoint = serverUrl + "/removeconfiguration/" + configId;
                
                // Create an HTTP client
                HttpClient httpClient = HttpClient.newHttpClient();
                
                // Create the request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(removeEndpoint))
                    .GET()
                    .build();
                
                // Send the request
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                // Check the response
                if (response.statusCode() != 200) {
                    throw new Exception("Error removing configuration: " + response.body());
                }
                
                window.updateStatus("Configuration removed successfully: " + configId);
                
            } catch (Exception e) {
                window.updateStatus("Error removing configuration: " + e.getMessage(), true);
            }
        });
    }
} 