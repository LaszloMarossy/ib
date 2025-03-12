package com.ibbe.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.OrderBook;
import com.ibbe.fx.ChartWindow;
import com.ibbe.fx.TradingWindow;
import com.ibbe.util.PropertiesUtil;
import javafx.application.Platform;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;

/**
 * websocket client used by the JavaFX window for getting Orderbook info
 * (trades might not happen while orderbook is getting updates...)
 */
public class OrderbookWsClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TradingWindow tradingWindow;
    private ChartWindow chartWindow;
    private StompSession currentSession;

    public OrderbookWsClient(TradingWindow tradingWindow) {
        this.tradingWindow = tradingWindow;
    }

    public OrderbookWsClient(ChartWindow chartWindow) {
        this.chartWindow = chartWindow;
    }

    public void subscribeToOrderbookMonitoring() {
        try {
            // Close existing session if it exists
            if (currentSession != null && currentSession.isConnected()) {
                try {
                    currentSession.disconnect();
                    System.out.println("Closed existing Orderbook WebSocket connection");
                } catch (Exception e) {
                    System.out.println("Error closing existing Orderbook WebSocket connection: " + e.getMessage());
                }
            }
            
            String wsUrl = PropertiesUtil.getProperty("server.ws.url");
            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketStompClient stompClient = new WebSocketStompClient(client);
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());
            
            // Increase buffer size limit
            stompClient.setDefaultHeartbeat(new long[]{0, 0});
            stompClient.setTaskScheduler(new ConcurrentTaskScheduler());
            stompClient.setInboundMessageSizeLimit(1024 * 1024); // 1MB buffer size

            currentSession = stompClient.connect(wsUrl, new StompSessionHandlerAdapter() {
            }).get();

            currentSession.subscribe("/topic/orderbook", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return OrderBook.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    final OrderBook orderBook = (OrderBook) payload;
                    // Use Platform.runLater to ensure UI updates happen on the JavaFX Application Thread
                    Platform.runLater(() -> {
                        if (chartWindow != null) {
                            chartWindow.displayOrderbookData(orderBook);
                        } else if (tradingWindow != null) {
                            tradingWindow.updateOrderTables(orderBook.getPayload().getAsks(), orderBook.getPayload().getBids());
                        }
                    });
                }
            });

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
