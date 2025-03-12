package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Corresponds to the payload structure of the incoming Bitso Trades Websocket message; extends the base class that is
 * used within the ib app
 * @see BitsoTradesQueuerWsClient
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeWs extends Trade {


  public TradeWs(@JsonProperty("i") Long tid,
                 @JsonProperty("x") String createdAt,
                 @JsonProperty("a") BigDecimal amount,
                 @JsonProperty("r") BigDecimal price,
                 @JsonProperty("v") BigDecimal value,
                 @JsonProperty("mo") String mo,
                 @JsonProperty("to") String to,
                 @JsonProperty("t") String makerSide ) {
    // Handle null createdAt value
    if (createdAt != null) {
      ZonedDateTime zonedDateTime = Instant.ofEpochMilli(Long.parseLong(createdAt)).atZone(ZoneOffset.UTC);
      this.createdAt = zonedDateTime.toString();
    } else {
      // Set current time as fallback
      this.createdAt = ZonedDateTime.now(ZoneOffset.UTC).toString();
    }
    
    this.amount = amount;
    this.makerSide = (makerSide != null && makerSide.equals("0")) ? "sell" : "buy";
    this.price = price;
    this.tid = tid;
  }

  public TradeWs(Trade trade) {
    super(trade);
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

}
