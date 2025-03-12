package com.ibbe.entity;

import com.ibbe.executor.XchangeRatePoller;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

import static com.ibbe.entity.Tick.TICK_DOWN;
import static com.ibbe.entity.Tick.TICK_UP;
import static com.ibbe.entity.Tick.TICK_ZERO;

/**
 * internal data structures to keep the bitso trades, orderbooks for all configurable traders, coming from websocket
 */
@Component
public class BitsoDataAggregator extends IbDataAggregator {
  private static Logger LOGGER = LoggerFactory.getLogger(BitsoDataAggregator.class.getName());

  // we make up this value for the unknown value of the trade that was right before the first one we have
  private Tick mostRecentNonZeroTick = TICK_UP; //will only be TICK_UP or TICK_DOWN
  // indicates how many-eth time the order has been UP or DOWN
  static int tickCount = 1;
  @Autowired
  private XchangeRatePoller ratePoller;

  // producing events for registered configurations
 	private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

  // using it as a local variable, not to poll poller too much
  private BigDecimal currXRate;


  public BitsoDataAggregator() {
  }

  @PostConstruct
  public void init() {
    currXRate = new BigDecimal(ratePoller.getUsdMxn());

  }

  /**
   * add subscribers to changed info
   * @param l
   */
  public void addObserver(PropertyChangeListener l) {
    // new trades
    pcs.addPropertyChangeListener("WS", l);
 	}


  /**
   * run only for the incoming Bitso orderbook payload, as this converts currency to USD once, at entry time
   * Do NOT run this subsequently, when unpacking the objects from Kafka, as they are already in USD then
   */
  public void setOrderBookCurrencyToUSD() {
    Arrays.stream(orderBookPayload.getAsks()).forEach(str -> str.p = str.p.divide(currXRate, 0, RoundingMode.CEILING));
    Arrays.stream(orderBookPayload.getBids()).forEach(str -> str.p = str.p.divide(currXRate, 0, RoundingMode.CEILING));
  }

  public void internalizeBitsoTradeWs(TradeWs tradeWs) {
    // divide by the exchange rate to have USD amounts
    tradeWs.setPrice(tradeWs.getPrice().divide(currXRate, 2, RoundingMode.CEILING));
    setTickAndStatus(tradeWs);
    // pack together with Orderbook info
    tradeWs.setObp(orderBookPayload);

    LOGGER.info("+ + + " + tradeWs.getTid() + " price $"
        +  tradeWs.getPrice() + " " + tradeWs.getTick().toString() + " by $"
        + (previousBitsoTrade != null ? tradeWs.getPrice().subtract(previousBitsoTrade.getPrice()) : "0") + " "
        + tradeWs.getNthStatus());
  }

  /**
   * Enhances a Bitso Websocket trade object to be used in IB and to save into kafka
   * @param trade
   */
  private void setTickAndStatus(Trade trade) {
    // default start with no previous trade
    if (previousBitsoTrade == null) {
      // the earliest trade will never have previous tick info, so we make it up for it
      trade.setTick(TICK_UP);
      // similarly, set the nthStatus (how many times was it UP or DOWN already) to 1 as default
      trade.setNthStatus(TICK_UP.toString() + tickCount);
    } else {
      // if there was a previous trade
      switch (trade.getPrice().compareTo(previousBitsoTrade.getPrice())) {
        // price movement is DOWN
        case -1 -> {
          trade.setTick(TICK_DOWN);
          switch (previousBitsoTrade.getTick()) {
            // price movement is DOWN & previous trade was also DOWN
            case TICK_DOWN:
              // set most recent movement indicator (to ignore ZERO ticks)
              mostRecentNonZeroTick = TICK_DOWN; //may be unnecessary
              // increase count for DOWN
              tickCount++;
              break;
            // price movement is DOWN & previous tick was ZERO; ignore it and refer to the latest movement
            case TICK_ZERO:
              // previous ZERO current DOWN -> DOWN N+ or DOWN 1 (dep on mostRecentNonZeroTick)
              // one before ZERO was DOWN, so increase DOWN counter; type unchanged
              if (mostRecentNonZeroTick == TICK_DOWN) {
                tickCount++;
              } else {//case TICK_UP:
                // one before ZERO was UP, so reset counter and type
                tickCount = 1;
                mostRecentNonZeroTick = TICK_DOWN;
              }
              break;
            // if previous tick was UP:
            default:
              // previous UP current DOWN -> DOWN 1
              tickCount = 1;
              mostRecentNonZeroTick = TICK_DOWN;
          }
        }
        // price movement is UP
        case 1 -> {
          trade.setTick(TICK_UP);
          switch (previousBitsoTrade.getTick()) {
            // previous DOWN current UP => reset counter; movement change
            case TICK_DOWN:
              mostRecentNonZeroTick = TICK_UP;
              tickCount = 1;
              break;
            // previous ZERO current UP => dep on latest movement
            case TICK_ZERO:
              // latest DOWN, current UP => reset counter; movement change
              if (mostRecentNonZeroTick == TICK_DOWN) {
                tickCount = 1;
                mostRecentNonZeroTick = TICK_UP;
              } else { // latest TICK_UP, current UP => increment counter
                tickCount++;
              }
              break;
            // previous UP current UP => increase counter
            default:
              tickCount++;
          }
        }
        // price movement ZERO
        default ->
            trade.setTick(TICK_ZERO);
      }
      trade.setNthStatus(mostRecentNonZeroTick.toString() + tickCount);
    }
  }

  /**
   * add an element to the last position of the deque of trades reveived via websocket and send to event monitors (in
   * this case the TradingExecutor
   *
   * @param tradeWs TradeWs to be added
   *
   *
   */
  public void addInternalizedTradeWs(TradeWs tradeWs) {
    super.addInternalizedTradeWs(tradeWs);
    // ConfigurableTradingExecutors should know about the new tradeWs
    pcs.firePropertyChange("WS", previousBitsoTrade, tradeWs);
    previousBitsoTrade = tradeWs;
  }



}
