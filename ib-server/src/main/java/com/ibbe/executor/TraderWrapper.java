package com.ibbe.executor;

import com.ibbe.entity.TradeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Spring component that manages multiple TradingExecutor instances.
 * Acts as a container/registry for trading executors that can run in parallel
 * with different configurations.
 *
 * Other Spring components can autowire this wrapper to access trading executors
 * by their configuration IDs.
 * 
 * This class ensures that only one TradingExecutor exists per configuration ID,
 * preventing duplicate trading processes for the same configuration.
 */
@Component
public class TraderWrapper {

  // Logger for this class (SLF4J)
  private static final Logger slf4jLogger = LoggerFactory.getLogger(TraderWrapper.class);
  
  // Logger for this class (java.util.logging)
  private static final java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(TraderWrapper.class.getName());

  // Maps configuration IDs to their corresponding TradingExecutor instances
  // Key: configuration ID (String)
  // Value: TradingExecutor instance that handles trading for that configuration
  private HashMap<String, TradingExecutor> tradingExecutors;
  
  // Scheduler for periodic logging
  private final ScheduledExecutorService scheduler;

  /**
   * Initialize empty map of trading executors.
   * Called by Spring when creating this component.
   */
  public TraderWrapper() {
    this.tradingExecutors = new HashMap<String, TradingExecutor>();
    this.scheduler = Executors.newScheduledThreadPool(1);
    
    // Schedule periodic logging of trading executors - changed from 10 seconds to 60 seconds (1 minute)
    this.scheduler.scheduleAtFixedRate(this::logTradingExecutors, 60, 60, TimeUnit.SECONDS);
    
    slf4jLogger.info("TraderWrapper initialized with periodic logging every 60 seconds (SLF4J)");
  }

  /**
   * Creates and registers a new TradingExecutor if one doesn't already exist
   * for the given configuration.
   * 
   * @param tradeConfig The configuration to create a new executor for
   * @implNote If an executor already exists for this config ID, the method
   *          will silently return without creating a duplicate
   */
  public void addConfig(TradeConfig tradeConfig) {
    if (!tradingExecutors.containsKey(tradeConfig.getId())) {
      TradingExecutor tradingExecutor = new TradingExecutor(tradeConfig);
      tradingExecutors.put(tradeConfig.getId(), tradingExecutor);
      
      slf4jLogger.info("Added new trading executor with config ID: {}, ups: {}, downs: {} ", 
          tradeConfig.getId(), tradeConfig.getUps(), tradeConfig.getDowns());
     }
  }
  
  /**
   * Removes a trading executor by its configuration ID.
   * If no executor exists for the given ID, the method silently returns.
   *
   * @param configId The ID of the configuration to remove
   * @return true if a configuration was removed, false if no configuration with that ID existed
   */
  public boolean removeConfig(String configId) {
    if (configId != null && tradingExecutors.containsKey(configId)) {
      TradingExecutor removed = tradingExecutors.remove(configId);
      slf4jLogger.info("Removed trading executor with config ID: {} ", configId);
      return true;
    }
    return false;
  }
  
  /**
   * Logs the current state of all trading executors.
   * This method is called every minute by the scheduler.
   * Only logs if there are active trading executors.
   */
  private void logTradingExecutors() {
    if (tradingExecutors.isEmpty()) {
      // Don't log anything if there are no active trading executors
      return;
    }
    
    slf4jLogger.info("# of executors: {} ", tradingExecutors.size());
    
    tradingExecutors.forEach((id, executor) -> {
      TradeConfig config = executor.getTradeConfig();
      
      // Log using SLF4J logger
      slf4jLogger.info("Executor {}, ups: {}, downs: {} ", 
      config.getId(), config.getUps(), config.getDowns());
    });
  }
}

