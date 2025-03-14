package com.ibbe.entity;

import java.math.BigDecimal;
import java.util.Deque;

/**
 * Entity class for performance analysis data.
 * Used for Webservice communication between PerformanceAnalysisEndpoint server and PerformanceWindow client.
 * Also, amounts of type double for
 * - STMAPrice (short term moving average of price),
 * - LTMAPrice (long term moving average of price),
 * - SAUp (sum of trade amounts where price of t > price of t-1),
 * - SADown(sum of trade amounts where price of t < price of t-1) (SAUp > SADown contributes to buy signal, and SADown >
 *   SAUp contributes to sell signal)
 *
 * And finally a double type for
 * - PosRelToAvg (position relative to averages)
 * indicating whether the trade price is closer to the bid price (favoring buy)
 * han to the ask price (favoring sell) (to be used with the best bid and ask price)
 */
public class PerformanceData {
    private int sequence;
    private double tradePrice;
    private double tradeAmount;
    private double avgAskPrice;
    private double avgAskAmount;
    private double avgBidPrice;
    private double avgBidAmount;
    private long timestamp;
    private boolean amountMissing;
    private Long tradeId;

    // Moving averages
    private double STMAPrice; // Short term moving average of price
    private double LTMAPrice; // Long term moving average of price

    // Sum of amounts for price movements
    private double SAUp;    // Sum of trade amounts where price of t > price of t-1
    private double SADown;  // Sum of trade amounts where price of t < price of t-1

    // Position relative to averages
    private double PosRelToAvg; // Indicates whether trade price is closer to bid (buy signal) or ask (sell signal)



    // Default constructor
    public PerformanceData() {
    }

    // Constructor with all fields
    public PerformanceData(
            int sequence,
            double tradePrice,
            double tradeAmount,
            double avgAskPrice,
            double avgAskAmount,
            double avgBidPrice,
            double avgBidAmount,
            long timestamp,
            boolean amountMissing,
            Long tradeId) {
        this.sequence = sequence;
        this.tradePrice = tradePrice;
        this.tradeAmount = tradeAmount;
        this.avgAskPrice = avgAskPrice;
        this.avgAskAmount = avgAskAmount;
        this.avgBidPrice = avgBidPrice;
        this.avgBidAmount = avgBidAmount;
        this.timestamp = timestamp;
        this.amountMissing = amountMissing;
        this.tradeId = tradeId;

        this.STMAPrice = 0.0;
        this.LTMAPrice = 0.0;
        this.SAUp = 0.0;
        this.SADown = 0.0;
        this.PosRelToAvg = 0.0;

    }



    /**
     * Updates the position relative to averages based on current trade price, best bid, and best ask
     *
     * @param orderBook containing the asks and bids array
     */
    public void updatePosRelToAvg(OrderBookPayload orderBook) {
        if (orderBook == null || orderBook.getBids() == null || orderBook.getAsks() == null 
        || orderBook.getBids().length == 0 || orderBook.getAsks().length == 0) {
            return;
        }

        // Get best bid (highest buy price) - first in the bids array
        Order bestBid = orderBook.getBids()[0];
        BigDecimal bestBidPrice = bestBid.getP();
        BigDecimal bestBidAmount = bestBid.getA();

        // Get best ask (lowest sell price) - first in the asks array
        Order bestAsk = orderBook.getAsks()[0];
        BigDecimal bestAskPrice = bestAsk.getP();
        BigDecimal bestAskAmount = bestAsk.getA();

        if (bestBidPrice != null && bestAskPrice != null) {
            // Calculate distances
            double distanceToBid = Math.abs(tradePrice - bestBidPrice.doubleValue());
            double distanceToAsk = Math.abs(tradePrice - bestAskPrice.doubleValue());

            // Calculate position relative to averages
            // Positive values indicate closer to bid (buy signal)
            // Negative values indicate closer to ask (sell signal)
            this.PosRelToAvg = distanceToAsk - distanceToBid;
        }
    }

    /**
     * Updates the moving averages based on the current prices in the queue
     */
    public void updateMovingAverages(Deque<BigDecimal> tradePricesQueue) {
        if (tradePricesQueue.isEmpty()) {
            return;
        }

        // Calculate short-term moving average (all prices in the queue)
        double sum = 0.0;
        for (BigDecimal price : tradePricesQueue) {
            sum += price.doubleValue();
        }
        this.STMAPrice = sum / tradePricesQueue.size();

        // For long-term moving average, we could use a longer period
        // This is a simplified implementation - in a real system, you might want to
        // maintain a separate, larger queue for the long-term average
        this.LTMAPrice = this.STMAPrice; // Simplified for now
    }

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

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public double getTradePrice() {
        return tradePrice;
    }

    public void setTradePrice(double tradePrice) {
        this.tradePrice = tradePrice;
    }

    public void setTradePrice(BigDecimal tradePrice) {
        this.tradePrice = tradePrice != null ? tradePrice.doubleValue() : 0;
    }

    public double getTradeAmount() {
        return tradeAmount;
    }

    public void setTradeAmount(double tradeAmount) {
        this.tradeAmount = tradeAmount;
    }

    public void setTradeAmount(BigDecimal tradeAmount) {
        this.tradeAmount = tradeAmount != null ? tradeAmount.doubleValue() : 0;
    }

    public double getAvgAskPrice() {
        return avgAskPrice;
    }

    public void setAvgAskPrice(double avgAskPrice) {
        this.avgAskPrice = avgAskPrice;
    }

    public void setAvgAskPrice(BigDecimal avgAskPrice) {
        this.avgAskPrice = avgAskPrice != null ? avgAskPrice.doubleValue() : 0;
    }

    public double getAvgAskAmount() {
        return avgAskAmount;
    }

    public void setAvgAskAmount(double avgAskAmount) {
        this.avgAskAmount = avgAskAmount;
    }

    public void setAvgAskAmount(BigDecimal avgAskAmount) {
        this.avgAskAmount = avgAskAmount != null ? avgAskAmount.doubleValue() : 0;
    }

    public double getAvgBidPrice() {
        return avgBidPrice;
    }

    public void setAvgBidPrice(double avgBidPrice) {
        this.avgBidPrice = avgBidPrice;
    }

    public void setAvgBidPrice(BigDecimal avgBidPrice) {
        this.avgBidPrice = avgBidPrice != null ? avgBidPrice.doubleValue() : 0;
    }

    public double getAvgBidAmount() {
        return avgBidAmount;
    }

    public void setAvgBidAmount(double avgBidAmount) {
        this.avgBidAmount = avgBidAmount;
    }

    public void setAvgBidAmount(BigDecimal avgBidAmount) {
        this.avgBidAmount = avgBidAmount != null ? avgBidAmount.doubleValue() : 0;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp != null ? timestamp : 0L;
    }

    public boolean isAmountMissing() {
        return amountMissing;
    }

    public void setAmountMissing(boolean amountMissing) {
        this.amountMissing = amountMissing;
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

    public void setSTMAPrice(double STMAPrice) {
        this.STMAPrice = STMAPrice;
    }

    public double getLTMAPrice() {
        return LTMAPrice;
    }

    public void setLTMAPrice(double LTMAPrice) {
        this.LTMAPrice = LTMAPrice;
    }

    public double getSAUp() {
        return SAUp;
    }

    public void setSAUp(double SAUp) {
        this.SAUp = SAUp;
    }

    public double getSADown() {
        return SADown;
    }

    public void setSADown(double SADown) {
        this.SADown = SADown;
    }

    public double getPosRelToAvg() {
        return PosRelToAvg;
    }

    public void setPosRelToAvg(double posRelToAvg) {
        this.PosRelToAvg = posRelToAvg;
    }


} 