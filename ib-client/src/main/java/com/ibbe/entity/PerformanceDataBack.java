package com.ibbe.entity;

import com.ibbe.util.PropertiesUtil;

import java.math.BigDecimal;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;

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
 * - priceCloserToBestAsk (position relative to averages)
 * indicating whether the trade price is closer to the bid price (favoring buy)
 * than to the ask price (favoring sell) (to be used with the best bid and ask price)
 */
public class PerformanceData {
    private int sequence;
    public double tradePrice;
    public double tradeAmount;
    public double avgAskPrice;
    public double avgAskAmount;
    public double avgBidPrice;
    public double avgBidAmount;
    private long timestamp;
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
    private FxTradesDisplayData fxTradesDisplayData;
    
    // List of profits from previous trading chunks
    private List<BigDecimal> chunkProfits = new ArrayList<>();
    
    // A newly completed chunk to be sent to clients
    private ChunkInfo newCompletedChunk;
    
    // Current chunk information
    private ChunkInfo currentChunk;
    
    // Total number of chunks that exist (may be more than in the chunks list)
    private int totalChunkCount;

    // keeping configured constants for long/short term moving average calc
    public int ltma, stma;

    // Default constructor
    public PerformanceData() {
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
     * Gets the total number of chunks that exist (may be more than are contained in the chunks list)
     * 
     * @return Total count of all chunks
     */
    public int getTotalChunkCount() {
        return totalChunkCount;
    }
    
    /**
     * Sets the total number of chunks
     * 
     * @param totalChunkCount Total count of all chunks
     */
    public void setTotalChunkCount(int totalChunkCount) {
        this.totalChunkCount = totalChunkCount;
    }
    
    // Additional getters and setters for chunk-related fields
    
    public List<BigDecimal> getChunkProfits() {
        return chunkProfits;
    }
    
    public void setChunkProfits(List<BigDecimal> chunkProfits) {
        this.chunkProfits = chunkProfits;
    }
    
    public ChunkInfo getNewCompletedChunk() {
        return newCompletedChunk;
    }
    
    public void setNewCompletedChunk(ChunkInfo newCompletedChunk) {
        this.newCompletedChunk = newCompletedChunk;
    }
    
    public ChunkInfo getCurrentChunk() {
        return currentChunk;
    }
    
    public void setCurrentChunk(ChunkInfo currentChunk) {
        this.currentChunk = currentChunk;
    }
    
    /**
     * Gets the FX trades display data
     * 
     * @return The FX trades display data
     */
    public FxTradesDisplayData getFxTradesDisplayData() {
        return fxTradesDisplayData;
    }
    
    /**
     * Sets the FX trades display data
     * 
     * @param fxTradesDisplayData The FX trades display data
     */
    public void setFxTradesDisplayData(FxTradesDisplayData fxTradesDisplayData) {
        this.fxTradesDisplayData = fxTradesDisplayData;
    }
    
    /**
     * Gets the pretend trade
     * 
     * @return The pretend trade
     */
    public Trade getPretendTrade() {
        return pretendTrade;
    }
    
    /**
     * Sets the pretend trade
     * 
     * @param pretendTrade The pretend trade
     */
    public void setPretendTrade(Trade pretendTrade) {
        this.pretendTrade = pretendTrade;
    }
    
    /**
     * Gets whether the amount is missing
     * 
     * @return True if amount is missing, false otherwise
     */
    public boolean isAmountMissing() {
        return amountMissing;
    }
    
    /**
     * Sets whether the amount is missing
     * 
     * @param amountMissing True if amount is missing, false otherwise
     */
    public void setAmountMissing(boolean amountMissing) {
        this.amountMissing = amountMissing;
    }
    
    /**
     * Gets the sequence number
     * 
     * @return The sequence number
     */
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
     * Gets the timestamp
     * 
     * @return The timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Sets the timestamp
     * 
     * @param timestamp The timestamp
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Gets the trade ID
     * 
     * @return The trade ID
     */
    public Long getTradeId() {
        return tradeId;
    }
    
    /**
     * Sets the trade ID
     * 
     * @param tradeId The trade ID
     */
    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
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
    
    /**
     * Gets the short-term moving average price
     * 
     * @return The short-term moving average price
     */
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
     * Gets the indicator of whether the price is closer to the best ask than the best bid
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
    
    // ... Rest of the class implementation
} 