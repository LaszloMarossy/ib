package com.ibbe.executor;

import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.Order;
import com.ibbe.entity.OrderBookPayload;
import com.ibbe.entity.PerformanceData;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.entity.TrendData;
import com.ibbe.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

  // Sequence counter for performance data points
  private final AtomicInteger sequenceCounter = new AtomicInteger(0);

  // set initial balances from config, but no latest price and recent trades!
  protected BigDecimal currencyBalance = new BigDecimal(PropertiesUtil.getProperty("starting.bal.currency")).setScale(2, RoundingMode.DOWN);
  protected BigDecimal coinBalance = new BigDecimal(PropertiesUtil.getProperty("starting.bal.coin")).setScale(8, RoundingMode.DOWN);
  protected BigDecimal latestPrice = new BigDecimal(0);
  protected BigDecimal firstTradePrice = new BigDecimal(0);
  protected BigDecimal profit = new BigDecimal(0);
  protected final BigDecimal startingCurrencyBalance = currencyBalance;
  protected final BigDecimal startingCoinBalance = coinBalance;
  protected final TrendData trendData = new TrendData();

  // keeps ups and downs from the user input
  protected final int downN;
  protected final int upN;
  // represents the tade config's ID; not used in performance trading
  protected final String id;

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
    downN = Integer.parseInt(tradeConfig.getDowns());
    upN = Integer.parseInt(tradeConfig.getUps());
    id = tradeConfig.getId();
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
   * - MA5<MA20 (short-term trend below long-term trend, indicating downward momentum).
   * - QAt>QBt (stronger selling interest).
   */
  public PerformanceData makeTradeDecision(Trade trade, OrderBookPayload orderBook) {
    // Skip if essential data is missing
    if (trade == null || trade.getNthStatus() == null ||
        trade.getTick() == null) {
      logger.warn("Skipping trade decision due to missing data");
      return null;
    }
    // Calculate statistics
    PerformanceData performanceData = calculatePerformanceData(trade, orderBook);
    // now calculate long and short term trends data
    calculateTrends(trendData, performanceData, orderBook);


    // set the first trade price based on the very first trade prie
    latestPrice = trade.getPrice();
    if (firstTradePrice.equals(BigDecimal.ZERO)) {
      firstTradePrice = latestPrice;
    }

    // set balance info regardless of whether we will be trading
    performanceData.setFxTradesDisplayData(new FxTradesDisplayData(
        currencyBalance, coinBalance, trade.getPrice(), new ArrayList<>()
    ));

    // todo see if more of the trendData info can be used in trade decisions!
    Trade pretendTrade = null;
    try {
      // here make trade decision based on configuration
      if (sellingTime(trade, performanceData)) {
        pretendTrade = trade(trade, MARKER_SIDE_BUY);
        updateBalances(pretendTrade);
        updateDisplay(performanceData.getFxTradesDisplayData());
      } else {
        if (buyingTime(trade, performanceData)) {
          pretendTrade = trade(trade, MARKER_SIDE_SELL);
          updateBalances(pretendTrade);
          updateDisplay(performanceData.getFxTradesDisplayData());
        }
      }
      performanceData.setPretendTrade(pretendTrade);
    } catch (Exception e) {
      logger.error("Error in makeTradeDecision: {}", e.getMessage(), e);
    }
    return performanceData;
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
  protected void updateBalances(Trade pretendTrade) {
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
  }

  protected void updateDisplay(FxTradesDisplayData fxTradesDisplayData) {
    fxTradesDisplayData.setCurrencyBalance(currencyBalance);
    fxTradesDisplayData.setCoinBalance(coinBalance);
    fxTradesDisplayData.setProfit(calculateProfit());
    fxTradesDisplayData.setAccountValue(calculateAccountValue());
  }




  public BigDecimal calculateProfit() {
    BigDecimal currentValue = calculateAccountValue();
    BigDecimal originalValue = startingCoinBalance.multiply(firstTradePrice).add(startingCurrencyBalance).setScale(2, RoundingMode.DOWN);
    profit = currentValue.subtract(originalValue).setScale(2, RoundingMode.DOWN);
    return profit;
  }

  public BigDecimal calculateAccountValue() {
    BigDecimal accountValue = coinBalance.multiply(latestPrice).add(currencyBalance).setScale(2, RoundingMode.DOWN);
    return accountValue;
  }

  protected boolean sellingTime(Trade trade, PerformanceData performanceData) {
    // Check for null values to prevent NullPointerException
    if (trade == null || performanceData == null || trade.getNthStatus() == null ||
        trade.getTick() == null) {
      return false;
    }

    // a value was given for the up condition by the user
    return downN > 0 &&
        // average bid amount exceeds average ask amount
        // performanceData.avgBidAmount > performanceData.avgAskAmount &&
        // short term trend of prices exceeds long term trend
        // performanceData.STMAPrice > performanceData.LTMAPrice &&
        // sum amounts for up trades are greater than for down trades over last N trades
        // performanceData.SAUp > performanceData.SADown &&
        // latest trade price is closer to the best ask price, than to the best bid price
        // performanceData.priceCloserToBestAsk > 0 &&
        // latest trade matches the configured Nth up value
        trade.getNthStatus().equals(TICK_DOWN.toString() + downN) &&
        // last trade was UP from before
        trade.getTick().equals(TICK_DOWN);
  }

  protected boolean buyingTime(Trade trade, PerformanceData performanceData) {
    // Check for null values to prevent NullPointerException
    if (trade == null || performanceData == null || trade.getNthStatus() == null ||
        trade.getTick() == null) {
      return false;
    }

    // a value was given for the down condition by the user
    return upN > 0 &&
        // average ask amount exceeds average bid amount
        // performanceData.avgBidAmount < performanceData.avgAskAmount &&
        // short term trend of prices below long term trend
        // performanceData.STMAPrice < performanceData.LTMAPrice &&
        // sum amounts for down trades are greater than for up trades over last N trades
        // performanceData.SAUp < performanceData.SADown &&
        // latest trade price is closer to the best bid price, than to the best ask price
        // performanceData.priceCloserToBestAsk < 0 &&
        // latest trade matches the configured Nth up value
        trade.getNthStatus().equals(TICK_UP.toString() + upN) &&
        // last trade was DOWN from before
        trade.getTick().equals(TICK_UP);
  }

  /**
   * Calculates performance data based on trade and orderbook data.
   *
   * Bt: the average price of the top 20 bid prices.
   * At: the average price of the top 20 ask prices.
   * QBt: the average amount of the top 20 bid amounts.
   * QAt: the average amount of the top 20 ask amounts.
   */
  public PerformanceData calculatePerformanceData(Trade trade, OrderBookPayload orderBook) {
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
      PerformanceData data = new PerformanceData();
      data.setSequence(sequenceCounter.getAndIncrement());
      data.setTradeId(trade.getTid());
      data.setTradePrice(trade.getPrice());
      data.setTradeAmount(trade.getAmount());
      data.setAvgAskPrice(avgAskPrice);
      data.setAvgAskAmount(avgAskAmount);
      data.setAvgBidPrice(avgBidPrice);
      data.setAvgBidAmount(avgBidAmount);
      data.setTimestamp(System.currentTimeMillis());

      return data;
  }


  /**
   * enhance the PeformanceData object with trends data, redying it for trading decisions and FX display
   * get all info into place for making trade decisions
   */
  public void calculateTrends(TrendData trendData, PerformanceData performanceData, OrderBookPayload orderBook) {
      performanceData.updateTradePriceRelToBest(orderBook);
      performanceData.updateMovingAverages(trendData.getTradePricesQueue());
      performanceData.updateSumOfTrade(trendData.getLastPrice());
  }




  public BigDecimal getCoinBalance() {
    return coinBalance;
  }

  public void setCoinBalance(BigDecimal coinBalance) {
    this.coinBalance = coinBalance;
  }

  public BigDecimal getCurrencyBalance() {
    return currencyBalance;
  }

  public void setCurrencyBalance(BigDecimal currencyBalance) {
    this.currencyBalance = currencyBalance;
  }

  public BigDecimal getProfit() { return profit; }



}
