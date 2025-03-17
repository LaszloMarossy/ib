package com.ibbe.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.Order;
import com.ibbe.entity.OrderBookPayload;
import com.ibbe.entity.PerformanceData;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.entity.TrendData;
import com.ibbe.executor.PerformanceTrader;
import com.ibbe.kafka.TradesConsumer;
import com.ibbe.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket endpoint for performance analysis.
 * Consumes Kafka messages and calculates statistics for a given configuration.
 */
@Component
public class PerformanceAnalysisEndpoint extends TextWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceAnalysisEndpoint.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Map to store active sessions and their associated executor services
    private static final Set<WebSocketSession> activeSessions = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, ExecutorService> sessionExecutors = new HashMap<>();
    private final Map<String, AtomicBoolean> sessionRunningFlags = new HashMap<>();
    
    // Sequence counter for performance data points
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    
    // Map to store consumer instances
    private final Map<String, TradesConsumer> sessionConsumers = new HashMap<>();
    
    /**
     * Handles incoming WS messages from PerformanceAnalysisClient on the FX side.
     * Expects a TradeConfig object as the message payload.
     * Start the Kafka consumer in a separate thread
     *
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            LOGGER.info("Received performance analysis request from client: {}", session.getId());
            
            // Add session to active sessions
            activeSessions.add(session);
            
            // Reset sequence counter for new client connection
            sequenceCounter.set(0);
            LOGGER.info("Reset sequence counter for new client connection");
            
            // Parse the trade configuration
            TradeConfig tradeConfig = objectMapper.readValue(message.getPayload(), TradeConfig.class);
            LOGGER.info("Analyzing performance for config - ID: {}, Ups: {}, Downs: {}", 
                    tradeConfig.getId(), tradeConfig.getUps(), tradeConfig.getDowns());
            
            // Create a running flag for this session
            String sessionId = session.getId();
            AtomicBoolean isRunning = new AtomicBoolean(true);
            sessionRunningFlags.put(sessionId, isRunning);
            
            // Create an executor service for this session
            ExecutorService executor = Executors.newSingleThreadExecutor();
            sessionExecutors.put(sessionId, executor);
            
            // Start the Kafka consumer in a separate thread
            executor.submit(() -> analyzeTradeConfigPerf(session, tradeConfig, isRunning));
            
        } catch (Exception e) {
            LOGGER.error("Error processing client message", e);
            sendErrorMessage(session, "Error processing request: " + e.getMessage());
        }
    }
    
    /**
     * Initiates the processing of all Trades stored in Kafka, by calling the TradesConsumer
     * defines and registers the MessageHandler for the Kafka message consumer
     * and sends performance data to the client.
     */
    private void analyzeTradeConfigPerf(WebSocketSession session, TradeConfig config, AtomicBoolean isRunning) {
        try {
            // Create a new consumer instance for this session to consume kafka records
            TradesConsumer sessionConsumer = new TradesConsumer();
            sessionConsumer.startConsumer();

            // objects to keep track of performance over many of the played back kafka trades
            final TrendData trendData = new TrendData();
            final PerformanceTrader trader = new PerformanceTrader(config);
            // set initial balances from config, but no latest price and recent trades!
            final FxTradesDisplayData fxTradesDisplayData = new FxTradesDisplayData(
                new BigDecimal(PropertiesUtil.getProperty("starting.bal.currency")),
                new BigDecimal(PropertiesUtil.getProperty("starting.bal.coin")),
                null, null);
            
            // Register message handler
            sessionConsumer.registerMessageHandler(trade -> {

                 /**
                 * The Historical trades/orderbook message processing logic comes here!!!
                 */
                if (!isRunning.get() || !session.isOpen()) {
                    return false; // Stop processing
                }
                
                try {
                    // set the current price for the potential pretend trade from the trade just coming in
                    // todo here potentially may use the nearest orderbook prices depending on whether buying or selling
                    fxTradesDisplayData.setLatestPrice(trade.getPrice());
                    // Extract orderbook data if available
                    OrderBookPayload orderBook = trade.getObp();
                    if (orderBook != null) {
                        // Calculate statistics
                        PerformanceData performanceData = calculatePerformanceData(trade, orderBook, config);
                        // tag along current balance data to keep track of profits
                        performanceData.setFxTradesDisplayData(fxTradesDisplayData);
                        // now calculate long and short term trends data
                        calculateTrends(trendData, performanceData, orderBook);
                        // here make trade decision based on configuration
                        trader.makeTradeDecision(trade, performanceData);

                        // Send data to client
                        String jsonData = objectMapper.writeValueAsString(performanceData);
                        session.sendMessage(new TextMessage(jsonData));
                    }
                    
                    return true; // Continue processing
                } catch (IOException e) {
                    LOGGER.error("Error sending performance data to client", e);
                    return false; // Stop processing on error
                }
            });
            
            // Store the consumer in a map for cleanup
            sessionConsumers.put(session.getId(), sessionConsumer);
            
            // Wait until session is closed
            while (isRunning.get() && session.isOpen()) {
                Thread.sleep(1000);
            }
            
            // Stop the consumer when done
            sessionConsumer.stopConsumer();
            
        } catch (Exception e) {
            LOGGER.error("Error processing Kafka messages", e);
            try {
                sendErrorMessage(session, "Error processing Kafka messages: " + e.getMessage());
            } catch (IOException ex) {
                LOGGER.error("Error sending error message to client", ex);
            }
        }
    }

    /**
     * enhance the PeformanceData object with trends data, redying it for trading decisions and FX display
     * get all info into place for making trade decisions
     */
    private void calculateTrends(TrendData trendData, PerformanceData performanceData, OrderBookPayload orderBook) {
        performanceData.updateTradePriceRelToBest(orderBook);
        performanceData.updateMovingAverages(trendData.getTradePricesQueue());
        performanceData.updateSumOfTrade(trendData.getLastPrice());
    }

    /**
     * Calculates performance data based on trade and orderbook data.
     *
     * Bt: the average price of the top 20 bid prices.
     * At: the average price of the top 20 ask prices.
     * QBt: the average amount of the top 20 bid amounts.
     * QAt: the average amount of the top 20 ask amounts.
     */
    private PerformanceData calculatePerformanceData(Trade trade, OrderBookPayload orderBook, TradeConfig config) {
        // Calculate average ask price and amount
        BigDecimal totalAskPrice = BigDecimal.ZERO;
        BigDecimal totalAskAmount = BigDecimal.ZERO;
        
        for (Order ask : orderBook.getAsks()) {
            totalAskPrice = totalAskPrice.add(ask.getP());
            totalAskAmount = totalAskAmount.add(ask.getA());
        }
        
        BigDecimal askCount = new BigDecimal(orderBook.getAsks().length);
        BigDecimal avgAskPrice = askCount.compareTo(BigDecimal.ZERO) > 0 
                ? totalAskPrice.divide(askCount, 2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;
        BigDecimal avgAskAmount = askCount.compareTo(BigDecimal.ZERO) > 0 
                ? totalAskAmount.divide(askCount, 4, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;
        
        // Calculate average bid price and amount
        BigDecimal totalBidPrice = BigDecimal.ZERO;
        BigDecimal totalBidAmount = BigDecimal.ZERO;
        
        for (Order bid : orderBook.getBids()) {
            totalBidPrice = totalBidPrice.add(bid.getP());
            totalBidAmount = totalBidAmount.add(bid.getA());
        }
        
        BigDecimal bidCount = new BigDecimal(orderBook.getBids().length);
        BigDecimal avgBidPrice = bidCount.compareTo(BigDecimal.ZERO) > 0 
                ? totalBidPrice.divide(bidCount, 2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;
        BigDecimal avgBidAmount = bidCount.compareTo(BigDecimal.ZERO) > 0 
                ? totalBidAmount.divide(bidCount, 4, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;

        // Polulate performance data object
        PerformanceData data = new PerformanceData();
        data.setSequence(sequenceCounter.getAndIncrement());
        data.setTradeId(trade.getTid());
        data.setTradePrice(trade.getPrice());
        data.setTradeAmount(trade.getAmount());
        data.setAvgAskPrice(avgAskPrice);
        data.setAvgAskAmount(avgAskAmount);
        data.setAvgBidPrice(avgBidPrice);
        data.setAvgBidAmount(avgBidAmount);
        data.setTimestamp(System.currentTimeMillis());

        return data;
    }
    
    /**
     * Sends an error message to the client.
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("error", true);
        errorData.put("message", errorMessage);
        
        String jsonError = objectMapper.writeValueAsString(errorData);
        session.sendMessage(new TextMessage(jsonError));
    }
    
    /**
     * Handles WebSocket connection closure.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LOGGER.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        
        // Remove session from active sessions
        activeSessions.remove(session);
        
        // Stop the running flag for this session
        String sessionId = session.getId();
        AtomicBoolean isRunning = sessionRunningFlags.get(sessionId);
        if (isRunning != null) {
            isRunning.set(false);
        }
        
        // Shutdown the executor service for this session
        ExecutorService executor = sessionExecutors.get(sessionId);
        if (executor != null) {
            executor.shutdown();
        }
        
        // Clean up
        sessionRunningFlags.remove(sessionId);
        sessionExecutors.remove(sessionId);
        
        // Stop and remove the consumer
        TradesConsumer consumer = sessionConsumers.remove(sessionId);
        if (consumer != null) {
            consumer.stopConsumer();
        }
    }
    
    /**
     * Handles WebSocket transport errors.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOGGER.error("WebSocket transport error: {}", exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }
} 