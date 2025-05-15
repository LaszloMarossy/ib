package com.ibbe.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.time.Instant;

/**
 * Entity class for maintaining trend data for trading analysis.
 * Maintains running queues of recent trade data and calculated trend indicators.
 * 
 * maintain running queues of 
 * - last N (configure to be 20) trade prices (tradePricesQueue)
 * - last N (configure to be 20) trade amounts (tradeAmountsQueue)
 * - last N average bid amounts (bidAmountsQueue)
 * - last N average ask amounts (askAmountsQueue)
 * 
 */
public class TrendData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Default queue size
    private static final int DEFAULT_QUEUE_SIZE = 20;
    
    // Deques for maintaining recent trade data (newest at beginning/index 0, oldest at end)
    private final Deque<BigDecimal> tradePricesQueue;
    private final Deque<BigDecimal> tradeAmountsQueue;
    private final Deque<BigDecimal> bidAmountsQueue;
    private final Deque<BigDecimal> askAmountsQueue;
    
    // Maximum size for the queues
    private final int maxQueueSize;
    
    // Last price for comparison
    private BigDecimal lastPrice;
    
    // Last trade timestamp (to detect gaps)
    private Instant lastTradeTimestamp;
    
    /**
     * Default constructor with default queue size (20)
     */
    public TrendData() {
        this(DEFAULT_QUEUE_SIZE);
    }

    /**
     * Constructor with specified queue size
     * 
     * @param queueSize the maximum size for the data queues
     */
    public TrendData(int queueSize) {
        this.maxQueueSize = queueSize;
        this.tradePricesQueue = new ArrayDeque<>(queueSize);
        this.tradeAmountsQueue = new ArrayDeque<>(queueSize);
        this.bidAmountsQueue = new ArrayDeque<>(queueSize);
        this.askAmountsQueue = new ArrayDeque<>(queueSize);
        
        this.lastPrice = null;
        this.lastTradeTimestamp = null;
    }
    
    /**
     * Adds a new trade price to the queue, maintaining the maximum size.
     * Newest elements are at the beginning (index 0), oldest at the end.
     * 
     * @param price the trade price to add
     */
    public void addTradePrice(BigDecimal price) {
        if (tradePricesQueue.size() >= maxQueueSize) {
            tradePricesQueue.removeLast(); // Remove oldest element from the end
        }
        tradePricesQueue.addFirst(price); // Add newest element at the beginning

        lastPrice = price;
    }
    
    /**
     * Adds a new trade amount to the queue, maintaining the maximum size.
     * Newest elements are at the beginning (index 0), oldest at the end.
     * 
     * @param amount the trade amount to add
     */
    public void addTradeAmount(BigDecimal amount) {
        if (tradeAmountsQueue.size() >= maxQueueSize) {
            tradeAmountsQueue.removeLast(); // Remove oldest element from the end
        }
        tradeAmountsQueue.addFirst(amount); // Add newest element at the beginning
    }
    
    /**
     * Adds a new average bid amount to the queue, maintaining the maximum size.
     * Newest elements are at the beginning (index 0), oldest at the end.
     * 
     * @param amount the average bid amount to add
     */
    public void addBidAmount(BigDecimal amount) {
        if (bidAmountsQueue.size() >= maxQueueSize) {
            bidAmountsQueue.removeLast(); // Remove oldest element from the end
        }
        bidAmountsQueue.addFirst(amount); // Add newest element at the beginning
    }
    
    /**
     * Adds a new average ask amount to the queue, maintaining the maximum size.
     * Newest elements are at the beginning (index 0), oldest at the end.
     * 
     * @param amount the average ask amount to add
     */
    public void addAskAmount(BigDecimal amount) {
        if (askAmountsQueue.size() >= maxQueueSize) {
            askAmountsQueue.removeLast(); // Remove oldest element from the end
        }
        askAmountsQueue.addFirst(amount); // Add newest element at the beginning
    }
    
    /**
     * Updates the timestamp of the most recent trade
     * 
     * @param timestamp the timestamp of the current trade
     */
    public void updateTimestamp(Instant timestamp) {
        this.lastTradeTimestamp = timestamp;
    }
    
    /**
     * Gets the newest (most recent) trade price (at index 0)
     * @return the most recent trade price or null if queue is empty
     */
    public BigDecimal getNewestTradePrice() {
        return tradePricesQueue.isEmpty() ? null : tradePricesQueue.getFirst();
    }
    
    /**
     * Gets the oldest trade price in the queue (at the end)
     * @return the oldest trade price or null if queue is empty
     */
    public BigDecimal getOldestTradePrice() {
        return tradePricesQueue.isEmpty() ? null : tradePricesQueue.getLast();
    }
    
    /**
     * Clears all data in the trend queues
     */
    public void clear() {
        tradePricesQueue.clear();
        tradeAmountsQueue.clear();
        bidAmountsQueue.clear();
        askAmountsQueue.clear();
        lastPrice = null;
        lastTradeTimestamp = null;
    }
    
    // Getters and setters
    
    public Deque<BigDecimal> getTradePricesQueue() {
        return tradePricesQueue;
    }
    
    public Deque<BigDecimal> getTradeAmountsQueue() {
        return tradeAmountsQueue;
    }
    
    public Deque<BigDecimal> getBidAmountsQueue() {
        return bidAmountsQueue;
    }
    
    public Deque<BigDecimal> getAskAmountsQueue() {
        return askAmountsQueue;
    }
    
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }
    
    public Instant getLastTradeTimestamp() {
        return lastTradeTimestamp;
    }
    
    @Override
    public String toString() {
        return "TrendData{" +
                "queueSize=" + maxQueueSize +
                ", tradePricesQueueSize=" + tradePricesQueue.size() +
                ", tradeAmountsQueueSize=" + tradeAmountsQueue.size() +
                ", bidAmountsQueueSize=" + bidAmountsQueue.size() +
                ", askAmountsQueueSize=" + askAmountsQueue.size() +
                ", lastTradeTimestamp=" + lastTradeTimestamp +
                '}';
    }
} 