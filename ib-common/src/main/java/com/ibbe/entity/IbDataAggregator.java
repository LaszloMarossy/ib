package com.ibbe.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.LinkedBlockingDeque;

public class IbDataAggregator {
  private static Logger LOGGER = LoggerFactory.getLogger(IbDataAggregator.class.getName());

  protected LinkedBlockingDeque<TradeWs> internalizedTrades = new LinkedBlockingDeque<>();

  protected Trade previousBitsoTrade = null;

  @Value("${poller.interval.xchrate}")
  protected int internalTradesQueueSize;

  // the current snapshot of the orderbook; being shipped to Kafka with each trade
  protected OrderBookPayload orderBookPayload;



  /**
   * add an element to the last position of the deque of trades reveived via websocket and send to event monitors (in
   * this case the LiveTrader
   *
   * @param tradeWs TradeWs to be added
   *
   *
   */
  public void addInternalizedTradeWs(TradeWs tradeWs) {

    boolean success = false;
    // add tradeWs to internal queue (maintains the last X number of trades)
    while (!success) {
      success = internalizedTrades.offerLast(tradeWs);
    }
    if (internalizedTrades.size() > internalTradesQueueSize) {
      try {
        internalizedTrades.takeFirst();
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
  }

  public Object[] getRecentTrades() {
    return internalizedTrades.toArray();
  }


  public void setOrderBookPayload(OrderBookPayload obp) {
    orderBookPayload = obp;
  }

  public OrderBookPayload getOrderbookPayload() {
    return orderBookPayload;
  }



}
