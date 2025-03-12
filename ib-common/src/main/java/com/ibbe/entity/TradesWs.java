package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

/**
 * Bitso Trades Websocket message is to be read into this object
 * @see BitsoTradesQueuerWsClient
 */
public class TradesWs {
  private String type;
  private TradeWs[] payload;
  private String book;
  private String sent;
  private static final String tradesType = "trades";

  public TradesWs(@JsonProperty("type") String type,
                  @JsonProperty("payload") TradeWs[] payload,
                  @JsonProperty("book") String book,
                  @JsonProperty("action") String action,
                  @JsonProperty("response") String response,
                  @JsonProperty("time") String time,
                  @JsonProperty("sent") String sent) {
    this.type = type;
    this.payload = payload;
    this.book = book;
    this.sent = sent;
  }

  public boolean isTrade() {
    return type != null && type.equals(tradesType);
  }

  public TradeWs[] getPayload() {
    return payload;
  }

  public static String printTrades(String prefix, Object[] trades) {
    StringBuilder tradeIds = new StringBuilder(prefix);
    // handle new trades and identify new ones to be processed (some of the most recent trades are likely old)
    Arrays.stream(trades).forEach(t -> {
      tradeIds.append(" " + ((TradeWs) t).getTid());
    });
    return tradeIds.toString();
  }

}

