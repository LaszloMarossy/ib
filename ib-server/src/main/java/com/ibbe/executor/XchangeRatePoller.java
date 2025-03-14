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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Asynchronous single-threaded executor of refreshing internal copy of xchange rate;
 * - called by StartupListener.
 * - called by the AppController REST method
 */
@Component
public class XchangeRatePoller extends AsyncExecutor implements ApplicationListener<ApplicationReadyEvent> {

  private static XchangeRatePoller xchangeRatePoller;

  private static final Logger LOGGER = LoggerFactory.getLogger(XchangeRatePoller.class.getName());
  @Value("${poller.interval.xchrate}")
  private String POLLER_INTERVAL_XCHRATE;
  @Value("${poller.default.xchrate}")
  private String POLLER_DEFAULT_XCHRATE;
  @Value("${xchange.url}")
  private String POLLER_URL;
  private static ObjectMapper objectMapper = new ObjectMapper();
  private static int intervalSec = 10;
  // set default value to xchange rate as api may be limited
  private Double usdMxn = 0d;
  private final Environment env;

  // declaring this here vs. the base class as then there would be only a single thread pool for populate and consume
  private ExecutorService exchRateExe;

  @Autowired
  public XchangeRatePoller(Environment env)   {
    this.env = env;
    LOGGER.info("$$$$$$$$ XchangeRatePoller constructor");
    exchRateExe = Executors.newSingleThreadExecutor();
  }

  /**
   * method to make sure Autowire stuff properly set after
   * construction
   */
  @PostConstruct
  public void init() {
    intervalSec = Integer.parseInt(POLLER_INTERVAL_XCHRATE);
    usdMxn = Double.parseDouble(POLLER_DEFAULT_XCHRATE);
    LOGGER.info("XchangeRatePoller postconstruct " + intervalSec + " default " + usdMxn + " using Xchange " + POLLER_URL);
  }

  /**
   * method called by Spring when boot started up
   * @param event
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    LOGGER.info("onApplicationEvent() called");
    startPollingXcRate();
//    while(true) {
//      try {
//        Thread.sleep(8000);
//      } catch (InterruptedException e) {
//        e.printStackTrace();
//        throw new RuntimeException(e);
//      }
//      LOGGER.info("going on..");
//    }
  }

  /**
   * gets and saves xchange rate every X seconds
   */
  public void startPollingXcRate() {
    Callable<String> call = () -> {
      if (usdMxn == null) {
        usdMxn = Double.parseDouble(POLLER_DEFAULT_XCHRATE);
        LOGGER.info("starting with default UsdMxn " + usdMxn);
      }
      while(true) {
        try {
          Thread.sleep(intervalSec * 1000);
          
          // get external trades once
          LOGGER.info("$$$ getting exchange rate ");
          usdMxn = requestUsdMxn();
          if (usdMxn == null) {
            usdMxn = Double.parseDouble(POLLER_DEFAULT_XCHRATE);
            LOGGER.info("using default UsdMxn " + usdMxn);
          }
          LOGGER.info("$$$ USDMXN=".concat(usdMxn.toString()));
        } catch (Exception e) {
          LOGGER.error("Error in exchange rate polling: {}", e.getMessage());
          try {
            Thread.sleep(3000); // Brief pause before continuing the loop
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return "Interrupted";
          }
        }
      }
    };
    exchRateExe.submit(call);
  }

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

  public Double getUsdMxn() {
    return usdMxn;
  }

//  @Override
//  public void afterPropertiesSet() throws Exception {
//    LOGGER.info(Arrays.asList(env.getDefaultProfiles()).toString());
//    System.out.println("&&&&&&&&&&&&&&&&&&");
//
//  }
}
