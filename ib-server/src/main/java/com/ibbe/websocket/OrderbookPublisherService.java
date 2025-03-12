package com.ibbe.websocket;

import com.ibbe.entity.BitsoDataAggregator;
import com.ibbe.entity.OrderBook;
import com.ibbe.entity.OrderBookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderbookPublisherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderbookPublisherService.class);
    private final AtomicLong messageCount = new AtomicLong(0);
    private long startTime;

    @Autowired
    private BitsoDataAggregator bitsoDataAggregator;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @PostConstruct
    public void init() {
        startTime = System.currentTimeMillis();
        LOGGER.info("OrderbookPublisherService initialized - will publish orderbook data to /topic/orderbook every 2 seconds");
    }

    /**
     * Publishes orderbook data to the STOMP broker every 2 seconds
     */
    @Scheduled(fixedRate = 2000)
    public void publishOrderbookData() {
        try {
            OrderBookPayload obp = bitsoDataAggregator.getOrderbookPayload();
            if (obp != null) {
                OrderBook orderBook = new OrderBook(true, obp);
                
                // Add recent trades to the orderbook
                Object[] recentTrades = bitsoDataAggregator.getRecentTrades();
                // If there are too many trades, limit to the most recent 20
                if (recentTrades != null && recentTrades.length > 20) {
                    Object[] limitedTrades = new Object[20];
                    System.arraycopy(recentTrades, recentTrades.length - 20, limitedTrades, 0, 20);
                    orderBook.setTrades(limitedTrades);
                } else {
                    orderBook.setTrades(recentTrades);
                }
                
                // Publish to the STOMP topic
                messagingTemplate.convertAndSend("/topic/orderbook", orderBook);
                
                // Log every 100 messages or at debug level
                long count = messageCount.incrementAndGet();
                if (count % 100 == 0) {
                    long uptime = (System.currentTimeMillis() - startTime) / 1000;
                    LOGGER.info("Published {} orderbook messages to /topic/orderbook in {} seconds", count, uptime);
                } else {
                    LOGGER.debug("Published orderbook data to /topic/orderbook (message #{})", count);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error publishing orderbook data", e);
        }
    }
} 