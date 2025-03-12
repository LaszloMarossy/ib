package com.ibbe.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.BitsoDataAggregator;
import com.ibbe.entity.TradeWs;
import com.ibbe.entity.TradesWs;
import com.ibbe.kafka.TradesProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 *
 * server side WS client receiving the Bitso websocket trades
 * Asynchronous parallel executor populating BitsoDataAggregator queues
 */
@Component
public class BitsoTradesQueuerWsClient
    extends TextWebSocketHandler
    implements ApplicationListener<ApplicationReadyEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BitsoTradesQueuerWsClient.class);
  // declaring this here vs. the base class as then there would be only a single thread pool for populate and consume
  private ExecutorService exe;
  private boolean isSubmitted = false;
  private final ObjectMapper objectMapper = new ObjectMapper();
//  private WebSocketSession session;
  @Value("${bitso.ws.url}")
  private String wsUrl;
  @Autowired
  private BitsoDataAggregator bitsoDataAggregator;
  @Autowired
  private TradesProducer tradesProducer;


  public BitsoTradesQueuerWsClient() {
    exe = Executors.newFixedThreadPool(3);
  }

  /**
   * called after boot startup
   * @param event
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    startAsyncPopulateTradeQueue();
  }


  /**
   * startup method called by onApplicationEvent to connect to Bitso WS endpoint for Trades
   *
   * @return result string - ignored for now
   */
  public void startAsyncPopulateTradeQueue() {
    if (!isSubmitted) {
      Callable<String> call = () -> {
        LOGGER.info("connecting to Bitso WS endpoint ");
        try {
          WebSocketClient client = new StandardWebSocketClient();
          WebSocketSession session = client.doHandshake(this, wsUrl).get();
          session.sendMessage(new TextMessage("{ \"action\": \"subscribe\", \"book\": \"btc_mxn\", \"type\": \"trades\" }"));
        } catch (Exception e) {
          e.printStackTrace();
          throw new RuntimeException(e);
        }
        return "Connected to Bitso Websocket";
      };
      exe.submit(call);
      isSubmitted = true;
      LOGGER.info("startAsyncPopulateTradeQueue started");
    } else {
      LOGGER.info("startAsyncPopulateTradeQueue was already started");
    }
  }

  /**
   * Bitso Websocket callback method for each trade message
   * for each trade received
   * - enhance object for internal use
   * - add to the BitsoDataAggregator trades queue
   * - send to Kafka for playback together with the orderbook
   *
   */
  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage anyMessage) throws Exception {
//  @OnMessage
//  public void onMessage(String incoming) {
    try {
      TradesWs tradesWs = objectMapper.readValue(anyMessage.getPayload(), TradesWs.class);
      // check if it is not a keep alive message
      if (tradesWs.isTrade() && tradesWs.getPayload() != null) {
        TradeWs tradeWs = tradesWs.getPayload()[0];
        //multiply TID by 10 so pretend trades can be inserted in between
        tradeWs.setTid(tradeWs.getTid() * 10);
        // internalize tradeWs object preparing it for use in ib app
        bitsoDataAggregator.internalizeBitsoTradeWs(tradeWs);
        // add to internal queue for live monitoring
        bitsoDataAggregator.addInternalizedTradeWs(tradeWs);
        // send trade (along with current orderbook) to kafka
        tradesProducer.saveToKafka(tradeWs);
        // make sure the orderbook is not tagged to the trades after it was sent to Kafka..
        // This is now handled in the saveToKafka method
      }
//      Thread.sleep(100);
    } catch (Exception e) {
      LOGGER.error("DID NOT PROCESS TRADE {}", anyMessage.getPayload());
      e.printStackTrace();
    }
  }


  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
//  @OnClose
//  public void onClose(Session session, CloseReason reason) {
    try {
      LOGGER.error("CLOSING WEBSOCKET - {} - attempting to restart: ", status.getReason());
      isSubmitted = false;
      if (session.isOpen())
        session.close();
      startAsyncPopulateTradeQueue();
      LOGGER.error("RESTARTED WEBSOCKET ");
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
//  @OnError
//  public void onError(Session session, Throwable t) {
    try {
      LOGGER.error("ERROR IN WEBSOCKET");
      exception.printStackTrace();
      session.close();
    } catch (IOException ex) {
      ex.printStackTrace();
      System.exit(-1);
    }
  }

}
