package com.ibbe.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.TradeConfig;
import com.ibbe.fx.TradingWindow;
import com.ibbe.util.PropertiesUtil;
import javafx.application.Platform;

import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;


/**
 * WebSocket client used by the JavaFX window for getting trade-related data.
 * This class extends FxWsClient to inherit WebSocket handling capabilities.
 */
//@ClientEndpoint
public class TradeDataWsClient {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private TradingWindow tradingWindow;
  private WebSocketSession webSocketSession;


  public TradeDataWsClient(TradingWindow tradingWindow) {
    this.tradingWindow = tradingWindow;
  }

  /**
   * Subscribes to monitor the specified trade configuration executor.
   * @param tradeConfig The trade configuration to be sent.
   */
  public void subscribeToTradeConfigMonitoring(TradeConfig tradeConfig) {
    try {
      // Close existing WebSocket session if it exists
      if (webSocketSession != null && webSocketSession.isOpen()) {
        try {
          webSocketSession.close();
          System.out.println("Closed existing WebSocket connection");
        } catch (Exception e) {
          System.out.println("Error closing existing WebSocket connection: " + e.getMessage());
        }
      }
      
      String wsUrl = PropertiesUtil.getProperty("server.ws.url");
      String tradingMonitorEndpoint = wsUrl.replace("/websocket", "/tradingconfigmonitor");
      System.out.println("Connecting to WebSocket URL: " + tradingMonitorEndpoint);
      System.out.println("Subscribing to trade config: " + tradeConfig.getId());
      
      WebSocketClient client = new StandardWebSocketClient();
      webSocketSession = client.doHandshake(new TextWebSocketHandler() {
        @Override
        public void handleTextMessage(WebSocketSession session, TextMessage message) {
          try {
            final FxTradesDisplayData fxTradesDisplayData = objectMapper.readValue(message.getPayload(), FxTradesDisplayData.class);
            // Use Platform.runLater to ensure UI updates happen on the JavaFX Application Thread
            Platform.runLater(() -> {
              if (tradingWindow != null) {
                tradingWindow.displayTradesData(fxTradesDisplayData);
                // We no longer need to change button text as the button is removed
              }
            });
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }, new WebSocketHttpHeaders(), URI.create(tradingMonitorEndpoint)).get();
      
      // Send the trade configuration to the server
      String tradeConfigJson = objectMapper.writeValueAsString(tradeConfig);
      webSocketSession.sendMessage(new TextMessage(tradeConfigJson));
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error connecting to WebSocket: " + e.getMessage());
    }
  }
}
