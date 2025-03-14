package com.ibbe.executor;

import com.ibbe.cfg.ApplicationContextProvider;
import com.ibbe.entity.BitsoDataAggregator;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ibbe.entity.Tick.TICK_DOWN;
import static com.ibbe.entity.Tick.TICK_UP;

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
public class LiveTrader extends AsyncExecutor implements PropertyChangeListener {

  private static final Logger logger = LoggerFactory.getLogger(LiveTrader.class);
  private static final String MARKER_SIDE_SELL = "PRETEND sell";
  private static final String MARKER_SIDE_BUY = "PRETEND buy";

  private static int topX;
  private String downN;
  private String upM;
  private String id;
  private BigDecimal startingAccountValue = null;
  // list of trades Itsybitso WOULD make
  private ArrayList<Trade> pretendTrades = new ArrayList<>();
  private ExecutorService tradeExe;

  private FxTradesDisplayData fxTradesDisplayData;
  private BitsoDataAggregator bitsoDataAggregator;
  private XchangeRatePoller poller;

  private static final BigDecimal TRADING_FEE_BUY = new BigDecimal("1.01");
  private static final BigDecimal TRADING_FEE_SELL = new BigDecimal("0.99");
  private static final int TRADE_ID_OFFSET = 5;
  private static final int THREAD_POOL_SIZE = 5;

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
   * Initializes trading state and registers for trade events.
   * Called by ItsyBitsoWindow via IbbeController and TraderWrapper
   * @param tradeConfig the trading configuration to use (as passed by ItsyBitsoWindow)
   */
  public LiveTrader(TradeConfig tradeConfig) {
    if (tradeConfig == null) {
      throw new IllegalArgumentException("TradeConfig cannot be null");
    }
    try {
      ApplicationContext context = ApplicationContextProvider.getApplicationContext();
      this.poller = context.getBean(XchangeRatePoller.class);
      this.bitsoDataAggregator = context.getBean(BitsoDataAggregator.class);
      downN = tradeConfig.getDowns();
      upM = tradeConfig.getUps();
      id = tradeConfig.getId();
      topX = Integer.parseInt(PropertiesUtil.getProperty("displaydata.topx"));

      tradeExe = Executors.newFixedThreadPool(5);

      fxTradesDisplayData = new FxTradesDisplayData(new BigDecimal(PropertiesUtil.getProperty("starting.bal.currency")).setScale(2, RoundingMode.DOWN),
          new BigDecimal(PropertiesUtil.getProperty("starting.bal.coin")).setScale(4, RoundingMode.DOWN),
          new BigDecimal(0).setScale(2, RoundingMode.DOWN), new ArrayList<>());
      // register for new trades identified by the back-end
      bitsoDataAggregator.addObserver(this);
    } catch (Exception e) {
      e.printStackTrace();
      logger.error("SHIIIIIIT");
    }
    logger.info("ADDED UPS:" + tradeConfig.getUps() + " DOWNS:" + tradeConfig.getDowns() + " ID:" + tradeConfig.getId());
  }

  /**
   * Handles trade events from BitsoDataAggregator.
   * Evaluates trading conditions and executes trades when criteria are met.
   */
  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    try {
      Trade trade = ((Trade) evt.getNewValue());
      logger.info("trade event " + trade.getTid() + " from " + trade.getPrice());
      if (trade.getNthStatus().equals(TICK_DOWN.toString() + downN) && trade.getTick().equals(TICK_DOWN)) {
        trade(trade, MARKER_SIDE_SELL);
      }
      if (trade.getNthStatus().equals(TICK_UP.toString() + upM) && trade.getTick().equals(TICK_UP)) {
        trade(trade, MARKER_SIDE_BUY);
      }
      refreshDisplayWithNewTrade(trade);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates and executes a trade asynchronously.
   * Primarily used for future real trading implementation.
   */
  public Future<Trade> asyncTrade(Trade mostRecentTrade, String typeOfTrade) {
    Callable<Trade> call = () -> trade(mostRecentTrade, typeOfTrade);
    return tradeExe.submit(call);
  }

  /**
   * Creates a new pretend trade based on the most recent market trade.
   * Updates account balances and display data accordingly.
   * @param mostRecentTrade the most recent market trade (as received from BitsoDataAggregator)
   * @param typeOfTrade the type of trade to create (as passed by ItsyBitsoWindow)
   */
  public Trade trade(Trade mostRecentTrade, String typeOfTrade) {
    BigDecimal amount = new BigDecimal(PropertiesUtil.getProperty(
        MARKER_SIDE_BUY.equals(typeOfTrade) ? "buy.amt" : "sell.amt"))
        .setScale(4, RoundingMode.DOWN);
        
    Trade pretendTrade = Trade.builder()
        .createdAt(mostRecentTrade.getCreatedAt())
        .amount(amount)
        .makerSide(typeOfTrade)
        .price(mostRecentTrade.getPrice())
        .tid(mostRecentTrade.getTid() + TRADE_ID_OFFSET)
        .build();

    logger.info("$$$$$$$$$$ " + id + " >> " + pretendTrade.getTid());
    fxTradesDisplayData.addRecentTradeWs(pretendTrade);
    pretendTrades.add(pretendTrade);
    updateBalances(pretendTrade);
    return pretendTrade;
  }

  /**
   * Updates the display data with new trade information.
   * Calculates latest prices and updates UI elements.
   */
  public void refreshDisplayWithNewTrade(Trade trade) {
    if (trade != null) {
      fxTradesDisplayData.addRecentTradeWs(trade);
      // IMPORTANT: No conversion needed as the price is already in USD
      BigDecimal latestPrice = trade.getPrice();
      logger.info("Trade ID: {} - Setting latest price to: {} USD", trade.getTid(), latestPrice);
      fxTradesDisplayData.setLatestPrice(latestPrice);
      if (startingAccountValue == null) {
        startingAccountValue = new BigDecimal(0);
      }
    }
  }

  /**
   * Updates account balances after a trade execution.
   * Handles both buy and sell scenarios with their respective fees.
   */
  private void updateBalances(Trade pretendTrade) {
    switch (pretendTrade.getMakerSide()) {
      case MARKER_SIDE_BUY -> {
        fxTradesDisplayData.setCurrencyBalance(fxTradesDisplayData.getCurrencyBalance().subtract(
            pretendTrade.getAmount().multiply(
                pretendTrade.getPrice().divide(new BigDecimal(poller.getUsdMxn()), RoundingMode.DOWN)
            ).multiply(TRADING_FEE_BUY)
        ).setScale(2, RoundingMode.DOWN));
        fxTradesDisplayData.setCoinBalance(fxTradesDisplayData.getCoinBalance().add(pretendTrade.getAmount()).setScale(2, RoundingMode.DOWN));
      }
      case MARKER_SIDE_SELL -> {
        fxTradesDisplayData.setCurrencyBalance(fxTradesDisplayData.getCurrencyBalance().add(
            pretendTrade.getAmount().multiply(
                pretendTrade.getPrice().divide(new BigDecimal(poller.getUsdMxn()), RoundingMode.DOWN)
            ).multiply(TRADING_FEE_SELL)
        ).setScale(2, RoundingMode.DOWN));
        fxTradesDisplayData.setCoinBalance(fxTradesDisplayData.getCoinBalance().subtract(pretendTrade.getAmount()).setScale(2, RoundingMode.DOWN));
      }
      default -> logger.info("!!! WRONG MARKER SIDE: " + pretendTrade.getMakerSide());
    }
  }

  /**
   * Returns the current trade configuration for this executor.
   * @return A TradeConfig object with the current configuration values
   */
  public TradeConfig getTradeConfig() {
    return new TradeConfig(id, upM, downN);
  }

  public FxTradesDisplayData getFxTradesDisplayData() {
    return fxTradesDisplayData;
  }



}
