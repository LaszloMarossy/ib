package com.ibbe.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.OrderBookPayload;
import com.ibbe.entity.PerformanceData;
import com.ibbe.entity.TradeConfig;
import com.ibbe.executor.BasicTrader;
import com.ibbe.kafka.TradesConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
            final BasicTrader trader = new BasicTrader(config);

            // Register message handler
            sessionConsumer.registerMessageHandler(trade -> {

                 /**
                 * The Historical trades/orderbook message processing logic comes here!!!
                 */
                if (!isRunning.get() || !session.isOpen()) {
                    return false; // Stop processing
                }
                
                try {
//                    FxTradesDisplayData fxTradesDisplayData = getFxTradesDisplayData(
//                        trade, trader.getCurrencyBalance(), trader.getCoinBalance());
                    // Extract orderbook data if available
                    OrderBookPayload orderBook = trade.getObp();
                    if (orderBook != null) {
                        // here make trade decision based on configuration
                        PerformanceData performanceData = trader.makeTradeDecision(trade, orderBook);

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