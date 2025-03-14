package com.ibbe;

import com.ibbe.entity.BitsoDataAggregator;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.TradeConfig;
import com.ibbe.executor.TraderWrapper;
import com.ibbe.executor.TradingExecutor;
import com.ibbe.executor.XchangeRatePoller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * server-side REST controller for the Itsybitso trading application.
 * Handles incoming requests from the client and processes them to update the trading configuration or retrieve trading information.
 */
@RestController
public class IbbeController {
  private static final Logger logger = LoggerFactory.getLogger(IbbeController.class);

  @Autowired
  XchangeRatePoller poller;

  @Autowired
  TraderWrapper traderWrapper;

  @Autowired
  BitsoDataAggregator bitsoDataAggregator;

  /**
   * Handles GET requests to retrieve the current USD/MXN exchange rate.
   * @return String containing the current exchange rate
   */
  @GetMapping("/usdmxn")
  public String hello() {
    return String.format("The current rate of xchange " + poller.getUsdMxn());
  }

  /**
   * Handles GET requests to retrieve the current orderbook data.
   * @return String containing the orderbook data
   */
  @GetMapping("/orderbook")
  public String orderbook() {
    return String.format(" " + bitsoDataAggregator.getOrderbookPayload().toString());
  }

  /**
   * Adds a new trading configuration to be monitored by the window.
   * @param id The ID of the trading configuration
   * @param ups The ups value for the trading configuration
   * @param downs The downs value for the trading configuration
   * @return String containing the status of the trading configuration
   */
  @GetMapping("/addconfiguration/{id}/{ups}/{downs}")
  public String addTradingConfiguration(@PathVariable("id") String id, @PathVariable("ups") String ups, @PathVariable("downs") String downs) throws Exception {
      TradeConfig tradeConfig = new TradeConfig(id, ups, downs);
      traderWrapper.addConfig(tradeConfig);
      // this returns right away to the REST caller
      return "started polling the recent trades from Bitso with " + ups + " and " + downs + " " + id;
  }

  /**
   * Removes an existing trading configuration.
   * @param id The ID of the trading configuration to remove
   * @return String containing the status of the removal operation
   */
  @GetMapping("/removeconfiguration/{id}")
  public String removeTradingConfiguration(@PathVariable("id") String id) {
      boolean removed = traderWrapper.removeConfig(id);
      if (removed) {
          return "Successfully removed trading configuration with ID: " + id;
      } else {
          return "No trading configuration found with ID: " + id;
      }
  }

  /**
   * Handles GET requests to retrieve the trading executor information.
   * @param id The ID of the trading executor
   * @return String containing the trading executor information
   */
  @GetMapping("/getexecutorinfo/{id}")
  public String getExecutorInfo(@PathVariable("id") String id) throws Exception {
      // this returns right away to the REST caller
      FxTradesDisplayData dd = TradingExecutor.getConfigsDisplayData(id);
      return "trader " + id + " account value: $" + dd.calculateAccountValue()
          + " with profit $" + dd.calculateProfit();
  }
}
