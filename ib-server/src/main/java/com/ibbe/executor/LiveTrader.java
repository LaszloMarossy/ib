package com.ibbe.executor;

import com.ibbe.cfg.ApplicationContextProvider;
import com.ibbe.entity.BitsoDataAggregator;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.OrderBookPayload;
import com.ibbe.entity.PerformanceData;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;


/**
 * Asynchronous executor that handles trading decisions and execution. Created by IbbeController via TraderWrapper
 * Each instance represents a unique trading configuration and manages its own trades. (inputs from ItsyBitsoWindow)
 * 
 * Key responsibilities:
 * 1. Monitors trade events and executes trades based on configured conditions and BitsoDataAggregator events
 * 2. Maintains trading state and balances
 * 3. Updates display data for UI monitoring
 * 4. Manages pretend trades for simulation
 */
public class LiveTrader extends BasicTrader implements PropertyChangeListener {

  private static final Logger logger = LoggerFactory.getLogger(LiveTrader.class);

  // list of trades Itsybitso WOULD make
//  private ArrayList<Trade> pretendTrades = new ArrayList<>();

  private FxTradesDisplayData fxTradesDisplayData;
  private BitsoDataAggregator bitsoDataAggregator;
//  private XchangeRatePoller poller;

  /**
   * Creates a new trading executor with the specified configuration.
   * Initializes trading state and registers for trade events (reacting to BitsoDataAggregator trade events).
   * Called by ItsyBitsoWindow via IbbeController and TraderWrapper
   * @param tradeConfig the trading configuration to use (as passed by ItsyBitsoWindow)
   */
  public LiveTrader(TradeConfig tradeConfig) {
    super(tradeConfig);
    try {
      ApplicationContext context = ApplicationContextProvider.getApplicationContext();
//      this.poller = context.getBean(XchangeRatePoller.class);
      this.bitsoDataAggregator = context.getBean(BitsoDataAggregator.class);

      fxTradesDisplayData = new FxTradesDisplayData(
          currencyBalance, coinBalance,
          new BigDecimal(0).setScale(2, RoundingMode.DOWN), new ArrayList<>());
      // register for new trades identified by the back-end
      bitsoDataAggregator.addObserver(this);
    } catch (Exception e) {
      e.printStackTrace();
      logger.error("Completely unexpected error: ", e);
    }
    logger.info("ADDED UPS:" + tradeConfig.getUps() + " DOWNS:" + tradeConfig.getDowns() + " ID:" + tradeConfig.getId());
  }

  /**
   * Handles trade events from BitsoDataAggregator.
   * Evaluates trading conditions and executes trades when criteria are met.
   * @todo bring in line with different trading decision metrix in PerformanceTrader
   */
  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    try {
      Trade trade = ((Trade) evt.getNewValue());
      OrderBookPayload orderBook = bitsoDataAggregator.getOrderbookPayload();
      PerformanceData performanceData = makeTradeDecision(trade, orderBook);
      logger.info("$$$$$$>>>>> " + id + " >> " + (performanceData.getPretendTrade() == null));

      // if there is no starting account value yet, calculate and set
      if (BigDecimal.ZERO.equals(fxTradesDisplayData.getStartingAccountValue())) {
        fxTradesDisplayData.setStartingAccountValue(calculateAccountValue());
      }

      if (performanceData.getPretendTrade() != null) {
        tradeFollowUp(performanceData.getPretendTrade());
      }
      updateDisplay(fxTradesDisplayData);

//      logger.info("trade event " + trade.getTid() + " from " + trade.getPrice());
//      if (trade.getNthStatus().equals(TICK_DOWN.toString() + downN) && trade.getTick().equals(TICK_DOWN)) {
//        tradeFollowUp(trade(trade, MARKER_SIDE_SELL));
//      }
//      if (trade.getNthStatus().equals(TICK_UP.toString() + upM) && trade.getTick().equals(TICK_UP)) {
//        tradeFollowUp(trade(trade, MARKER_SIDE_BUY));
//      }
      refreshDisplayWithNewTrade(trade);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * this is specific to this trader
   * @param pretendTrade
   */
  private void tradeFollowUp(Trade pretendTrade) {
    logger.info("$$$$$$$$$$ " + id + " >> " + pretendTrade.getTid());
    fxTradesDisplayData.addRecentTrade(pretendTrade);
//    pretendTrades.add(pretendTrade);
//    updateBalances(pretendTrade);
  }

  /**
   * Updates the display data with new trade information.
   * Calculates latest prices and updates UI elements.
   */
  public void refreshDisplayWithNewTrade(Trade trade) {
    if (trade != null) {
      fxTradesDisplayData.addRecentTrade(trade);
      // IMPORTANT: No conversion needed as the price is already in USD
      BigDecimal latestPrice = trade.getPrice();
      logger.info("Trade ID: {} - Setting latest price to: {} USD", trade.getTid(), latestPrice);
      fxTradesDisplayData.setLatestPrice(latestPrice);
    }
  }

  /**
   * Returns the current trade configuration for this executor.
   * @return A TradeConfig object with the current configuration values
   */
  public TradeConfig getTradeConfig() {
    return new TradeConfig(
        id, String.valueOf(upN), String.valueOf(downN), useAvgBidVsAvgAsk, useShortVsLongMovAvg,
        useSumAmtUpVsDown, useTradePriceCloserToAskVsBuy);
  }

  public FxTradesDisplayData getFxTradesDisplayData() {
    return fxTradesDisplayData;
  }



}
