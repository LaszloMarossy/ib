package com.ibbe.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.BitsoDataAggregator;
import com.ibbe.entity.OrderBook;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous single-threaded executor continuously polling orderbook from Bitso
 * - called by ServletInitializer.
 */
@Component
public class BitsoOrderbookPoller extends AsyncExecutor implements ApplicationListener<ApplicationReadyEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BitsoOrderbookPoller.class.getName());
  @Value("${poller.interval.orderbook}")
  private String POLLER_INTERVAL_ORDERBOOK;
  @Value("${bitso.get.orderbook.url}")
  private String BITSO_GET_ORDERBOOK_URL;
  @Autowired
  BitsoDataAggregator bitsoDataAggregator;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private int orderbookPollingIntervalSec = 1;

  // declaring this here vs. the base class as then there would be only a single thread pool for populate and consume
  private ExecutorService orderbookPollingExe;

  public BitsoOrderbookPoller() {
    orderbookPollingExe = Executors.newSingleThreadExecutor();
  }

  @PostConstruct
  public void init() {
    orderbookPollingIntervalSec = Integer.parseInt(POLLER_INTERVAL_ORDERBOOK);
  }

  /**
   * called by boot automatically when it is initialized
   * @param event
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    pollBitsoOrderbook();
  }


  /**
   * main method called by system startup `ServletInitializer`.
   * Starts refreshing the internal recent trades list
   * with polled trades REST info.
   *
   * @return result future string - ignored for now
   */
  private void pollBitsoOrderbook() {
    LOGGER.info("starting to poll the orderbook from Bitso");
    Callable<String> call = () -> {
      String result = "0";
      while (true) {
        try {
          RestClient restClient = RestClient.create();
          result = restClient.get()
                  .uri(BITSO_GET_ORDERBOOK_URL)
                  .retrieve()
                  .body(String.class);

          OrderBook orderBook = objectMapper.readValue(result, OrderBook.class);
          bitsoDataAggregator.setOrderBookPayload(orderBook.getPayload());
          //run once only at time of export from Bitso
          bitsoDataAggregator.setOrderBookCurrencyToUSD();
//          LOGGER.info("getting bitso orderbook ");
          Thread.sleep(orderbookPollingIntervalSec * 1000L);
        } catch (Exception e) {
          LOGGER.error("FAILED ON MESSAGE " + result);
          e.printStackTrace();
          LOGGER.error(e.getMessage());
          Thread.sleep(3000);
          pollBitsoOrderbook();
        }
      }
    };
    orderbookPollingExe.submit(call);
  }

}
