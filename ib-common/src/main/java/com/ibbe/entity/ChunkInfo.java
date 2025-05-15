package com.ibbe.entity;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Contains information about a continuous trading chunk.
 * A chunk represents a series of trades without large time gaps between them.
 */
public class ChunkInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final int chunkNumber;
    private final BigDecimal profit;
    private final BigDecimal startingTradePrice;
    private final BigDecimal endingTradePrice;
    private final int tradeCount;
    private final long startTimeMillis;  // Using epoch milliseconds
    private final long endTimeMillis;    // Using epoch milliseconds
    
    /**
     * Constructor that accepts all fields including millisecond timestamps directly
     * This constructor is crucial for Jackson deserialization
     */
    public ChunkInfo(int chunkNumber, BigDecimal profit, BigDecimal startingTradePrice, 
                     BigDecimal endingTradePrice, int tradeCount, long startTimeMillis, long endTimeMillis) {
        this.chunkNumber = chunkNumber;
        this.profit = profit;
        this.startingTradePrice = startingTradePrice;
        this.endingTradePrice = endingTradePrice;
        this.tradeCount = tradeCount;
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
    }
    
    public int getChunkNumber() {
        return chunkNumber;
    }
    
    public BigDecimal getProfit() {
        return profit;
    }
    
    public BigDecimal getStartingTradePrice() {
        return startingTradePrice;
    }
    
    public BigDecimal getEndingTradePrice() {
        return endingTradePrice;
    }
    
    public int getTradeCount() {
        return tradeCount;
    }
    
    public long getStartTimeMillis() {
        return startTimeMillis;
    }
    
    public long getEndTimeMillis() {
        return endTimeMillis;
    }
    
    @Override
    public String toString() {
        return "ChunkInfo{" +
               "chunkNumber=" + chunkNumber +
               ", profit=" + profit +
               ", startingTradePrice=" + startingTradePrice +
               ", endingTradePrice=" + endingTradePrice +
               ", tradeCount=" + tradeCount +
               ", startTimeMillis=" + startTimeMillis +
               ", endTimeMillis=" + endTimeMillis +
               '}';
    }
} 