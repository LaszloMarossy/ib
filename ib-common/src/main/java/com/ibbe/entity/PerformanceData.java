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
    
    // List of detailed chunk information
    private List<ChunkInfo> chunks = new ArrayList<>();
    
    // Current chunk information
    private ChunkInfo currentChunk;
    
    // Total number of chunks that exist (may be more than what's in the chunks list)
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
        this.tradeId = tradeId;
        this.amountMissing = amountMissing;

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
     * Updates the moving averages based on the current prices in the queue
     *
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
    
//    /**
//     * Returns the trade type:
//     * 1 = BUY
//     * 2 = SELL
//     *
//     * @return 1 for buy trades, 2 for sell trades
//     */
//    public int getTradeType() {
//        if (pretendTrade != null && pretendTrade.getMakerSide() != null) {
//            if (pretendTrade.getMakerSide().toLowerCase().contains("buy")) {
//                return 1; // Buy
//            } else {
//                return 2; // Sell
//            }
//        }
//        return 0; // Unknown
//    }
    
//    /**
//     * Returns the price of the pretend trade as a BigDecimal.
//     *
//     * @return the pretend trade price or BigDecimal.ZERO if no pretend trade
//     */
//    public BigDecimal getPrice() {
//        if (pretendTrade != null) {
//            return pretendTrade.getPrice();
//        }
//        return BigDecimal.ZERO;
//    }
    
//    /**
//     * Sets the price of the pretend trade.
//     * This method is needed for JSON deserialization.
//     *
//     * @param price the price to set
//     */
//    public void setPrice(BigDecimal price) {
//        // If this is called during JSON deserialization but no pretend trade exists yet,
//        // create a new trade to hold the price
//        if (pretendTrade == null) {
//            // Initialize with default values
//            pretendTrade = new Trade("", BigDecimal.ZERO, "buy", price, 0L);
//        } else {
//            pretendTrade.setPrice(price);
//        }
//    }
    
//    /**
//     * Returns the amount of the pretend trade as a BigDecimal.
//     *
//     * @return the pretend trade amount or BigDecimal.ZERO if no pretend trade
//     */
//    public BigDecimal getAmount() {
//        if (pretendTrade != null) {
//            return pretendTrade.getAmount();
//        }
//        return BigDecimal.ZERO;
//    }

    public FxTradesDisplayData getFxTradesDisplayData() {
        return fxTradesDisplayData;
    }

    public void setFxTradesDisplayData(FxTradesDisplayData fxTradesDisplayData) {
        this.fxTradesDisplayData = fxTradesDisplayData;
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
     * @return the price closer to best ask value
     */
    public double getPriceCloserToBestAsk() {
        return priceCloserToBestAsk;
    }
    
    /**
     * Sets the price closer to best ask value.
     * 
     * @param priceCloserToBestAsk the price closer to best ask value to set
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

//    /**
//     * Sets the amount of the pretend trade.
//     * This method is needed for JSON deserialization.
//     *
//     * @param amount the amount to set
//     */
//    public void setAmount(BigDecimal amount) {
//        // If this is called during JSON deserialization but no pretend trade exists yet,
//        // create a new trade to hold the amount
//        if (pretendTrade == null) {
//            // Initialize with default values
//            pretendTrade = new Trade("", amount, "buy", BigDecimal.ZERO, 0L);
//        } else {
//            pretendTrade.setAmount(amount);
//        }
//    }
//
//    /**
//     * Sets the trade type (buy/sell) of the pretend trade.
//     * This method is needed for JSON deserialization.
//     *
//     * @param tradeType the trade type to set (1 for buy, 2 for sell)
//     */
//    public void setTradeType(int tradeType) {
//        // If this is called during JSON deserialization but no pretend trade exists yet,
//        // create a new trade to hold the trade type
//        if (pretendTrade == null) {
//            // Initialize with default values and appropriate maker side
//            String makerSide = (tradeType == 1) ? "buy" : "sell";
//            pretendTrade = new Trade("", BigDecimal.ZERO, makerSide, BigDecimal.ZERO, 0L);
//        } else {
//            // Update the maker side based on the trade type
//            pretendTrade.setMakerSide((tradeType == 1) ? "buy" : "sell");
//        }
//    }

    public List<BigDecimal> getChunkProfits() {
        return chunkProfits;
    }
    
    public void setChunkProfits(List<BigDecimal> chunkProfits) {
        this.chunkProfits = chunkProfits;
    }

    /**
     * Gets the list of detailed chunk information
     * 
     * @return List of chunk information objects
     */
    public List<ChunkInfo> getChunks() {
        return chunks;
    }
    
    /**
     * Sets the list of detailed chunk information
     * 
     * @param chunks List of chunk information objects
     */
    public void setChunks(List<ChunkInfo> chunks) {
        this.chunks = chunks;
    }
    
    /**
     * Gets information about the current trading chunk
     * 
     * @return Current chunk information
     */
    public ChunkInfo getCurrentChunk() {
        return currentChunk;
    }
    
    /**
     * Sets information about the current trading chunk
     * 
     * @param currentChunk Current chunk information
     */
    public void setCurrentChunk(ChunkInfo currentChunk) {
        this.currentChunk = currentChunk;
    }

    // Getter and setter for totalChunkCount
    public int getTotalChunkCount() {
        return totalChunkCount;
    }
    
    public void setTotalChunkCount(int totalChunkCount) {
        this.totalChunkCount = totalChunkCount;
    }

} 