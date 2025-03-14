package com.ibbe.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.TradeConfig;
import com.ibbe.executor.LiveTrader;
import com.ibbe.executor.TraderFactory;
import com.ibbe.util.PropertiesUtil;
import jakarta.websocket.Session;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * websocket endpoint to serve the javafx ui with refreshed display info
 */
@Component
public class TradingMonitorEndpoint extends TextWebSocketHandler {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(TradingMonitorEndpoint.class.getName());
  private Session session;
  private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<Session>());
  private static boolean cont = true;
  private static int uniCounter = 0;
  private static final String interval = PropertiesUtil.getProperty("");
  private static ObjectMapper objectMapper = new ObjectMapper();

  // Map to store active sessions and their associated executor services
  private static final Set<WebSocketSession> activeSessions = Collections.synchronizedSet(new HashSet<>());
  private ExecutorService exe = Executors.newSingleThreadExecutor();
  private TradeConfig tradeConfig;
  private volatile boolean isRunning = true;

  @Autowired
  TraderFactory traderFactory;

  /**
   * keep sending trading display data for given TradingConfig
   * to the websocket client that sent the given configuration
   *
   * @param session
   * @param anyMessage should map to a TradeConfig
   */
  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage anyMessage) throws Exception {
    try {
      LOGGER.info("Received message from client: {}", session.getId());
      System.out.println("!!!!!&&&&!!!!!! " + anyMessage.getPayload());
      
      // Add session to active sessions
      activeSessions.add(session);
      
      // Parse the trade configuration
      tradeConfig = objectMapper.readValue(anyMessage.getPayload(), TradeConfig.class);
      
      // Create a new thread for sending data to this client
      exe.submit(() -> {
        try {
          while (isRunning && session.isOpen()) {
            try {
              Thread.sleep(1000);
              
              // Check if session is still open before sending
              if (!session.isOpen()) {
                LOGGER.info("Session closed, stopping data transmission: {}", session.getId());
                break;
              }
              
              FxTradesDisplayData fxTradesDisplayData = traderFactory.getTraderDisplayData(tradeConfig.getId());
              String displayString = objectMapper.writeValueAsString(fxTradesDisplayData);
              
              // Send data only if session is still open
              if (session.isOpen()) {
                session.sendMessage(new TextMessage(displayString));
              } else {
                LOGGER.info("Session closed during send operation: {}", session.getId());
                break;
              }
            } catch (IOException e) {
              // Check if this is a broken pipe or closed channel exception
              if (isBrokenPipeOrClosedChannel(e)) {
                LOGGER.info("Client disconnected: {}", session.getId());
                break;
              } else {
                LOGGER.error("Error sending message to client: {}", e.getMessage());
                e.printStackTrace();
              }
              break;
            } catch (Exception e) {
              LOGGER.error("Error in data transmission loop: {}", e.getMessage());
              e.printStackTrace();
              break;
            }
          }
        } catch (Exception e) {
          LOGGER.error("Error in WebSocket thread: {}", e.getMessage());
          e.printStackTrace();
        } finally {
          // Clean up when the loop exits
          try {
            if (session.isOpen()) {
              session.close();
            }
          } catch (IOException e) {
            LOGGER.error("Error closing session: {}", e.getMessage());
          }
          activeSessions.remove(session);
          LOGGER.info("WebSocket thread terminated for session: {}", session.getId());
        }
        return null;
      });
      
    } catch (Exception ex) {
      LOGGER.error("Error processing client message: {}", ex.getMessage());
      ex.printStackTrace();
    }
  }

  /**
   * Helper method to check if an exception is related to client disconnection
   */
  private boolean isBrokenPipeOrClosedChannel(Throwable t) {
    if (t == null) return false;
    
    // Check if this exception or any of its causes is a broken pipe or closed channel
    Throwable cause = t;
    while (cause != null) {
      if (cause.getMessage() != null && 
          (cause.getMessage().contains("Broken pipe") || 
           cause instanceof java.nio.channels.ClosedChannelException)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /**
   * the Websocket endpoint will be called when the user clicks on the Startup button of a given trading confituration
   *
   * @param session
   */
  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    LOGGER.info("WebSocket connection established: {}", session.getId());
    System.out.println("((((((((((((((((((((((((((((((((((((((((((( " + session.getId() + " open: " + session.isOpen());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable t) {
    LOGGER.info("Transport error on session: {}", session.getId());
    
    try {
      // Clean up resources for this session
      activeSessions.remove(session);
      
      t.printStackTrace();
      int count = 0;
      Throwable root = t;
      while (root.getCause() != null && count < 20) {
        root = root.getCause();
        count++;
      }
      
      if (isBrokenPipeOrClosedChannel(root)) {
        LOGGER.info("Client disconnected (transport error): {}", session.getId());
      } else if (!session.isOpen() && root instanceof IOException) {
        // IOException after close. Assume this is a variation of the user
        // closing their browser (or refreshing very quickly) and ignore it.
        LOGGER.info("IOException after session close: {}", session.getId());
      } else {
        LOGGER.error("WebSocket transport error: {}", t.getMessage());
      }
      
      // Try to close the session if it's still open
      if (session.isOpen()) {
        try {
          session.close();
        } catch (IOException e) {
          LOGGER.error("Error closing session after transport error: {}", e.getMessage());
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error handling transport error: {}", e.getMessage());
      e.printStackTrace();
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    LOGGER.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
    System.out.println("((((((((((((((((((((((((((((((((((( onClose " + session.getId() + " open: " + session.isOpen());
    
    // Clean up resources for this session
    activeSessions.remove(session);
  }
}
