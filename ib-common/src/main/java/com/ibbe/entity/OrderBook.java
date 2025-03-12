package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * used by ChartWindow to display orderbook and trade dots
 */
public class OrderBook {
  boolean success;
  OrderBookPayload payload;
  Object[] trades = new Object[10];

  public OrderBook(@JsonProperty("success") boolean success,
                   @JsonProperty("payload") OrderBookPayload payload) {
    this.success = success;
    this.payload = payload;
  }

  public boolean isSuccess() {
    return success;
  }

  public OrderBookPayload getPayload() {
    return payload;
  }

  public void setTrades(Object[] trades) {
    this.trades = trades;
  }

  public Object[] getTrades() {
    return this.trades;
  }


}

