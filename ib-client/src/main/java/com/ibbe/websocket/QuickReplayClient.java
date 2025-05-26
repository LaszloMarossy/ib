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
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
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
    
    // Incoming message queue for throttling processing
    private final Queue<PerformanceData> incomingMessageQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedMessageCount = new AtomicInteger(0);
    private static final int MAX_QUEUED_MESSAGES = 10000; // Safety limit to prevent memory issues
    
    // Complete dataset storage - thread-safe list with capacity for 50,000 records
    private final List<PerformanceData> completeDataset = Collections.synchronizedList(new ArrayList<>(50000));
    private final AtomicInteger totalRecordsReceived = new AtomicInteger(0);
    
    // Track the last used configuration ID
    private String currentConfigId = null;
    
    // List to store accumulated chunks
    private final List<ChunkInfo> accumulatedChunks = Collections.synchronizedList(new ArrayList<>());
    
    // Set to track processed chunk IDs to prevent duplicates
    private final Set<Integer> processedChunkIds = Collections.synchronizedSet(new HashSet<>());
    
    // Running total of all chunk profits
    private final AtomicReference<BigDecimal> totalChunkProfit = new AtomicReference<>(BigDecimal.ZERO);
    
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
     * Starts the performance analysis by connecting to the server with a criteria string.
     * This method is used by the QuickReplayWindow.
     * 
     * @param ups The ups value for the configuration
     * @param downs The downs value for the configuration
     * @param criteriaStr Comma-separated string of criteria names to use
     * @param configId The configuration ID to use
     */
    public void startPerformanceAnalysis(String ups, String downs, String criteriaStr, String configId) {
        // Parse the criteria string into boolean values
        boolean useAvgBidVsAvgAsk = criteriaStr.contains("avgBidVsAvgAsk");
        boolean useShortVsLongMovAvg = criteriaStr.contains("shortVsLongMovAvg");
        boolean useSumAmtUpVsDown = criteriaStr.contains("sumAmtUpVsDown");
        boolean useTradePriceCloserToAskVsBuy = criteriaStr.contains("tradePriceCloserToAskVsBuy");
        
        // Call the main method with the parsed criteria and the provided configId
        startPerformanceAnalysis(ups, downs, 
                              useAvgBidVsAvgAsk,
                              useShortVsLongMovAvg,
                              useSumAmtUpVsDown,
                              useTradePriceCloserToAskVsBuy,
                              configId);
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
        // Generate a random configId and call the method that takes a configId
        startPerformanceAnalysis(ups, downs,
                              useAvgBidVsAvgAsk,
                              useShortVsLongMovAvg,
                              useSumAmtUpVsDown,
                              useTradePriceCloserToAskVsBuy,
                              RandomString.getRandomString());
    }
    
    /**
     * Starts the performance analysis by connecting to the server with expanded criteria options and a specific configId.
     * 
     * @param ups The ups value for the configuration
     * @param downs The downs value for the configuration
     * @param useAvgBidVsAvgAsk Whether to use average bid vs average ask in trading decisions
     * @param useShortVsLongMovAvg Whether to use short vs long moving average in trading decisions
     * @param useSumAmtUpVsDown Whether to use sum amount up vs down in trading decisions
     * @param useTradePriceCloserToAskVsBuy Whether to use trade price closer to ask vs buy in trading decisions
     * @param configId The configuration ID to use
     */
    public void startPerformanceAnalysis(String ups, String downs, 
                                       boolean useAvgBidVsAvgAsk,
                                       boolean useShortVsLongMovAvg,
                                       boolean useSumAmtUpVsDown,
                                       boolean useTradePriceCloserToAskVsBuy,
                                       String configId) {
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
                accumulatedChunks.clear(); // Clear accumulated chunks for new analysis
                processedChunkIds.clear(); // Clear processed chunk IDs
                totalChunkProfit.set(BigDecimal.ZERO); // Reset total chunk profit
                
                // Stop any existing processing
                processingActive.set(false);
                
                // Create a new WebSocket client
                WebSocketClient client = new StandardWebSocketClient();
                
                // Build the WebSocket URL
                String wsUrl = PropertiesUtil.getProperty("server.ws.url");
                String performanceAnalysisEndpoint = wsUrl.replace("/websocket", "/performanceanalysis");
                
                // Save the current configuration ID
                this.currentConfigId = configId;
                
                // First, create the TradeConfig using the REST POST endpoint
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
     * Handles incoming WebSocket messages.
     * Queues messages for throttled processing instead of processing immediately.
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            
            // Try to deserialize the message directly into a PerformanceData object
            try {
                PerformanceData data = objectMapper.readValue(payload, PerformanceData.class);
                processDataPoint(data);
//                if (data != null) {
//                    // Add to queue for throttled processing instead of processing immediately
//                    // Only keep up to MAX_QUEUED_MESSAGES to prevent memory issues
//                    if (queuedMessageCount.get() < MAX_QUEUED_MESSAGES) {
//                        incomingMessageQueue.add(data);
//                        queuedMessageCount.incrementAndGet();
//
//                        // Ensure processing is active to handle the queue
//                        if (!processingActive.get()) {
//                            startProcessingData();
//                        }
//                    } else {
//                        // Queue is full, log warning and drop the message
//                        System.err.println("WARNING: Message queue full, dropping message to prevent memory issues");
//                    }
//
//                    // Increment the total records counter regardless of processing
//                    totalRecordsReceived.incrementAndGet();
//                }
            } catch (Exception e) {
                System.err.println("Error parsing WebSocket message: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Error processing data point: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Starts processing data from the queue efficiently.
     * Uses batching to reduce UI updates and improve performance.
     * todo take out?
     */
    private void startProcessingData() {
        if (chartUpdater != null && !chartUpdater.isShutdown()) {
            chartUpdater.shutdownNow();
        }
        
        System.out.println("Starting data processing thread...");
        chartUpdater = Executors.newSingleThreadScheduledExecutor();
        
        // Set processing active flag
        processingActive.set(true);
        
        // Schedule periodic memory usage check
        ScheduledExecutorService memoryMonitor = Executors.newSingleThreadScheduledExecutor();
        memoryMonitor.scheduleAtFixedRate(() -> {
            checkMemoryUsage();
        }, 10, 10, TimeUnit.SECONDS);
        
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
                        return; // Don't attempt reconnection automatically
                    }
                }
                
                // Process a batch of messages from the incoming message queue
                int messagesToProcess = Math.min(10, incomingMessageQueue.size());
                List<PerformanceData> batch = new ArrayList<>(messagesToProcess);
                
                // Process a batch of messages from the queue
                for (int i = 0; i < messagesToProcess; i++) {
                    PerformanceData data = incomingMessageQueue.poll();
                    if (data == null) break;
                    
                    queuedMessageCount.decrementAndGet();
                    processDataPoint(data);
                    batch.add(data);
                }
                
                // Log queue status occasionally
                if (totalRecordsReceived.get() % 1000 == 0) {
                    System.out.println("Queue status: " + queuedMessageCount.get() + " messages waiting");
                }
                
                // Update UI with batch if not empty
                if (!batch.isEmpty()) {
                    // Update the UI with the batch of data
                    updateUIWithBatch(batch);
                    
                    // Add a small delay after each batch to reduce CPU usage
                    try {
                        Thread.sleep(20);  // 20ms delay - increased from 10ms
                    } catch (InterruptedException e) {
                        // Ignore interruption
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing data: " + e.getMessage());
                e.printStackTrace();
                // Don't let the thread die due to errors
                if (session != null && session.isOpen()) {
                    System.out.println("Encountered error but session is active, continuing processing");
                }
            }
        }, 0, 150, TimeUnit.MILLISECONDS); // 150ms interval for smooth UI updates
        
        System.out.println("Data processing thread started successfully");
    }
    
    /**
     * Processes a single data point, handling chunks and trades.
     */
    private void processDataPoint(PerformanceData data) {
        // Process new completed chunk
        if (data.getNewCompletedChunk() != null) {
            ChunkInfo newChunk = data.getNewCompletedChunk();
            
            // Thread-safe check to prevent duplicate processing
//            if (!processedChunkIds.contains(newChunk.getChunkNumber())) {
            processedChunkIds.add(newChunk.getChunkNumber());
                // Add new chunks to the beginning of the list (newest first)
            accumulatedChunks.addFirst(newChunk);
                
                // Update the running total of chunk profits
            BigDecimal currentTotal = totalChunkProfit.get();
            totalChunkProfit.set(currentTotal.add(newChunk.getProfit()));
                
                // Only log important events like new chunks
            System.out.println("Received new completed chunk #" + newChunk.getChunkNumber());

        }
        
        // Process pretend trade if present
        if (data.getPretendTrade() != null) {
            // For QuickReplayMode (mode == 2), only keep track of pretend trades
            if (window.getMode() == 2) {
                synchronized (completeDataset) {
                    // Add the new data with pretend trade to the dataset
                    completeDataset.add(data);
                    
                    // If over the limit, remove the oldest entries
                    if (completeDataset.size() > MAX_RECORDS) {
                        completeDataset.remove(0);
                    }
                }
                
                // Explicitly tell the window to update when we have a new pretend trade
                window.onNewDataAvailable();
            } else {
                // For visual mode, add to the dataset as usual
                addToCompleteDataset(data);
            }
        }
        
        // Process balance updates
        if (data.getFxTradesDisplayData() != null) {
            FxTradesDisplayData displayData = data.getFxTradesDisplayData();
            // Update balance display on window 
            if (displayData.getCurrencyBalance() != null && 
                displayData.getCoinBalance() != null && 
                displayData.getProfit() != null) {
                
                window.updateBalanceDisplay(
                    displayData.getCurrencyBalance(),
                    displayData.getCoinBalance(),
                    displayData.getProfit()
                );
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
                
                // Get the total profit from all accumulated chunks
                BigDecimal chunkProfit = getTotalChunkProfit();
                BigDecimal tradeProfit = displayData.getProfit();
                
                // For now, we'll only use the trade profit from the server, not chunk profit
                // This avoids double-counting as the server-side profit already accounts for chunks
                BigDecimal totalProfit = tradeProfit;
                
                // Need to create a final variable for use in the lambda
                final BigDecimal finalTotalProfit = totalProfit;
                Platform.runLater(() -> {
                    try {
                        window.updateBalanceDisplay(
                            displayData.getCurrencyBalance(),
                            displayData.getCoinBalance(),
                            finalTotalProfit
                        );
                        
                        // Update trade statistics if window is QuickReplayWindow
                        if (window instanceof com.ibbe.fx.QuickReplayWindow) {
                            ((com.ibbe.fx.QuickReplayWindow) window).updateTradeStatistics(
                                totalRecordsReceived.get(), 
                                getPretendTradeCount()
                            );
                        }
                    } catch (Exception e) {
                        System.err.println("Error updating balance and profit: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        }
        
        // Check if we have any pretend trades in this batch
        long pretendTradeCount = batch.stream()
            .filter(p -> p.getPretendTrade() != null)
            .count();
        
        // Notify the window only if we have processed relevant data
        if (pretendTradeCount > 0 || batch.stream().anyMatch(p -> p.getNewCompletedChunk() != null)) {
            Platform.runLater(() -> {
                try {
                    // Notify the window that new data is available
                    window.onNewDataAvailable();
                    
                    // Update trade statistics if window is QuickReplayWindow
                    if (window instanceof com.ibbe.fx.QuickReplayWindow) {
                        ((com.ibbe.fx.QuickReplayWindow) window).updateTradeStatistics(
                            totalRecordsReceived.get(), 
                            getPretendTradeCount()
                        );
                    }
                } catch (Exception e) {
                    System.err.println("Error updating UI: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
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
    
    /**
     * Gets the accumulated chunks for display in the UI.
     * 
     * @return A copy of the accumulated chunks list
     */
    public List<ChunkInfo> getAccumulatedChunks() {
        return new ArrayList<>(accumulatedChunks);
    }
    
    /**
     * Gets the total profit from all accumulated chunks.
     * Returns the precomputed running total rather than calculating on demand.
     * 
     * @return The sum of profits from all accumulated chunks
     */
    public BigDecimal getTotalChunkProfit() {
        return totalChunkProfit.get();
    }
    
    /**
     * Gets the current active chunk, if any.
     * 
     * @return The current active chunk, or null if no active chunk
     */
    public ChunkInfo getCurrentChunk() {
        // The current chunk must be extracted from the latest data point
        synchronized(completeDataset) {
            if (completeDataset.isEmpty()) {
                return null;
            }
            
            PerformanceData latestData = completeDataset.get(completeDataset.size() - 1);
            return latestData != null ? latestData.getCurrentChunk() : null;
        }
    }
    
    /**
     * Checks memory usage and performs cleanup if necessary
     */
    private void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usedPercentage = (double) usedMemory / maxMemory * 100;
        
        // Log memory usage periodically instead of randomly
        int totalRecords = totalRecordsReceived.get();
        if (totalRecords % 5000 == 0) {  // Reduced frequency - only log every 5000 records
            System.out.println("Memory usage: " + String.format("%.2f", usedPercentage) + "% (" + 
                (usedMemory / (1024 * 1024)) + "MB / " + (maxMemory / (1024 * 1024)) + "MB)");
        }
        
        // If memory usage is high, perform cleanup - lowered threshold for earlier cleanup
        if (usedPercentage > 60) {
            System.out.println("WARNING: High memory usage detected (" + 
                String.format("%.2f", usedPercentage) + "%), performing cleanup...");
            cleanupMemory();
        }
    }
    
    /**
     * Performs memory cleanup to prevent OutOfMemoryError
     */
    private void cleanupMemory() {
        synchronized (completeDataset) {
            // If we have a large dataset, trim it down significantly
            if (completeDataset.size() > 5000) {
                int keepCount = 2000;  // Keep the most recent 2000 entries
                int removeCount = completeDataset.size() - keepCount;
                
                System.out.println("Trimming dataset from " + completeDataset.size() + 
                    " to " + keepCount + " entries");
                
                // Remove older entries
                completeDataset.subList(0, removeCount).clear();
            }
        }
        
        // Force garbage collection
        System.gc();
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
} 