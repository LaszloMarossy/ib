package com.ibbe.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.ChunkInfo;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.entity.TradeSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.yml")
public class PerformanceAnalysisEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final CompletableFuture<List<TradeSnapshot>> receivedSnapshots = new CompletableFuture<>();

    @BeforeEach
    void setUp() throws Exception {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        
        stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String url = String.format("ws://localhost:%d/websocket", port);
        stompSession = stompClient.connect(
            url,
            new StompSessionHandlerAdapter() {}
        ).get(1, TimeUnit.SECONDS);
    }

    @Test
    void testTradeSnapshotDataFlow() throws ExecutionException, InterruptedException, TimeoutException {
        // Subscribe to the performance analysis topic
        stompSession.subscribe("/topic/performanceanalysis", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TradeSnapshot.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                TradeSnapshot snapshot = (TradeSnapshot) payload;
                List<TradeSnapshot> snapshots = receivedSnapshots.getNow(new ArrayList<>());
                snapshots.add(snapshot);
                receivedSnapshots.complete(snapshots);
            }
        });

        // Create and send a test configuration
        TradeConfig config = new TradeConfig(
            "test-config-123",
            "10",  // ups
            "10",  // downs
            true,  // useAvgBidVsAvgAsk
            true,  // useShortVsLongMovAvg
            true,  // useSumAmtUpVsDown
            true   // useTradePriceCloserToAskVsBuy
        );

        stompSession.send("/app/performanceanalysis", config);

        // Wait for and verify the received snapshots
        List<TradeSnapshot> snapshots = receivedSnapshots.get(5, TimeUnit.SECONDS);
        assertFalse(snapshots.isEmpty(), "Should receive at least one TradeSnapshot");

        // Verify the structure of received snapshots
        for (TradeSnapshot snapshot : snapshots) {
            // Verify basic fields
            assertNotNull(snapshot.getTimestamp(), "Timestamp should not be null");
            assertTrue(snapshot.getTradePrice() > 0, "Trade price should be positive");
            assertTrue(snapshot.getTradeAmount() > 0, "Trade amount should be positive");
            assertTrue(snapshot.getSequence() > 0, "Sequence should be positive");

            // Check for chunk information
            if (snapshot.getCompletedChunk() != null) {
                ChunkInfo chunk = snapshot.getCompletedChunk();
                assertTrue(chunk.getChunkNumber() > 0, "Chunk number should be positive");
                assertNotNull(chunk.getProfit(), "Chunk profit should not be null");
                assertTrue(chunk.getStartingTradePrice().compareTo(BigDecimal.ZERO) > 0, 
                    "Starting trade price should be positive");
                assertTrue(chunk.getEndingTradePrice().compareTo(BigDecimal.ZERO) > 0, 
                    "Ending trade price should be positive");
                assertTrue(chunk.getTradeCount() > 0, "Trade count should be positive");
                assertTrue(chunk.getStartTimeMillis() > 0, "Start time should be positive");
                assertTrue(chunk.getEndTimeMillis() > chunk.getStartTimeMillis(), 
                    "End time should be after start time");
            }

            // Check for pretend trade information
            if (snapshot.getPretendTrade() != null) {
                Trade pretendTrade = snapshot.getPretendTrade();
                assertTrue(pretendTrade.getTid() > 0, "Trade ID should be positive");
                assertNotNull(pretendTrade.getMakerSide(), "Maker side should not be null");
                assertTrue(pretendTrade.getPrice().compareTo(BigDecimal.ZERO) > 0, 
                    "Price should be positive");
                assertTrue(pretendTrade.getAmount().compareTo(BigDecimal.ZERO) > 0, 
                    "Amount should be positive");
                assertNotNull(pretendTrade.getCreatedAt(), "Created at timestamp should not be null");
            }

            // Verify balance information
            assertNotNull(snapshot.getCurrencyBalance(), "Currency balance should not be null");
            assertNotNull(snapshot.getCoinBalance(), "Coin balance should not be null");
            assertNotNull(snapshot.getAccountValueInChunk(), "Account value should not be null");
        }
    }

    @Test
    void testChunkDataFlow() throws ExecutionException, InterruptedException, TimeoutException {
        // Subscribe to the performance analysis topic
        stompSession.subscribe("/topic/performanceanalysis", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TradeSnapshot.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                TradeSnapshot snapshot = (TradeSnapshot) payload;
                if (snapshot.getCompletedChunk() != null) {
                    List<TradeSnapshot> snapshots = receivedSnapshots.getNow(new ArrayList<>());
                    snapshots.add(snapshot);
                    receivedSnapshots.complete(snapshots);
                }
            }
        });

        // Create and send a test configuration
        TradeConfig config = new TradeConfig(
            "test-config-123",
            "10",  // ups
            "10",  // downs
            true,  // useAvgBidVsAvgAsk
            true,  // useShortVsLongMovAvg
            true,  // useSumAmtUpVsDown
            true   // useTradePriceCloserToAskVsBuy
        );

        stompSession.send("/app/performanceanalysis", config);

        // Wait for and verify the received snapshots with chunks
        List<TradeSnapshot> snapshots = receivedSnapshots.get(5, TimeUnit.SECONDS);
        assertFalse(snapshots.isEmpty(), "Should receive at least one TradeSnapshot with a chunk");

        // Verify chunk data
        for (TradeSnapshot snapshot : snapshots) {
            ChunkInfo chunk = snapshot.getCompletedChunk();
            assertNotNull(chunk, "Chunk should not be null");
            
            // Verify chunk properties
            assertTrue(chunk.getChunkNumber() > 0, "Chunk number should be positive");
            assertNotNull(chunk.getProfit(), "Chunk profit should not be null");
            assertTrue(chunk.getStartingTradePrice().compareTo(BigDecimal.ZERO) > 0, 
                "Starting trade price should be positive");
            assertTrue(chunk.getEndingTradePrice().compareTo(BigDecimal.ZERO) > 0, 
                "Ending trade price should be positive");
            assertTrue(chunk.getTradeCount() > 0, "Trade count should be positive");
            assertTrue(chunk.getStartTimeMillis() > 0, "Start time should be positive");
            assertTrue(chunk.getEndTimeMillis() > chunk.getStartTimeMillis(), 
                "End time should be after start time");
        }
    }

    @Test
    void testPretendTradeDataFlow() throws ExecutionException, InterruptedException, TimeoutException {
        // Subscribe to the performance analysis topic
        stompSession.subscribe("/topic/performanceanalysis", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TradeSnapshot.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                TradeSnapshot snapshot = (TradeSnapshot) payload;
                if (snapshot.getPretendTrade() != null) {
                    List<TradeSnapshot> snapshots = receivedSnapshots.getNow(new ArrayList<>());
                    snapshots.add(snapshot);
                    receivedSnapshots.complete(snapshots);
                }
            }
        });

        // Create and send a test configuration
        TradeConfig config = new TradeConfig(
            "test-config-123",
            "10",  // ups
            "10",  // downs
            true,  // useAvgBidVsAvgAsk
            true,  // useShortVsLongMovAvg
            true,  // useSumAmtUpVsDown
            true   // useTradePriceCloserToAskVsBuy
        );

        stompSession.send("/app/performanceanalysis", config);

        // Wait for and verify the received snapshots with pretend trades
        List<TradeSnapshot> snapshots = receivedSnapshots.get(5, TimeUnit.SECONDS);
        assertFalse(snapshots.isEmpty(), "Should receive at least one TradeSnapshot with a pretend trade");

        // Verify pretend trade data
        for (TradeSnapshot snapshot : snapshots) {
            Trade pretendTrade = snapshot.getPretendTrade();
            assertNotNull(pretendTrade, "Pretend trade should not be null");
            
            // Verify pretend trade properties
            assertTrue(pretendTrade.getTid() > 0, "Trade ID should be positive");
            assertNotNull(pretendTrade.getMakerSide(), "Maker side should not be null");
            assertTrue(pretendTrade.getPrice().compareTo(BigDecimal.ZERO) > 0, 
                "Price should be positive");
            assertTrue(pretendTrade.getAmount().compareTo(BigDecimal.ZERO) > 0, 
                "Amount should be positive");
            assertNotNull(pretendTrade.getCreatedAt(), "Created at timestamp should not be null");
        }
    }
} 