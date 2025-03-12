package com.ibbe.entity;

import java.math.BigDecimal;

/**
 * Entity class for performance analysis data.
 * Used for Webservice communication between PerformanceAnalysisEndpoint server and PerformanceWindow client.
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
} 