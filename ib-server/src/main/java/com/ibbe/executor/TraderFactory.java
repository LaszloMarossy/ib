package com.ibbe.executor;

import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.TradeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spring component that manages multiple LiveTrader instances.
 * Acts as a container/registry for trading executors that can run in parallel
 * with different configurations.
 *
 * Other Spring components can autowire this wrapper to access trading executors
 * by their configuration IDs.
 * 
 * This class ensures that only one LiveTrader exists per configuration ID,
 * preventing duplicate trading processes for the same configuration.
 */
@Component
public class TraderFactory {

  // Logger for this class (SLF4J)
  private static final Logger slf4jLogger = LoggerFactory.getLogger(TraderFactory.class);
  

  // Maps configuration IDs to their corresponding LiveTrader instances
  // Key: configuration ID (String)
  // Value: LiveTrader instance that handles trading for that configuration
//  private HashMap<String, LiveTrader> liveTraders;
  private final Map<String, LiveTrader> traders = new ConcurrentHashMap<>();

  
  // Scheduler for periodic logging
  private final ScheduledExecutorService scheduler;

  /**
   * Initialize empty map of trading executors.
   * Called by Spring when creating this component.
   */
  public TraderFactory() {
    this.scheduler = Executors.newScheduledThreadPool(1);
    
    // Schedule periodic logging of trading executors - changed from 10 seconds to 60 seconds (1 minute)
    this.scheduler.scheduleAtFixedRate(this::logLiveTraders, 60, 60, TimeUnit.SECONDS);
    
    slf4jLogger.info("TraderWrapper initialized with periodic logging every 60 seconds (SLF4J)");
  }

  /**
   * Gets or creates a trader for the given configuration ID
   */
  public LiveTrader getTrader(String id) {
      return traders.get(id);
  }

  /**
   * Creates and registers a new LiveTrader if one doesn't already exist
   * for the given configuration.
   *
   * @param config The configuration to create a new executor for
   * @implNote If an executor already exists for this config ID, the method
   *          will silently return without creating a duplicate
   */
  public LiveTrader createTrader(TradeConfig config) {
    String id = config.getId();

    // Remove existing trader if present
    removeTrader(id);

    // Create and configure new trader
    LiveTrader trader = new LiveTrader(config);
    traders.put(id, trader);
    slf4jLogger.info("Added new trading executor with config ID: {}, ups: {}, downs: {} ",
        config.getId(), config.getUps(), config.getDowns());
    return trader;
  }

  /**
   * Removes a trader by ID
   */
  public boolean removeTrader(String id) {
      if (id != null && traders.containsKey(id)) {
        traders.remove(id);
        slf4jLogger.info("Removed trader with ID: {}", id);
        return true;
      }
      return false;
  }

  /**
   * Gets display data for a trader by ID
   */
  public FxTradesDisplayData getTraderDisplayData(String id) {
      LiveTrader trader = traders.get(id);
      if (trader == null) {
        slf4jLogger.warn("No trader found for ID: {}", id);
          return null;
      }
      return trader.getFxTradesDisplayData();
  }



  /**
   * Logs the current state of all trading executors.
   * This method is called every minute by the scheduler.
   * Only logs if there are active trading executors.
   */
  private void logLiveTraders() {
    if (traders.isEmpty()) {
      // Don't log anything if there are no active trading executors
      return;
    }
    
    slf4jLogger.info("# of executors: {} ", traders.size());
    
    traders.forEach((id, executor) -> {
      TradeConfig config = executor.getTradeConfig();
      
      // Log using SLF4J logger
      slf4jLogger.info("Executor {}, ups: {}, downs: {} ", 
      config.getId(), config.getUps(), config.getDowns());
    });
  }


}

