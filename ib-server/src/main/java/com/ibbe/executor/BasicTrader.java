package com.ibbe.executor;

import com.ibbe.entity.Order;
import com.ibbe.entity.OrderBookPayload;
import com.ibbe.entity.TradeSnapshot;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.entity.TrendData;
import com.ibbe.entity.ChunkInfo;
import com.ibbe.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ibbe.entity.Tick.TICK_DOWN;
import static com.ibbe.entity.Tick.TICK_UP;

public class BasicTrader {

  private static final Logger logger = LoggerFactory.getLogger(BasicTrader.class);

  protected static final String MARKER_SIDE_SELL = "PRETEND sell";
  protected static final String MARKER_SIDE_BUY = "PRETEND buy";

  protected static final BigDecimal TRADING_FEE_BUY = new BigDecimal("1.01");
  protected static final BigDecimal TRADING_FEE_SELL = new BigDecimal("0.99");
  protected static final BigDecimal BUY_AMT = new BigDecimal(PropertiesUtil.getProperty("buy.amt"));
  protected static final BigDecimal SELL_AMT = new BigDecimal(PropertiesUtil.getProperty("sell.amt"));

  // the ID of the current trade is divisible by 10 so that we can insert a pretendTrade and maintain
  protected static final int TRADE_ID_OFFSET = 5;

  // Maximum time gap between trades (in hours) before starting a new chunk
  // Use a low value for testing to ensure chunks are created frequently
  protected static final long MAX_TRADE_GAP_HOURS = 1;

  // For testing: Force new chunk creation every this many trades
  // Commented out to prevent creating new chunks every 20 trades
  // This was causing too many chunks to be created, which is not the intended behavior
  // Chunks should accumulate up to 20 trades but still be part of the same logical group
  // protected static final int FORCE_NEW_CHUNK_EVERY_N_TRADES = 20;

  // Sequence counter for performance data points
  private final AtomicInteger sequenceCounter = new AtomicInteger(0);

  // set initial balances from config, but no latest price and recent trades!
  protected BigDecimal currencyBalance = new BigDecimal(PropertiesUtil.getProperty("starting.bal.currency")).setScale(2, RoundingMode.DOWN);
  protected BigDecimal coinBalance = new BigDecimal(PropertiesUtil.getProperty("starting.bal.coin")).setScale(8, RoundingMode.DOWN);
  protected BigDecimal profit = new BigDecimal(0);
  protected final BigDecimal startingCurrencyBalance = currencyBalance;
  protected final BigDecimal startingCoinBalance = coinBalance;
  protected final TrendData trendData = new TrendData();

  // Store the last processed trade for reference
  protected Trade lastProcessedTrade = null;

  // Store detailed info about each chunk
  protected final List<ChunkInfo> chunks = new ArrayList<>();

  // Track the current chunk number
  protected int currentChunkNumber = 1;

  // Track the starting timestamp of the current chunk
  protected Instant currentChunkStartTime = null;

  // Track the first trade price of the current chunk
  // the price of the first trade in the current chunk - kept so that we can calculate the profit of the current chunk
//  protected BigDecimal firstTradePrice = new BigDecimal(0);
  protected BigDecimal currentChunkStartPrice = null;

  // Track the number of trades in the current chunk
  protected int currentChunkTradeCount = 0;

  // keeps ups and downs from the user input
  protected final int downN;
  protected final int upN;
  protected final boolean useAvgBidVsAvgAsk;
  protected final boolean useShortVsLongMovAvg;
  protected final boolean useSumAmtUpVsDown;
  protected final boolean useTradePriceCloserToAskVsBuy;
  // represents the tade config's ID; not used in performance trading
  protected final String id;

  // Use long for timestamp storage
  protected long currentChunkStartTimeMillis = 0;

  /**
   * Enum representing trade types with their corresponding marker strings.
   * Used to maintain consistency in trade type identification.
   */
  public enum TradeType {
    BUY("PRETEND buy"),
    SELL("PRETEND sell");

    private final String value;

    TradeType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /**
   * Creates a new trading executor with the specified configuration.
   *
   * @param tradeConfig the trading configuration to use (as passed by PerformanceAnalysisEndpoint)
   */
  public BasicTrader(TradeConfig tradeConfig) {
    if (tradeConfig == null) {
      throw new IllegalArgumentException("TradeConfig cannot be null");
    }

    // Parse downs value - default to 0 if null or not a valid integer
    int parsedDownN = 0;
    try {
      String downsValue = tradeConfig.getDowns();
      if (downsValue != null && !downsValue.trim().isEmpty()) {
        parsedDownN = Integer.parseInt(downsValue.trim());
      }
    } catch (NumberFormatException e) {
      logger.warn("Invalid downs value in TradeConfig: {}. Using default value of 0.", tradeConfig.getDowns());
    }
    downN = parsedDownN;

    // Parse ups value - default to 0 if null or not a valid integer
    int parsedUpN = 0;
    try {
      String upsValue = tradeConfig.getUps();
      if (upsValue != null && !upsValue.trim().isEmpty()) {
        parsedUpN = Integer.parseInt(upsValue.trim());
      }
    } catch (NumberFormatException e) {
      logger.warn("Invalid ups value in TradeConfig: {}. Using default value of 0.", tradeConfig.getUps());
    }
    upN = parsedUpN;

    id = tradeConfig.getId();
    useAvgBidVsAvgAsk = tradeConfig.isUseAvgBidVsAvgAsk();
    useShortVsLongMovAvg = tradeConfig.isUseShortVsLongMovAvg();
    useSumAmtUpVsDown = tradeConfig.isUseSumAmtUpVsDown();
    useTradePriceCloserToAskVsBuy = tradeConfig.isUseTradePriceCloserToAskVsBuy();

    logger.info("Performance according to UPS:{} DOWNS:{} ID:{}", upN, downN, id);

    // Reset sequence counter for new client connection
    sequenceCounter.set(0);
  }

  /**
   * Handles trade events read from kafka and makes trade decisions.
   * <p>
   * Buy signal:
   * - MA5 > MA20 (short-term trend of moving averages exceeds long-term trend, indicating upward momentum).
   * - QBt > QAt (average bid amount exceeds the average ask amount).
   * - Pt > Pt−1 >⋯> Pt−N+1 (Consistent price increases)
   * - Vup > Vdown (sum of amounts for trades where Pt > Pt−1 or where Pt > Pt−1 over the last NN trades).
   * <p>
   * Sell signal:
   * - MA5 < MA20 (short-term trend below long-term trend, indicating downward momentum).
   * - QAt>QBt (stronger selling interest).
   */
  public TradeSnapshot makeTradeDecision(Trade trade, OrderBookPayload orderBook) {
    // Skip if essential data is missing
    if (trade == null || trade.getNthStatus() == null ||
        trade.getTick() == null) {
      logger.warn("Skipping trade decision due to missing data");
      return null;
    }

    // Calculate orderbook averages for trade and create the tradeSnapshot object representing the trade
    TradeSnapshot tradeSnapshot = calculateOrderbookAveragesForTrade(trade, orderBook);

    // Handle time-based trade chunking and set FX display data
    handleTradeChunks(tradeSnapshot);

    // now calculate long and short term trends data and update the tradeSnapshot object
    calculateTrends(trade, tradeSnapshot, orderBook);

    // todo see if more of the trendData info can be used in trade decisions!
    Trade pretendTrade = null;
    try {
      // here make trade decision based on configuration
      if (sellingTime(trade, tradeSnapshot)) {
        pretendTrade = trade(trade, MARKER_SIDE_SELL);
        updateBalances(pretendTrade, tradeSnapshot);
      } else {
        if (buyingTime(trade, tradeSnapshot)) {
          pretendTrade = trade(trade, MARKER_SIDE_BUY);
          updateBalances(pretendTrade, tradeSnapshot);
        }
      }
      tradeSnapshot.setPretendTrade(pretendTrade);
    } catch (Exception e) {
      logger.error("Error in makeTradeDecision: {}", e.getMessage(), e);
    }

    // Save this trade as the last processed trade for future reference
    lastProcessedTrade = trade;

    return tradeSnapshot;
  }

  /**
   * Calculates the orderbook averages for a given trade.
   *
   * Bt: the average price of the top 20 bid prices.
   * At: the average price of the top 20 ask prices.
   * QBt: the average amount of the top 20 bid amounts.
   * QAt: the average amount of the top 20 ask amounts.
   */
  public TradeSnapshot calculateOrderbookAveragesForTrade(Trade trade, OrderBookPayload orderBook) {
      // Calculate average ask price and amount
      BigDecimal totalAskPrice = BigDecimal.ZERO;
      BigDecimal totalAskAmount = BigDecimal.ZERO;

      for (Order ask : orderBook.getAsks()) {
          totalAskPrice = totalAskPrice.add(ask.getP());
          totalAskAmount = totalAskAmount.add(ask.getA());
      }

      BigDecimal askCount = new BigDecimal(orderBook.getAsks().length);
      BigDecimal avgAskPrice = askCount.compareTo(BigDecimal.ZERO) > 0
              ? totalAskPrice.divide(askCount, 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      BigDecimal avgAskAmount = askCount.compareTo(BigDecimal.ZERO) > 0
              ? totalAskAmount.divide(askCount, 4, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;

      // Calculate average bid price and amount
      BigDecimal totalBidPrice = BigDecimal.ZERO;
      BigDecimal totalBidAmount = BigDecimal.ZERO;

      for (Order bid : orderBook.getBids()) {
          totalBidPrice = totalBidPrice.add(bid.getP());
          totalBidAmount = totalBidAmount.add(bid.getA());
      }

      BigDecimal bidCount = new BigDecimal(orderBook.getBids().length);
      BigDecimal avgBidPrice = bidCount.compareTo(BigDecimal.ZERO) > 0
              ? totalBidPrice.divide(bidCount, 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      BigDecimal avgBidAmount = bidCount.compareTo(BigDecimal.ZERO) > 0
              ? totalBidAmount.divide(bidCount, 4, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;

      // Polulate performance data object
      TradeSnapshot data = new TradeSnapshot();
      data.setSequence(sequenceCounter.getAndIncrement());
      data.setTradeId(trade.getTid());
      data.setTradePrice(trade.getPrice() == null ? 0d : trade.getPrice().doubleValue());
      data.setTradeAmount(trade.getAmount() == null ? 0d : trade.getAmount().doubleValue());
      data.setAvgAskPrice(avgAskPrice.doubleValue());
      data.setAvgAskAmount(avgAskAmount.doubleValue());
      data.setAvgBidPrice(avgBidPrice.doubleValue());
      data.setAvgBidAmount(avgBidAmount);
      data.setTimestamp(trade.getCreatedAt());
      return data;
  }

  /**
   * Handles time-based trade chunking logic.
   * Detects time gaps between trades and manages trading chunks accordingly.
   *
   * @param tradeSnapshot the performance data to update with the FX display data
   */
  protected void handleTradeChunks(TradeSnapshot tradeSnapshot) {
    // Process the chunk transition if needed
    if (weShouldStartNewChunk(tradeSnapshot)) {
      // If this isn't the first trade ever, calculate the current chunk's profit before resetting
      if (lastProcessedTrade != null) {
        BigDecimal chunkProfit = calculateChunkProfit(tradeSnapshot);

//        // Get last trade timestamp as milliseconds
//        Instant lastTradeTimestamp = parseTradeTimestamp(lastProcessedTrade.getCreatedAt());
//        long lastTradeTimeMillis = lastTradeTimestamp != null ? lastTradeTimestamp.toEpochMilli() : 0;

        // Create and store chunk info using milliseconds
        ChunkInfo completedChunk = new ChunkInfo(currentChunkNumber, chunkProfit, currentChunkStartPrice,
            (lastProcessedTrade != null ? lastProcessedTrade.getPrice() : BigDecimal.ZERO),
            currentChunkTradeCount, currentChunkStartTimeMillis, convertTimestampToMillis(lastProcessedTrade.getCreatedAt()));
//        chunks.add(completedChunk);
        
        // Add this newly completed chunk to the snapshot data
        // This way the client only receives the newly completed chunk
        tradeSnapshot.setCompletedChunk(completedChunk);
//        tradeSnapshot.setAccountValueInChunk(calculateAccountValue(tradeSnapshot));
        logger.info("Added newly completed chunk {} to performance data", currentChunkNumber);

        currentChunkNumber++;

        logger.info("Saving profit of {} for completed trading chunk {}", chunkProfit, completedChunk.getChunkNumber());
      }

      // Initialize a new trading chunk with this trade
      initNewChunk(tradeSnapshot);
    } else {
      // Increment trade count for the current chunk
      currentChunkTradeCount++;
    }

      // Set balance info regardless of whether we're in a new chunk

//      tradeSnapshot.setCurrencyBalance(currencyBalance);
//      tradeSnapshot.setCoinBalance(coinBalance);
//      tradeSnapshot.setTradePrice(lastProcessedTrade != null ? lastProcessedTrade.getPrice().doubleValue() : 0d);

      // Update profit and account value
//      tradeSnapshot.setAccountValueInChunk(calculateAccountValue(tradeSnapshot));

      // Add the current chunk info
//        ChunkInfo currentChunk = new ChunkInfo(
//            currentChunkNumber,
//            calculateChunkProfit(),
//            currentChunkStartPrice,
//            BigDecimal.valueOf(tradeSnapshot.tradePrice),
//            currentChunkTradeCount,
//            currentChunkStartTimeMillis,
//            convertTimestampToMillis(lastProcessedTrade.getCreatedAt())
//        );
//
//        // Add current chunk info to performance data
//        tradeSnapshot.setCurrentChunk(currentChunk);

  }

  protected boolean weShouldStartNewChunk(TradeSnapshot tradeSnapshot) {
    // Get the current trade timestamp
    Instant currentTradeTimestamp = parseTradeTimestamp(tradeSnapshot.getTimestamp());
    if (currentTradeTimestamp == null) {
      logger.warn("Could not parse timestamp for trade: {}", tradeSnapshot.getTradeId());
      return false;
    }
    // Get current trade timestamp as milliseconds
    long currentTradeTimeMillis = currentTradeTimestamp.toEpochMilli();

    // Determine if we need to start a new chunk
    boolean shouldStartNewChunk = false;

    // here determine if we're in a new chunk or not
    if (lastProcessedTrade == null) {
      // Very first trade ever - create the initial chunk
      shouldStartNewChunk = true;
      logger.info("Processing very first trade, initializing first trading chunk");
    } else {
      // Check for time gap between this trade and previous one
      Instant lastTradeTimestamp = parseTradeTimestamp(lastProcessedTrade.getCreatedAt());

      if (lastTradeTimestamp != null) {
        Duration gap = Duration.between(lastTradeTimestamp, currentTradeTimestamp);
        if (gap.toHours() >= MAX_TRADE_GAP_HOURS) {
          shouldStartNewChunk = true;
          logger.info("Detected time gap of {} hours. Starting new trading chunk.", gap.toHours());
        }
      }
    }
    return shouldStartNewChunk;
  }
  /**
   * Initializes a new trading chunk with the given trade.
   * Resets trend data, balances, and profit tracking for the new chunk.
   *
   * @param tradeSnapshot TradeSnapshot the first trade in the new chunk
   */
  private void initNewChunk(TradeSnapshot tradeSnapshot) {
    // Reset for new chunk
    trendData.clear();

    // Reset balances to starting values
    currencyBalance = new BigDecimal(PropertiesUtil.getProperty("starting.bal.currency")).setScale(2, RoundingMode.DOWN);
    coinBalance = new BigDecimal(PropertiesUtil.getProperty("starting.bal.coin")).setScale(8, RoundingMode.DOWN);

    // Set first trade price for this new chunk directly
    currentChunkStartPrice = BigDecimal.valueOf(tradeSnapshot.getTradePrice());
    profit = BigDecimal.ZERO;

    // Track chunk information
    Instant timestamp = parseTradeTimestamp(tradeSnapshot.getTimestamp());
    currentChunkStartTimeMillis = timestamp != null ? timestamp.toEpochMilli() : System.currentTimeMillis();
    currentChunkTradeCount = 1;

    logger.info("Initialized new trading chunk with first trade price: {} at time: {}",
        currentChunkStartPrice, timestamp);
  }

  public BigDecimal calculateChunkProfit(TradeSnapshot tradeSnapshot) {
    BigDecimal currentValue = calculateAccountValue(tradeSnapshot);
    BigDecimal originalValue = startingCoinBalance.multiply(currentChunkStartPrice).add(startingCurrencyBalance).setScale(2, RoundingMode.DOWN);
    profit = currentValue.subtract(originalValue).setScale(2, RoundingMode.DOWN);
    return profit;
  }

  /**
   * determine the account's current value WITHIN the current chunk of the trade being sent through it
   * @param tradeSnapshot
   * @return
   */
  public BigDecimal calculateAccountValue(TradeSnapshot tradeSnapshot) {
    // important that this uses the last processed trade price, as that definitely belongs to the last chunk
    // before a new one is initialized!
    if (lastProcessedTrade != null ) {
      return coinBalance.multiply(lastProcessedTrade.getPrice())
          .add(currencyBalance).setScale(2, RoundingMode.DOWN);
    } else {
      // in case of the very first trade, use the trade price of the shipped trade's price
      return startingCoinBalance.multiply(BigDecimal.valueOf(tradeSnapshot.tradePrice))
          .add(startingCurrencyBalance).setScale(2, RoundingMode.DOWN);
    }
  }

  /**
   * enhance the PeformanceData object with trends data, redying it for trading decisions and FX display
   * get all info into place for making trade decisions
   */
  public void calculateTrends(Trade trade, TradeSnapshot tradeSnapshot, OrderBookPayload orderBook) {
    // Add the new trade price to the queue, and as the latest price, before calculating trends
    // this should be performed regardless of a pause in running the process
    trendData.addTradePrice(trade.getPrice());
    trendData.addTradeAmount(trade.getAmount());

    tradeSnapshot.updateTradePriceRelToBest(orderBook);
    tradeSnapshot.updateMovingAverages(trendData.getTradePricesQueue());
    tradeSnapshot.updateSumOfTrade(lastProcessedTrade != null ? lastProcessedTrade.getPrice() : null);
  }

  /**
   * Determines if it's time to sell based on the trade and performance data.
   *
   * @param trade the trade to evaluate
   * @param tradeSnapshot the performance data for the trade
   * @return true if it's time to sell, false otherwise
   */
  protected boolean sellingTime(Trade trade, TradeSnapshot tradeSnapshot) {
    // Check for null values to prevent NullPointerException
    if (trade == null || tradeSnapshot == null || trade.getNthStatus() == null ||
        trade.getTick() == null) {
      return false;
    }
    boolean sellingTime = false;
    // first condition is that ANY of the markers are set for selling
    sellingTime = downN > 0 || useAvgBidVsAvgAsk || useShortVsLongMovAvg || useSumAmtUpVsDown || useTradePriceCloserToAskVsBuy;

    if (downN > 0) {
      sellingTime = sellingTime && (trade.getNthStatus().equals(TICK_DOWN.toString() + downN) && trade.getTick().equals(TICK_DOWN));
    }

    if (useAvgBidVsAvgAsk) {
      sellingTime = sellingTime && tradeSnapshot.avgBidAmount > tradeSnapshot.avgAskAmount;
    }

    if (useShortVsLongMovAvg) {
      sellingTime = sellingTime && tradeSnapshot.STMAPrice > tradeSnapshot.LTMAPrice;
    }

    if (useSumAmtUpVsDown) {
      sellingTime = sellingTime && tradeSnapshot.SAUp > tradeSnapshot.SADown;
    }

    if (useTradePriceCloserToAskVsBuy) {
      sellingTime = sellingTime && tradeSnapshot.priceCloserToBestAsk > 0;
    }

    return sellingTime;
  }

  protected boolean buyingTime(Trade trade, TradeSnapshot tradeSnapshot) {
    // Check for null values to prevent NullPointerException
    if (trade == null || tradeSnapshot == null || trade.getNthStatus() == null ||
        trade.getTick() == null) {
      return false;
    }

    boolean buyingTime = false;
    // first condition is that ANY of the markers are set for buying
    buyingTime = upN > 0 || useAvgBidVsAvgAsk || useShortVsLongMovAvg || useSumAmtUpVsDown || useTradePriceCloserToAskVsBuy;

    if (upN > 0) {
      buyingTime = buyingTime && (trade.getNthStatus().equals(TICK_UP.toString() + upN) && trade.getTick().equals(TICK_UP));
    }

    if (useAvgBidVsAvgAsk) {
      buyingTime = buyingTime && tradeSnapshot.avgBidAmount < tradeSnapshot.avgAskAmount;
    }

    if (useShortVsLongMovAvg) {
      buyingTime = buyingTime && tradeSnapshot.STMAPrice < tradeSnapshot.LTMAPrice;
    }

    if (useSumAmtUpVsDown) {
      buyingTime = buyingTime && tradeSnapshot.SAUp < tradeSnapshot.SADown;
    }

    if (useTradePriceCloserToAskVsBuy) {
      buyingTime = buyingTime && tradeSnapshot.priceCloserToBestAsk < 0;
    }

    return buyingTime;
  }

  /**
   * Creates a new pretend trade based on the most recent market trade.
   * Updates account balances and display data accordingly.
   *
   * @param mostRecentTrade the most recent market trade (as received from BitsoDataAggregator)
   * @param typeOfTrade     the type of trade to create (as passed by ItsyBitsoWindow)
   */
  public Trade trade(Trade mostRecentTrade, String typeOfTrade) {
    if (mostRecentTrade == null || typeOfTrade == null) {
      logger.warn("Cannot create pretend trade with null parameters");
      return null;
    }

    BigDecimal amount = (MARKER_SIDE_BUY.equals(typeOfTrade) ? BUY_AMT : SELL_AMT).setScale(4, RoundingMode.DOWN);

    Trade pretendTrade = Trade.builder()
        .createdAt(mostRecentTrade.getCreatedAt())
        .amount(amount)
        .makerSide(typeOfTrade)
        // todo here use either bids or asks price instead of latest trade's price..
        .price(mostRecentTrade.getPrice())
        .tid(mostRecentTrade.getTid() + TRADE_ID_OFFSET)
        .build();

    // logger.info("$$PRETEND$$ {} >> {}", id, pretendTrade.getTid());
    // perform here as this is when balances are affected
    return pretendTrade;
  }

  /**
   * Updates account balances after a trade execution.
   * Handles both buy and sell scenarios with their respective fees.
   */
  protected void updateBalances(Trade pretendTrade, TradeSnapshot tradeSnapshot) {
    if (pretendTrade.getAmount() == null || pretendTrade.getPrice() == null) {
      logger.warn("Cannot update balances with null balance or trade values");
      return;
    }

    // the cost of the pretend trade at current trade price
    BigDecimal priceOfTrade = pretendTrade.getAmount().multiply(pretendTrade.getPrice());
    switch (pretendTrade.getMakerSide()) {
      case MARKER_SIDE_BUY -> {
        currencyBalance = currencyBalance.subtract(priceOfTrade.multiply(TRADING_FEE_BUY))
            .setScale(2, RoundingMode.DOWN);
        coinBalance = coinBalance.add(pretendTrade.getAmount()).setScale(8, RoundingMode.DOWN);
      }
      case MARKER_SIDE_SELL -> {
        currencyBalance = currencyBalance.add(priceOfTrade.multiply(TRADING_FEE_SELL))
            .setScale(2, RoundingMode.DOWN);
        coinBalance = coinBalance.subtract(pretendTrade.getAmount()).setScale(8, RoundingMode.DOWN);
      }
      default -> logger.info("!!! WRONG MARKER SIDE: {}", pretendTrade.getMakerSide());
    }
    // set fields for the display too
    tradeSnapshot.setCurrencyBalance(currencyBalance);
    tradeSnapshot.setCoinBalance(coinBalance);
    // pretend trades in chunks would carry this snapshot acct value
    tradeSnapshot.setAccountValueInChunk(calculateAccountValue(tradeSnapshot));

  }


  /**
   * Parses the timestamp from a trade
   *
   * @param tradeTimestamp the createdAt string of the trade
   * @return the Instant or null if unable to parse
   */
  private Instant parseTradeTimestamp(String tradeTimestamp) {
     try {
      // Convert string timestamp to Instant
      ZonedDateTime zonedDateTime = ZonedDateTime.parse(tradeTimestamp);
      return zonedDateTime.toInstant();
    } catch (Exception e) {
      logger.warn("Failed to parse trade timestamp: {}", tradeTimestamp);
      return null;
    }
  }


  /**
   * Converts an ISO-8601 timestamp string to milliseconds since epoch
   * @param timestampStr String in format "2025-05-22T13:20:49.930Z"
   * @return milliseconds since epoch
   */
  private long convertTimestampToMillis(String timestampStr) {
      try {
          return Instant.parse(timestampStr).toEpochMilli();
      } catch (Exception e) {
          System.err.println("Error parsing timestamp: " + timestampStr);
          return System.currentTimeMillis(); // fallback to current time
      }
  }

  /**
   * Gets the price of the last processed trade
   *
   * @return the last trade price or BigDecimal.ZERO if no trades processed
   */
  public BigDecimal getLastTradePrice() {
    return lastProcessedTrade != null ? lastProcessedTrade.getPrice() : BigDecimal.ZERO;
  }


}
