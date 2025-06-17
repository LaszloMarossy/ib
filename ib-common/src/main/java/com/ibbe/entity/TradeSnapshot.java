package com.ibbe.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


/**
 * Entity class representing a Kafka trade event; to serve as a performance analysis.
 * Used for Webservice communication between PerformanceAnalysisEndpoint server and the FX window client WS clients.
 * Also, amounts of type double for
 * - STMAPrice (short term moving average of price),
 * - LTMAPrice (long term moving average of price),
 * - SAUp (sum of trade amounts where price of t > price of t-1),
 * - SADown(sum of trade amounts where price of t < price of t-1) (SAUp > SADown contributes to buy signal, and SADown >
 * SAUp contributes to sell signal)
 * <p>
 * And finally a double type for
 * - priceCloserToBestAsk (position relative to averages)
 * indicating whether the trade price is closer to the bid price (favoring buy)
 * than to the ask price (favoring sell) (to be used with the best bid and ask price)
 */
public class TradeSnapshot {

  @Setter
  @Getter
  private int sequence;
  @Setter
  @Getter
  public double tradePrice;
  @Setter
  @Getter
  public double tradeAmount;
  @Setter
  @Getter
  public double avgAskPrice;
  @Setter
  @Getter
  public double avgAskAmount;
  @Setter
  @Getter
  public double avgBidPrice;
  @Getter
  public double avgBidAmount;
  @Setter
  @Getter
  private String timestamp;
  @Setter
  @Getter
  private Long tradeId;
  @Setter
  @Getter
  private boolean amountMissing;
  // Moving averages
  @Setter
  @Getter
  public double STMAPrice; // Short term moving average of price
  @Setter
  @Getter
  public double LTMAPrice; // Long term moving average of price
  // Sum of amounts for price movements
  @Setter
  @Getter
  public double tradeAmountIncrease;    // Sum of trade amounts in last N trades where price of t > price of t-1
  @Setter
  @Getter
  public double tradeAmountDecrease;  // Sum of trade amounts in last N trades where price of t < price of t-1
  // trade price relative to best bid/ask prices - positive means closer to ask; negative closer to bid
  @Getter
  public double priceCloserToBestAsk;
  // if a trade occurs by the PerformanceTrader bc of its configuration, then this represents it
  @Setter
  @Getter
  private Trade pretendTrade;
  @Setter
  @Getter
  private BigDecimal currencyBalance;
  //    this.profit = calculateProfit();
  @Setter
  @Getter
  private BigDecimal coinBalance;
  @Setter
  @Getter
  private BigDecimal latestPrice;
  // carried for each trade, this indicates the account value in the current chunk
  // this means that this is always based on the chunk balances in effect
  @Setter
  @Getter
  private BigDecimal accountValueInChunk;
  // A newly completed chunk to be sent to clients
  @Setter
  @Getter
  private ChunkInfo completedChunk;
  // Current chunk information
  private ChunkInfo currentChunk;

  // Default constructor
  public TradeSnapshot() {
    // short/long term moving average
    this.STMAPrice = 0.0;
    this.LTMAPrice = 0.0;
    // sum of amounts up/down
    this.tradeAmountIncrease = 0.0;
    this.tradeAmountDecrease = 0.0;
    // position relative to averages
    this.priceCloserToBestAsk = 0.0;

  }


  public void setAvgBidAmount(BigDecimal avgBidAmount) {
    this.avgBidAmount = avgBidAmount != null ? avgBidAmount.doubleValue() : 0;
  }


  /**
   * Sets the price closer to best ask value from a BigDecimal.
   *
   * @param value the value to set
   */
  public void setPriceCloserToBestAsk(BigDecimal value) {
    if (value != null) {
      this.priceCloserToBestAsk = value.doubleValue();
    }
  }

}