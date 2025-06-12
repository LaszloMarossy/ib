package com.ibbe.websocket;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.entity.TradeSnapshot;
import com.ibbe.executor.BasicTrader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class PerformanceAnalysisEndpointTest {

    private WebSocketSession mockWebSocketSession;
    private ObjectMapper testObjectMapper; 

    @Captor
    ArgumentCaptor<TextMessage> textMessageCaptor;

    @BeforeEach
    void setUp() {
        testObjectMapper = new ObjectMapper();
        testObjectMapper.registerModule(new JavaTimeModule());
        testObjectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        mockWebSocketSession = Mockito.mock(WebSocketSession.class);
        lenient().when(mockWebSocketSession.isOpen()).thenReturn(true);
    }

    private List<Trade> loadTradesFromFile(String jsonFileName) throws IOException {
        InputStream jsonStream = getClass().getResourceAsStream(jsonFileName);
        if (jsonStream == null) {
            throw new FileNotFoundException("Cannot find test data file: " + jsonFileName + " in classpath. Ensure it starts with '/' and is in src/test/resources.");
        }
        KafkaTestRecordsWrapper wrapper = testObjectMapper.readValue(jsonStream, KafkaTestRecordsWrapper.class);
        return wrapper.getTrades();
    }

    private List<TradeSnapshot> deserializeCapturedMessages(List<TextMessage> messages) throws IOException {
        List<TradeSnapshot> snapshots = new ArrayList<>();
        if (messages == null) return snapshots;
        for (TextMessage message : messages) {
            snapshots.add(this.testObjectMapper.readValue(message.getPayload(), TradeSnapshot.class));
        }
        return snapshots;
    }
    
    @Test
    void testAverageCalculations_from_kafkaTestRecordsFile() throws IOException {
        TradeConfig testConfig = new TradeConfig("xyz", "3", "2", false, false, false, false);
        BasicTrader basicTrader = new BasicTrader(testConfig);
        AtomicBoolean isRunning = new AtomicBoolean(true);
        PerformanceAnalysisEndpoint endpoint = new PerformanceAnalysisEndpoint();

        lenient().when(mockWebSocketSession.getId()).thenReturn("test-session-avg-calc");

        List<Trade> inputTrades = loadTradesFromFile("/kafka-test-records.json");
        assertFalse(inputTrades.isEmpty(), "Should load trades from kafka-test-records.json");
        System.out.println("Verified: Trades loaded successfully from kafka-test-records.json.");
        assertEquals(6, inputTrades.size(), "Should have loaded 6 trades from kafka-test-records.json");
        System.out.println("Verified: Exactly 6 trades loaded.");

        for (Trade trade : inputTrades) {
             endpoint.processKafkaTradeForPerformanceAnalysis(
                 trade, 
                 mockWebSocketSession,
                 isRunning,
                 basicTrader,
                 this.testObjectMapper
             );
        }
        
        verify(mockWebSocketSession, times(2)).sendMessage(textMessageCaptor.capture());
        System.out.println("Verified: sendMessage called exactly 2 times.");
        List<TextMessage> actualSentMessages = textMessageCaptor.getAllValues();
        List<TradeSnapshot> capturedSnapshots = deserializeCapturedMessages(actualSentMessages);

        assertEquals(2, capturedSnapshots.size(), "Expected 2 snapshots (1 buy, 1 sell) to be captured.");
        System.out.println("Verified: Captured exactly 2 snapshot messages.");
        
        TradeSnapshot pretendBuy  = capturedSnapshots.get(0);
        TradeSnapshot pretendSell = capturedSnapshots.get(1);
        
        // --- Assertions for first pretend trade (BUY) ---
        assertEquals(4, pretendBuy.getTradeId(), "Pretend BUY Trade ID mismatch.");
        System.out.println("BUY @ TID 4: Verified Trade ID.");
        assertNotNull(pretendBuy.getPretendTrade(), "PretendTrade object should not be null for BUY operation.");
        System.out.println("BUY @ TID 4: Verified PretendTrade object exists.");
        assertEquals("PRETEND buy", pretendBuy.getPretendTrade().getMakerSide(), "PretendTrade makerSide for BUY operation is incorrect.");
        System.out.println("BUY @ TID 4: Verified makerSide is 'PRETEND buy'.");
        assertEquals(10560, pretendBuy.getAvgAskPrice(), "AvgAskPrice for pretend BUY (TID 4) is wrong.");
        System.out.println("BUY @ TID 4: Verified average ask price.");
        assertEquals(0.024, pretendBuy.getAvgAskAmount(), "AvgAskAmount for pretend BUY (TID 4) is wrong.");
        System.out.println("BUY @ TID 4: Verified average ask amount.");
        assertEquals(10490, pretendBuy.getAvgBidPrice(), "AvgBidPrice for pretend BUY (TID 4) is wrong.");
        System.out.println("BUY @ TID 4: Verified average bid price.");
        assertEquals(0.024, pretendBuy.getAvgBidAmount(), "AvgBidAmount for pretend BUY (TID 4) is wrong.");
        System.out.println("BUY @ TID 4: Verified average bid amount.");
        // priceCloserToBestAsk = distanceToAsk - distanceToBid; negative value means closer to bid (buy signal)
        assertTrue(pretendBuy.getPriceCloserToBestAsk() < 0, "getPriceCloserToBestAsk for BUY should be negative.");
        System.out.println("BUY @ TID 4: Verified priceCloserToBestAsk is negative. Value = " + pretendBuy.getPriceCloserToBestAsk());

        // --- Assertions for second pretend trade (SELL) ---
        assertEquals(6, pretendSell.getTradeId(), "Pretend SELL Trade ID mismatch.");
        System.out.println("SELL @ TID 6: Verified Trade ID.");
        assertNotNull(pretendSell.getPretendTrade(), "PretendTrade object should not be null for SELL operation.");
        System.out.println("SELL @ TID 6: Verified PretendTrade object exists.");
        assertEquals("PRETEND sell", pretendSell.getPretendTrade().getMakerSide(), "PretendTrade makerSide for SELL operation is incorrect.");
        System.out.println("SELL @ TID 6: Verified makerSide is 'PRETEND sell'.");
        assertEquals(10540, pretendSell.getAvgAskPrice(), "AvgAskPrice for pretend SELL (TID 6) is wrong.");
        System.out.println("SELL @ TID 6: Verified average ask price.");
        assertEquals(0.03, pretendSell.getAvgAskAmount(), "AvgAskAmount for pretend SELL (TID 6) is wrong.");
        System.out.println("SELL @ TID 6: Verified average ask amount.");
        assertEquals(10470, pretendSell.getAvgBidPrice(), "AvgBidPrice for pretend SELL (TID 6) is wrong.");
        System.out.println("SELL @ TID 6: Verified average bid price.");
        assertEquals(0.03, pretendSell.getAvgBidAmount(), "AvgBidAmount for pretend SELL (TID 6) is wrong.");
        System.out.println("SELL @ TID 6: Verified average bid amount.");
        // priceCloserToBestAsk = distanceToAsk - distanceToBid; positive value means closer to ask (sell signal)
        assertTrue(pretendSell.getPriceCloserToBestAsk() > 0, "getPriceCloserToBestAsk for SELL should be positive.");
        System.out.println("SELL @ TID 6: Verified priceCloserToBestAsk is positive. Value = " + pretendSell.getPriceCloserToBestAsk());
    }

    @Test
    void testPretendTradesWithExtendedMovingAverageData() throws IOException {
        TradeConfig testConfig = new TradeConfig("xyz-ma-test", "3", "2", false, false, false, false);
        BasicTrader basicTrader = new BasicTrader(testConfig);
        AtomicBoolean isRunning = new AtomicBoolean(true);
        PerformanceAnalysisEndpoint endpoint = new PerformanceAnalysisEndpoint();

        lenient().when(mockWebSocketSession.getId()).thenReturn("test-session-ma-calc");

        List<Trade> inputTrades = loadTradesFromFile("/kafka-test-records-moving-averages.json");
        assertEquals(25, inputTrades.size(), "Should load 25 trades from the extended file.");
        System.out.println("Verified: Exactly 25 trades loaded from extended data file.");

        for (Trade trade : inputTrades) {
            endpoint.processKafkaTradeForPerformanceAnalysis(
                trade,
                mockWebSocketSession,
                isRunning,
                basicTrader,
                this.testObjectMapper
            );
        }

        int expectedPretendTradeMessages = 6; 
        verify(mockWebSocketSession, times(expectedPretendTradeMessages)).sendMessage(textMessageCaptor.capture());
        System.out.println("Verified: sendMessage called exactly 6 times for pretend trades.");
        
        List<TradeSnapshot> capturedSnapshots = deserializeCapturedMessages(textMessageCaptor.getAllValues());
        assertEquals(expectedPretendTradeMessages, capturedSnapshots.size(), "Incorrect number of TradeSnapshot messages captured for pretend trades.");
        System.out.println("Verified: Captured exactly 6 snapshot messages for pretend trades.");

        // --- Assertions for each pretend trade from the extended data file ---

        TradeSnapshot buy1 = capturedSnapshots.get(0);
        assertEquals(4, buy1.getTradeId(), "Pretend trade 1 (BUY) should have TID 4.");
        System.out.println("Extended Test - BUY @ TID 4: Verified Trade ID.");
        assertNotNull(buy1.getPretendTrade(), "Pretend trade 1 (BUY @ TID 4) object missing.");
        System.out.println("Extended Test - BUY @ TID 4: Verified PretendTrade object exists.");
        assertEquals("PRETEND buy", buy1.getPretendTrade().getMakerSide(), "Pretend trade 1 (BUY @ TID 4) makerSide incorrect.");
        System.out.println("Extended Test - BUY @ TID 4: Verified makerSide is 'PRETEND buy'.");

        TradeSnapshot sell1 = capturedSnapshots.get(1);
        assertEquals(6, sell1.getTradeId(), "Pretend trade 2 (SELL) should have TID 6.");
        System.out.println("Extended Test - SELL @ TID 6: Verified Trade ID.");
        assertNotNull(sell1.getPretendTrade(), "Pretend trade 2 (SELL @ TID 6) object missing.");
        System.out.println("Extended Test - SELL @ TID 6: Verified PretendTrade object exists.");
        assertEquals("PRETEND sell", sell1.getPretendTrade().getMakerSide(), "Pretend trade 2 (SELL @ TID 6) makerSide incorrect.");
        System.out.println("Extended Test - SELL @ TID 6: Verified makerSide is 'PRETEND sell'.");

        TradeSnapshot buy2 = capturedSnapshots.get(2);
        assertEquals(12, buy2.getTradeId(), "Pretend trade 3 (BUY) should have TID 12.");
        System.out.println("Extended Test - BUY @ TID 12: Verified Trade ID.");
        assertNotNull(buy2.getPretendTrade(), "Pretend trade 3 (BUY @ TID 12) object missing.");
        System.out.println("Extended Test - BUY @ TID 12: Verified PretendTrade object exists.");
        assertEquals("PRETEND buy", buy2.getPretendTrade().getMakerSide(), "Pretend trade 3 (BUY @ TID 12) makerSide incorrect.");
        System.out.println("Extended Test - BUY @ TID 12: Verified makerSide is 'PRETEND buy'.");

        TradeSnapshot sell2 = capturedSnapshots.get(3);
        assertEquals(16, sell2.getTradeId(), "Pretend trade 4 (SELL) should have TID 16.");
        System.out.println("Extended Test - SELL @ TID 16: Verified Trade ID.");
        assertNotNull(sell2.getPretendTrade(), "Pretend trade 4 (SELL @ TID 16) object missing.");
        System.out.println("Extended Test - SELL @ TID 16: Verified PretendTrade object exists.");
        assertEquals("PRETEND sell", sell2.getPretendTrade().getMakerSide(), "Pretend trade 4 (SELL @ TID 16) makerSide incorrect.");
        System.out.println("Extended Test - SELL @ TID 16: Verified makerSide is 'PRETEND sell'.");
        
        TradeSnapshot buy3 = capturedSnapshots.get(4);
        assertEquals(21, buy3.getTradeId(), "Pretend trade 5 (BUY) should have TID 21.");
        System.out.println("Extended Test - BUY @ TID 21: Verified Trade ID.");
        assertNotNull(buy3.getPretendTrade(), "Pretend trade 5 (BUY @ TID 21) object missing.");
        System.out.println("Extended Test - BUY @ TID 21: Verified PretendTrade object exists.");
        assertEquals("PRETEND buy", buy3.getPretendTrade().getMakerSide(), "Pretend trade 5 (BUY @ TID 21) makerSide incorrect.");
        System.out.println("Extended Test - BUY @ TID 21: Verified makerSide is 'PRETEND buy'.");

        TradeSnapshot sell3 = capturedSnapshots.get(5);
        assertEquals(24, sell3.getTradeId(), "Pretend trade 6 (SELL) should have TID 24.");
        System.out.println("Extended Test - SELL @ TID 24: Verified Trade ID.");
        assertNotNull(sell3.getPretendTrade(), "Pretend trade 6 (SELL @ TID 24) object missing.");
        System.out.println("Extended Test - SELL @ TID 24: Verified PretendTrade object exists.");
        assertEquals("PRETEND sell", sell3.getPretendTrade().getMakerSide(), "Pretend trade 6 (SELL @ TID 24) makerSide incorrect.");
        System.out.println("Extended Test - SELL @ TID 24: Verified makerSide is 'PRETEND sell'.");
    }
}

class KafkaTestRecord {
    private Trade value;
    public Trade getValue() { return value; }
    public void setValue(Trade value) { this.value = value; }
}

class KafkaTestRecordsWrapper {
    private List<KafkaTestRecord> records;
    public List<KafkaTestRecord> getRecords() { return records; }
    public void setRecords(List<KafkaTestRecord> records) { this.records = records; }

    public List<Trade> getTrades() {
        if (records == null) return new ArrayList<>();
        return records.stream()
                .filter(record -> record != null && record.getValue() != null)
                .map(KafkaTestRecord::getValue)
                .collect(Collectors.toList());
    }
}