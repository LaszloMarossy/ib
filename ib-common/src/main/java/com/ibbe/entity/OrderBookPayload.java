package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.sql.Timestamp;

public class OrderBookPayload {
  private Order[] asks;
  private Order[] bids;
  private Timestamp updated_at;
  private long sequence;

  public OrderBookPayload(@JsonProperty("trades") Order[] asks,
                          @JsonProperty("bids") Order[] bids,
                          @JsonProperty("updated_at") Timestamp updated_at,
                          @JsonProperty("sequence") long sequence) {
    this.asks = asks;
    this.bids = bids;
    this.updated_at = updated_at;
    this.sequence = sequence;
  }

  public Order[] getAsks() {
    return asks;
  }

  public Order[] getBids() {
    return bids;
  }

  public Timestamp getUpdated_at() {
    return updated_at;
  }

  public long getSequence() {
    return sequence;
  }
}

