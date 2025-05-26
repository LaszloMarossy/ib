package com.ibbe.entity;

import com.ibbe.util.PropertiesUtil;

import java.math.BigDecimal;
import java.util.Deque;

/**
 * Entity class representing a Kafka trade event; to serve as a performance analysis.
 * Used for Webservice communication between PerformanceAnalysisEndpoint server and the FX window client WS clients.
 * Also, amounts of type double for
 * - STMAPrice (short term moving average of price),
 * - LTMAPrice (long term moving average of price),
 * - SAUp (sum of trade amounts where price of t > price of t-1),
 * - SADown(sum of trade amounts where price of t < price of t-1) (SAUp > SADown contributes to buy signal, and SADown >
 *   SAUp contributes to sell signal)
 *
 * And finally a double type for
 * - priceCloserToBestAsk (position relative to averages)
 * indicating whether the trade price is closer to the bid price (favoring buy)
 * than to the ask price (favoring sell) (to be used with the best bid and ask price)
 */
public class TradeSnapshot {
    private int sequence;
    public double tradePrice;
    public double tradeAmount;
    public double avgAskPrice;
    public double avgAskAmount;
    public double avgBidPrice;
    public double avgBidAmount;
    private String timestamp;
    private Long tradeId;
    private boolean amountMissing;

    // Moving averages
    public double STMAPrice; // Short term moving average of price
    public double LTMAPrice; // Long term moving average of price

    // Sum of amounts for price movements
    public double SAUp;    // Sum of trade amounts where price of t > price of t-1
    public double SADown;  // Sum of trade amounts where price of t < price of t-1

    // trade price relative to best bid/ask prices - positive means closer to ask; negative closer to bid
    public double priceCloserToBestAsk;

    // if a trade occurs by the PerformanceTrader bc of its configuration, then this represents it
    private Trade pretendTrade;

    // to keep balances and profit
//    private FxTradesDisplayData fxTradesDisplayData;
    // fields brought over from FxTradesDisplayData
    private BigDecimal currencyBalance;
    private BigDecimal coinBalance;
    private BigDecimal latestPrice;
    // carried for each trade, this indicates the account value in the current chunk
    // this means that this is always based on the chunk balances in effect
    private BigDecimal accountValueInChunk;
    
    // A newly completed chunk to be sent to clients
    private ChunkInfo completedChunk;
    // Current chunk information
    private ChunkInfo currentChunk;
    // keeping configured constants for long/short term moving average calc
    public int ltma, stma;

    // Default constructor
    public TradeSnapshot() {
        // short/long term moving average
        this.STMAPrice = 0.0;
        this.LTMAPrice = 0.0;
        // sum of amounts up/down
        this.SAUp = 0.0;
        this.SADown = 0.0;
        // position relative to averages
        this.priceCloserToBestAsk = 0.0;

        // get from config what is long vs short term
        String stmaStr = PropertiesUtil.getProperty("stma");
        String ltmaStr = PropertiesUtil.getProperty("ltma");
        
        // Default to 5 for stma and 20 for ltma if properties are not set
        this.stma = stmaStr != null ? Integer.parseInt(stmaStr) : 5;
        this.ltma = ltmaStr != null ? Integer.parseInt(ltmaStr) : 20;

    }


    /**
     * Updates the trade price relative to best bid, and best ask prices
     * - Positive values indicate closer to bid (buy signal)
     * - Negative values indicate closer to ask (sell signal)
     *
     * @param orderBook containing the asks and bids array
     */
    public void updateTradePriceRelToBest(OrderBookPayload orderBook) {
        if (orderBook == null || orderBook.getBids() == null || orderBook.getAsks() == null 
        || orderBook.getBids().length == 0 || orderBook.getAsks().length == 0) {
            // If orderbook is null or empty, set a neutral value
            this.priceCloserToBestAsk = 0.0;
            return;
        }

        // Get best bid (highest buy price) - first in the bids array
        Order bestBid = orderBook.getBids()[0];
        BigDecimal bestBidPrice = bestBid.getP();

        // Get best ask (lowest sell price) - first in the asks array
        Order bestAsk = orderBook.getAsks()[0];
        BigDecimal bestAskPrice = bestAsk.getP();

        if (bestBidPrice != null && bestAskPrice != null) {
            // Calculate distances
            double distanceToBid = Math.abs(tradePrice - bestBidPrice.doubleValue());
            double distanceToAsk = Math.abs(tradePrice - bestAskPrice.doubleValue());

            // Calculate position relative to averages
            this.priceCloserToBestAsk = distanceToAsk - distanceToBid;
        } else {
            // If prices are null, set a neutral value
            this.priceCloserToBestAsk = 0.0;
        }
    }

    /**
     * Gets the sequence number
     *
     * @return The sequence number
     */
    public void updateMovingAverages(Deque<BigDecimal> tradePricesQueue) {
        if (tradePricesQueue.isEmpty()) {
            return;
        }

        // Calculate short-term moving average (all prices in the queue)
        double sum = 0.0;
        int counter = 0;
        for (BigDecimal price : tradePricesQueue) {
            counter++;
            sum += price.doubleValue();
            if (counter == stma) {
                this.STMAPrice = sum/counter;
            }
            if (counter == ltma) {
                this.LTMAPrice = sum/counter;
                break;
            }
        }
    }

    /**
     *
     * @param lastPrice the price of the last trade
     */
    public void updateSumOfTrade(BigDecimal lastPrice) {
        // Update SAUp and SADown if we have a previous price to compare
        if (lastPrice != null) {
            if (tradePrice > lastPrice.doubleValue()) {
                // Price went up
                SAUp += tradePrice;
            } else if (tradePrice < lastPrice.doubleValue()) {
                // Price went down
                SADown += tradePrice;
            }
        }
    }

    // Getters and setters
    public int getSequence() {
        return sequence;
    }

    /**
     * Sets the sequence number
     *
     * @param sequence The sequence number
     */
    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    /**
     * Gets the trade price
     *
     * @return The trade price
     */
    public double getTradePrice() {
        return tradePrice;
    }

    /**
     * Sets the trade price
     *
     * @param tradePrice The trade price
     */
    public void setTradePrice(double tradePrice) {
        this.tradePrice = tradePrice;
    }

    /**
     * Gets the trade amount
     *
     * @return The trade amount
     */
    public double getTradeAmount() {
        return tradeAmount;
    }

    /**
     * Sets the trade amount
     *
     * @param tradeAmount The trade amount
     */
    public void setTradeAmount(double tradeAmount) {
        this.tradeAmount = tradeAmount;
    }

    /**
     * Gets the average ask price
     *
     * @return The average ask price
     */
    public double getAvgAskPrice() {
        return avgAskPrice;
    }

    /**
     * Sets the average ask price
     *
     * @param avgAskPrice The average ask price
     */
    public void setAvgAskPrice(double avgAskPrice) {
        this.avgAskPrice = avgAskPrice;
    }

    /**
     * Gets the average ask amount
     *
     * @return The average ask amount
     */
    public double getAvgAskAmount() {
        return avgAskAmount;
    }

    /**
     * Sets the average ask amount
     *
     * @param avgAskAmount The average ask amount
     */
    public void setAvgAskAmount(double avgAskAmount) {
        this.avgAskAmount = avgAskAmount;
    }

    /**
     * Gets the average bid price
     *
     * @return The average bid price
     */
    public double getAvgBidPrice() {
        return avgBidPrice;
    }

    /**
     * Sets the average bid price
     *
     * @param avgBidPrice The average bid price
     */
    public void setAvgBidPrice(double avgBidPrice) {
        this.avgBidPrice = avgBidPrice;
    }

    /**
     * Gets the average bid amount
     *
     * @return The average bid amount
     */
    public double getAvgBidAmount() {
        return avgBidAmount;
    }

    /**
     * Sets the average bid amount
     *
     * @param avgBidAmount The average bid amount
     */
    public void setAvgBidAmount(double avgBidAmount) {
        this.avgBidAmount = avgBidAmount;
    }

    public void setAvgBidAmount(BigDecimal avgBidAmount) {
        this.avgBidAmount = avgBidAmount != null ? avgBidAmount.doubleValue() : 0;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public double getSTMAPrice() {
        return STMAPrice;
    }

    /**
     * Sets the short-term moving average price
     *
     * @param STMAPrice The short-term moving average price
     */
    public void setSTMAPrice(double STMAPrice) {
        this.STMAPrice = STMAPrice;
    }

    /**
     * Gets the long-term moving average price
     *
     * @return The long-term moving average price
     */
    public double getLTMAPrice() {
        return LTMAPrice;
    }

    /**
     * Sets the long-term moving average price
     *
     * @param LTMAPrice The long-term moving average price
     */
    public void setLTMAPrice(double LTMAPrice) {
        this.LTMAPrice = LTMAPrice;
    }

    /**
     * Gets the sum of amounts for upward price movements
     *
     * @return The sum of amounts for upward price movements
     */
    public double getSAUp() {
        return SAUp;
    }

    /**
     * Sets the sum of amounts for upward price movements
     *
     * @param SAUp The sum of amounts for upward price movements
     */
    public void setSAUp(double SAUp) {
        this.SAUp = SAUp;
    }

    /**
     * Gets the sum of amounts for downward price movements
     *
     * @return The sum of amounts for downward price movements
     */
    public double getSADown() {
        return SADown;
    }

    /**
     * Sets the sum of amounts for downward price movements
     *
     * @param SADown The sum of amounts for downward price movements
     */
    public void setSADown(double SADown) {
        this.SADown = SADown;
    }

    /**
     * Returns the pretend trade associated with this data point.
     */
    public Trade getPretendTrade() {
        return pretendTrade;
    }

    /**
     * Sets the pretend trade for this data point.
     */
    public void setPretendTrade(Trade pretendTrade) {
        this.pretendTrade = pretendTrade;
    }


    // Getter and setter for amountMissing
    public boolean isAmountMissing() {
        return amountMissing;
    }

    public void setAmountMissing(boolean amountMissing) {
        this.amountMissing = amountMissing;
    }

    /**
     * Gets the price closer to best ask value.
     * 
     * @return Positive if closer to ask, negative if closer to bid
     */
    public double getPriceCloserToBestAsk() {
        return priceCloserToBestAsk;
    }
    
    /**
     * Sets the indicator of whether the price is closer to the best ask than the best bid
     * 
     * @param priceCloserToBestAsk Positive if closer to ask, negative if closer to bid
     */
    public void setPriceCloserToBestAsk(double priceCloserToBestAsk) {
        this.priceCloserToBestAsk = priceCloserToBestAsk;
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

    /**
     * Gets the newly completed chunk to be sent to clients
     *
     * @return Newly completed chunk information
     */
    public ChunkInfo getCompletedChunk() {
        return completedChunk;
    }

    /**
     * Sets the newly completed chunk to be sent to clients
     *
     * @param completedChunk Newly completed chunk information
     */
    public void setCompletedChunk(ChunkInfo completedChunk) {
        this.completedChunk = completedChunk;
    }

    public BigDecimal getCurrencyBalance() {
      return currencyBalance;
    }

    public void setCurrencyBalance(BigDecimal currencyBalance) {
      this.currencyBalance = currencyBalance;
  //    this.profit = calculateProfit();
    }

    public BigDecimal getCoinBalance() {
      return coinBalance;
    }

    public void setCoinBalance(BigDecimal coinBalance) {
      this.coinBalance = coinBalance;
  //    this.profit = calculateProfit();
    }

    public BigDecimal getLatestPrice() {
      return latestPrice;
    }

    public void setLatestPrice(BigDecimal latestPrice) {
      this.latestPrice = latestPrice;
    }

    public BigDecimal getAccountValueInChunk() {
      return accountValueInChunk;
    }

    public void setAccountValueInChunk(BigDecimal accountValueInChunk) {
      this.accountValueInChunk = accountValueInChunk;
    }
} 