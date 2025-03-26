package com.ibbe.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.ExchangeRate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous single-threaded executor of refreshing internal copy of xchange rate;
 * - called by StartupListener.
 * - called by the AppController REST method
 * 
 * This implementation uses the configured default value initially and polls
 * the exchange rate API very infrequently to conserve API quota.
 */
@Component
public class XchangeRatePoller implements ApplicationListener<ApplicationReadyEvent> {

  private static XchangeRatePoller xchangeRatePoller;

  private static final Logger LOGGER = LoggerFactory.getLogger(XchangeRatePoller.class.getName());
  
  @Value("${poller.interval.xchrate}")
  private String POLLER_INTERVAL_XCHRATE;
  
  @Value("${poller.default.xchrate}")
  private String POLLER_DEFAULT_XCHRATE;
  
  @Value("${xchange.url}")
  private String POLLER_URL;
  
  private static final ObjectMapper objectMapper = new ObjectMapper();
  
  // Initial delay before first API call (in hours)
  private static final int INITIAL_DELAY_HOURS = 6;
  
  // Fixed polling interval (in hours)
  private static final int POLLING_INTERVAL_HOURS = 24;
  
  // set default value to xchange rate as api may be limited
  private Double usdMxn = 0d;
  
  // Track the last time we successfully polled the API
  private LocalDateTime lastSuccessfulPoll = null;
  
  private final Environment env;

  // declaring this here vs. the base class as then there would be only a single thread pool for populate and consume
  private ExecutorService exchRateExe;

  @Autowired
  public XchangeRatePoller(Environment env) {
    this.env = env;
    LOGGER.info("$$$$$$$$ XchangeRatePoller constructor");
    exchRateExe = Executors.newSingleThreadExecutor();
  }

  /**
   * Method to make sure Autowire stuff properly set after construction
   */
  @PostConstruct
  public void init() {
    usdMxn = Double.parseDouble(POLLER_DEFAULT_XCHRATE);
    LOGGER.info("XchangeRatePoller initialized with default rate: {} USD/MXN", usdMxn);
    LOGGER.info("Will start polling exchange rate API after {} hours", INITIAL_DELAY_HOURS);
    LOGGER.info("Fixed polling interval: {} hours", POLLING_INTERVAL_HOURS);
  }

  /**
   * Method called by Spring when boot started up
   * @param event
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    LOGGER.info("onApplicationEvent() called");
    startPollingXcRate();
  }

  /**
   * Gets and saves xchange rate with a fixed, infrequent interval
   * - Starts with the configured default value
   * - Waits for a significant initial delay before the first API call
   * - Uses a fixed, very infrequent polling interval
   */
  public void startPollingXcRate() {
    Callable<String> call = () -> {
      // Ensure we have a default value
      if (usdMxn == null) {
        usdMxn = Double.parseDouble(POLLER_DEFAULT_XCHRATE);
        LOGGER.info("Starting with default USD/MXN rate: {}", usdMxn);
      }
      
      // Initial delay before first API call
      LOGGER.info("Waiting {} hours before first exchange rate API call", INITIAL_DELAY_HOURS);
      Thread.sleep(TimeUnit.HOURS.toMillis(INITIAL_DELAY_HOURS));
      
      // Fixed polling interval in milliseconds
      long pollingIntervalMs = TimeUnit.HOURS.toMillis(POLLING_INTERVAL_HOURS);
      
      while(true) {
        try {
          // Get current timestamp for logging
          String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
          
          // Get external exchange rate
          Double newRate = requestUsdMxn();
          
          if (newRate != null) {
            // Update the rate and log success
            usdMxn = newRate;
            lastSuccessfulPoll = LocalDateTime.now();
            LOGGER.info("[{}] Updated USD/MXN rate to: {}", timestamp, usdMxn);
          } else {
            // Log failure but keep using the current rate
            LOGGER.warn("[{}] Failed to get exchange rate, continuing to use current rate: {}", 
                       timestamp, usdMxn);
          }
          
          // Sleep until next poll (fixed interval)
          LOGGER.info("[{}] Next poll in {} hours", timestamp, POLLING_INTERVAL_HOURS);
          Thread.sleep(pollingIntervalMs);
          
        } catch (Exception e) {
          LOGGER.error("Error in exchange rate polling: {}", e.getMessage());
          try {
            // Brief pause before continuing the loop
            Thread.sleep(TimeUnit.MINUTES.toMillis(30));
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return "Interrupted";
          }
        }
      }
    };
    exchRateExe.submit(call);
  }

  /**
   * Makes an API call to get the current USD/MXN exchange rate
   * @return The current USD/MXN rate, or null if the API call fails
   */
  private Double requestUsdMxn() {
    ExchangeRate exchangeRate = new ExchangeRate();
    String result = null;
    try {
      RestClient defaultClient = RestClient.create();
      
      try {
        result = defaultClient.get()
          .uri(POLLER_URL)
          .retrieve()
          .body(String.class);
      } catch (Exception e) {
        LOGGER.warn("Error fetching exchange rate: {}", e.getMessage());
        return null;
      }
      
      // Validate the response
      if (result == null || result.isEmpty()) {
        LOGGER.warn("Empty response received from exchange rate API");
        return null;
      }
      
      LOGGER.debug("Exchange rate API response: {}", result);
      exchangeRate = objectMapper.readValue(result, ExchangeRate.class);
      
      // Validate the parsed object
      if (exchangeRate == null || exchangeRate.getQuotes() == null || exchangeRate.getQuotes().getUSDMXN() == null) {
        LOGGER.warn("Failed to parse exchange rate data or USDMXN is null");
        return null;
      }
      
      return exchangeRate.getQuotes().getUSDMXN();
    } catch (Exception e) {
      LOGGER.error("Error processing exchange rate: {}", e.getMessage());
      if (result != null) {
        LOGGER.error("Response was: {}", result);
      }
      return null;
    }
  }

  /**
   * Returns the current USD/MXN exchange rate
   * @return The current USD/MXN rate
   */
  public Double getUsdMxn() {
    return usdMxn;
  }
  
  /**
   * Returns the time of the last successful API poll
   * @return The time of the last successful poll, or null if never polled successfully
   */
  public LocalDateTime getLastSuccessfulPoll() {
    return lastSuccessfulPoll;
  }
}
