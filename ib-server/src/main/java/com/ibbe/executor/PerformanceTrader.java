package com.ibbe.executor;

import com.ibbe.entity.PerformanceData;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.entity.TrendData;
import com.ibbe.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.ibbe.entity.Tick.TICK_DOWN;
import static com.ibbe.entity.Tick.TICK_UP;

/**
 * Asynchronous executor that handles trading decisions and execution on retrieved Kafka trade records
 */
public class PerformanceTrader {

  private static final Logger logger = LoggerFactory.getLogger(PerformanceTrader.class);
  private static final String MARKER_SIDE_SELL = "PRETEND sell";
  private static final String MARKER_SIDE_BUY = "PRETEND buy";

  // keeps ups and downs from the user input
  private final int downN;
  private final int upN;
  // represents the tade config's ID; not used in performance trading
  private final String id;

  private static final BigDecimal TRADING_FEE_BUY = new BigDecimal("1.01");
  private static final BigDecimal TRADING_FEE_SELL = new BigDecimal("0.99");
  private static final BigDecimal BUY_AMT = new BigDecimal(PropertiesUtil.getProperty("buy.amt"));
  private static final BigDecimal SELL_AMT = new BigDecimal(PropertiesUtil.getProperty("sell.amt"));
  private static final int TRADE_ID_OFFSET = 5;

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
   * @param tradeConfig the trading configuration to use (as passed by PerformanceAnalysisEndpoint)
   */
  public PerformanceTrader(TradeConfig tradeConfig) {
    if (tradeConfig == null) {
      throw new IllegalArgumentException("TradeConfig cannot be null");
    }
    downN = Integer.parseInt(tradeConfig.getDowns());
    upN = Integer.parseInt(tradeConfig.getUps());
    id = tradeConfig.getId();
    logger.info("Performance according to UPS:{} DOWNS:{} ID:{}", upN, downN, id);
  }

  /**
   * Handles trade events read from kafka and makes trade decisions.
   *
   * Buy signal:
   * - MA5 > MA20 (short-term trend of moving averages exceeds long-term trend, indicating upward momentum).
   * - QBt > QAt (average bid amount exceeds the average ask amount).
   * - Pt > Pt−1 >⋯> Pt−N+1 (Consistent price increases)
   * - Vup > Vdown (sum of amounts for trades where Pt > Pt−1 or where Pt > Pt−1 over the last NN trades).
   *
   * Sell signal:
   * - MA5<MA20 (short-term trend below long-term trend, indicating downward momentum).
   * - QAt>QBt (stronger selling interest).
   */
  public void makeTradeDecision(Trade trade, PerformanceData performanceData) {
    // Skip if essential data is missing
    if (trade == null || performanceData == null || trade.getNthStatus() == null || 
        trade.getTick() == null) {
      logger.warn("Skipping trade decision due to missing data");
      return;
    }

    // todo see if more of the trendData info can be used in trade decisions!
    Trade pretendTrade = null;
    try {
      // here make trade decision based on configuration
      if (sellingTime(trade, performanceData)) {
        pretendTrade = trade(trade, MARKER_SIDE_BUY);
        updateBalances(pretendTrade, performanceData);
      } else {
        if (buyingTime(trade, performanceData)) {
          pretendTrade = trade(trade, MARKER_SIDE_SELL);
          updateBalances(pretendTrade, performanceData);
        }
      }
     performanceData.setPretendTrade(pretendTrade);
    } catch (Exception e) {
      logger.error("Error in makeTradeDecision: {}", e.getMessage(), e);
    }
  }

  private boolean sellingTime(Trade trade, PerformanceData performanceData) {
    // Check for null values to prevent NullPointerException
    if (trade == null || performanceData == null || trade.getNthStatus() == null || 
        trade.getTick() == null) {
      return false;
    }
    
    if (
      // a value was given for the up condition by the user
      upN > 0 &&
      // average bid amount exceeds average ask amount
      // performanceData.avgBidAmount > performanceData.avgAskAmount &&
      // short term trend of prices exceeds long term trend
      // performanceData.STMAPrice > performanceData.LTMAPrice &&
      // sum amounts for up trades are greater than for down trades over last N trades
      // performanceData.SAUp > performanceData.SADown &&
      // latest trade price is closer to the best ask price, than to the best bid price
      // performanceData.priceCloserToBestAsk > 0 &&
      // latest trade matches the configured Nth up value
      trade.getNthStatus().equals(TICK_UP.toString() + upN) &&
      // last trade was UP from before
      trade.getTick().equals(TICK_UP)
    )
      return true;
    return false;
  }

  private boolean buyingTime(Trade trade, PerformanceData performanceData) {
    // Check for null values to prevent NullPointerException
    if (trade == null || performanceData == null || trade.getNthStatus() == null || 
        trade.getTick() == null) {
      return false;
    }
    
    if (
      // a value was given for the down condition by the user
      downN > 0 &&
      // average ask amount exceeds average bid amount
      // performanceData.avgBidAmount < performanceData.avgAskAmount &&
      // short term trend of prices below long term trend
      // performanceData.STMAPrice < performanceData.LTMAPrice &&
      // sum amounts for down trades are greater than for up trades over last N trades
      // performanceData.SAUp < performanceData.SADown &&
      // latest trade price is closer to the best bid price, than to the best ask price
      // performanceData.priceCloserToBestAsk < 0 &&
      // latest trade matches the configured Nth up value
      trade.getNthStatus().equals(TICK_DOWN.toString() + downN) &&
      // last trade was DOWN from before
      trade.getTick().equals(TICK_DOWN)
    )
      return true;
    return false;
  }


  /**
   * Creates a new pretend trade based on the most recent market trade.
   * Updates account balances and display data accordingly.
   * @param mostRecentTrade the most recent market trade (as received from BitsoDataAggregator)
   * @param typeOfTrade the type of trade to create (as passed by ItsyBitsoWindow)
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
  private void updateBalances(Trade pretendTrade, PerformanceData performanceData) {
    if (pretendTrade == null || performanceData == null || 
        performanceData.getFxTradesDisplayData() == null) {
      logger.warn("Cannot update balances with null parameters");
      return;
    }
    
    BigDecimal currentCurrencyBalance = performanceData.getFxTradesDisplayData().getCurrencyBalance();
    BigDecimal currentCoinBalance = performanceData.getFxTradesDisplayData().getCoinBalance();
    
    if (currentCurrencyBalance == null || currentCoinBalance == null || 
        pretendTrade.getAmount() == null || pretendTrade.getPrice() == null) {
      logger.warn("Cannot update balances with null balance or trade values");
      return;
    }
    
    // logger.info("Before trade - Currency: {}, Coin: {}", 
    //             currentCurrencyBalance, currentCoinBalance);
    
    BigDecimal priceOfTrade = pretendTrade.getAmount().multiply(pretendTrade.getPrice());
    switch (pretendTrade.getMakerSide()) {
      case MARKER_SIDE_BUY -> {
        BigDecimal newCurrencyBalance = currentCurrencyBalance.subtract(
            priceOfTrade.multiply(TRADING_FEE_BUY)).setScale(2, RoundingMode.DOWN);
        BigDecimal newCoinBalance = currentCoinBalance.add(
            pretendTrade.getAmount()).setScale(8, RoundingMode.DOWN);
            
        // logger.info("BUY Trade - Price: {}, Amount: {}, New Currency: {}, New Coin: {}", 
        //             pretendTrade.getPrice(), pretendTrade.getAmount(), 
        //             newCurrencyBalance, newCoinBalance);
                    
        performanceData.getFxTradesDisplayData().setCurrencyBalance(newCurrencyBalance);
        performanceData.getFxTradesDisplayData().setCoinBalance(newCoinBalance);
      }
      case MARKER_SIDE_SELL -> {
        BigDecimal newCurrencyBalance = currentCurrencyBalance.add(
            priceOfTrade.multiply(TRADING_FEE_SELL)).setScale(2, RoundingMode.DOWN);
        BigDecimal newCoinBalance = currentCoinBalance.subtract(
            pretendTrade.getAmount()).setScale(8, RoundingMode.DOWN);
            
        // logger.info("SELL Trade - Price: {}, Amount: {}, New Currency: {}, New Coin: {}", 
        //             pretendTrade.getPrice(), pretendTrade.getAmount(), 
        //             newCurrencyBalance, newCoinBalance);
                    
        performanceData.getFxTradesDisplayData().setCurrencyBalance(newCurrencyBalance);
        performanceData.getFxTradesDisplayData().setCoinBalance(newCoinBalance);
      }
      default -> logger.info("!!! WRONG MARKER SIDE: {}", pretendTrade.getMakerSide());
    }
    
    // Log the updated balances and profit
    // logger.info("After trade - Currency: {}, Coin: {}, Profit: {}", 
    //             performanceData.getFxTradesDisplayData().getCurrencyBalance(),
    //             performanceData.getFxTradesDisplayData().getCoinBalance(),
    //             performanceData.getFxTradesDisplayData().getProfit());
  }

}
