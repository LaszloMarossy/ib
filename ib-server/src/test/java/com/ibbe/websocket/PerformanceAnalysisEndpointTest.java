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
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private PerformanceAnalysisEndpoint performanceAnalysisEndpoint;
    private ObjectMapper testObjectMapper;

    @Captor
    ArgumentCaptor<TextMessage> textMessageCaptor;

    @BeforeEach
    void setUp() {
        testObjectMapper = new ObjectMapper();
        testObjectMapper.registerModule(new JavaTimeModule());
        testObjectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        performanceAnalysisEndpoint = new PerformanceAnalysisEndpoint();

        mockWebSocketSession = Mockito.mock(WebSocketSession.class);
    }

    private List<Trade> loadTradesFromFile(String jsonFileName) throws IOException {
        InputStream jsonStream = getClass().getResourceAsStream(jsonFileName);
        if (jsonStream == null) {
            throw new FileNotFoundException("Cannot find test data file: " + jsonFileName + " in classpath. Ensure it starts with '/' and is in src/test/resources.");
        }
        KafkaTestRecordsWrapper wrapper = testObjectMapper.readValue(jsonStream, KafkaTestRecordsWrapper.class);
        return wrapper.getTrades();
    }

    private void deserializeAndAssertAverages(
            List<TextMessage> capturedWebSocketMessages,
            List<Trade> inputTrades) throws IOException {

    }

    @Test
    void testAverageCalculations_from_kafkaTestRecordsFile() throws IOException {
        // Configure for 1 buy (on 3rd consecutive up) and 1 sell (on 2nd consecutive down)
        // All other strategy flags are false to isolate the up/down tick counting logic.
        TradeConfig testConfig = new TradeConfig("xyz", "3", "2", false, false, false, false);
        BasicTrader basicTrader = new BasicTrader(testConfig);
        AtomicBoolean isRunning = new AtomicBoolean(true);

        // Made this stubbing lenient because isRunning=false will cause the method to return early,
        // potentially before isOpen() is checked, due to short-circuit evaluation.
        lenient().when(mockWebSocketSession.isOpen()).thenReturn(true);
//        when(mockWebSocketSession.getId()).thenReturn("test-session-avg-calc");

        List<Trade> inputTrades = loadTradesFromFile("/kafka-test-records.json");
        assertFalse(inputTrades.isEmpty(), "Should load trades from kafka-test-records.json");
        assertEquals(6, inputTrades.size(), "Should have loaded 6 trades from kafka-test-records.json");

        for (Trade trade : inputTrades) {
            performanceAnalysisEndpoint.processKafkaTradeForPerformanceAnalysis(
                    trade, mockWebSocketSession, isRunning, basicTrader, testObjectMapper
            );
        }
        
        // Expect exactly 2 messages: 1 buy and 1 sell based on the TradeConfig and upcoming JSON data
        verify(mockWebSocketSession, times(2)).sendMessage(textMessageCaptor.capture());

        List<TextMessage> actualSentMessages = textMessageCaptor.getAllValues();
        
        List<TradeSnapshot> capturedSnapshots = new ArrayList<>();

        if (actualSentMessages.isEmpty()) {
            fail("No WebSocket messages were captured by ArgumentCaptor (and messages were expected for this assertion path).");
        }

        for (TextMessage textMessage : actualSentMessages) {
            TradeSnapshot capturedSnapshot = this.testObjectMapper.readValue(textMessage.getPayload(), TradeSnapshot.class);
            capturedSnapshots.add(capturedSnapshot);
        }

        assertEquals(2, capturedSnapshots.size());
        // first pretendTrade should be triggered by the record with TID 4 (at TICK_UP3)
        TradeSnapshot pretendBuy  = capturedSnapshots.get(0);
        TradeSnapshot pretendSell = capturedSnapshots.get(1);
        assertEquals(4, pretendBuy.getTradeId());
        assertEquals(6, pretendSell.getTradeId());

        assertNotNull(pretendBuy.getPretendTrade(), "Pretend Trade not found for buy");
        assertEquals("PRETEND buy", pretendBuy.getPretendTrade().getMakerSide(), "Pretend Trade type is not 'buy'");
        assertEquals(10560, pretendBuy.getAvgAskPrice(), "getAvgAskPrice for TID " + pretendBuy.getTradeId() + " is wrong.");
        System.out.println("* pretendBuy AvgAskPrice correct");
        assertEquals(0.024, pretendBuy.getAvgAskAmount(), "getAvgAskAmount for TID " + pretendBuy.getTradeId() + " is wrong.");
        System.out.println("* pretendBuy getAvgAskAmount correct");
        assertEquals(10490, pretendBuy.getAvgBidPrice(), "getAvgBidPrice for TID " + pretendBuy.getTradeId() + " is wrong.");
        System.out.println("* pretendSell getAvgBidPrice correct");
        assertEquals(0.024, pretendBuy.getAvgBidAmount(), "getAvgBidAmount for TID " + pretendBuy.getTradeId() + " is wrong.");
        System.out.println("* pretendSell getAvgBidAmount correct");
        assertEquals(10530, pretendBuy.getTradePrice(), "getTradePrice for TID " + pretendBuy.getTradeId() + " is wrong.");
        System.out.println("* pretendBuy getTradePrice correct");
        // priceCloserToBestAsk = distanceToAsk - distanceToBid; so if positive, closer to ask
        assertTrue(pretendBuy.getPriceCloserToBestAsk() < 0, "getPriceCloserToBestAsk is not negative: ask 540 trade 530 bid 510");
        System.out.println("* pretendSell getPriceCloserToBestAsk = " + pretendBuy.getPriceCloserToBestAsk());

        assertNotNull(pretendSell.getPretendTrade(), "Pretend Trade not found for sell");
        assertEquals("PRETEND sell", pretendSell.getPretendTrade().getMakerSide(), "Pretend Trade type is not 'buy'");
        assertEquals(10540, pretendSell.getAvgAskPrice(), "getAvgAskPrice for TID " + pretendSell.getTradeId() + " is wrong.");
        System.out.println("* pretendSell getAvgAskPrice correct");
        assertEquals(0.03, pretendSell.getAvgAskAmount(), "getAvgAskAmount for TID " + pretendSell.getTradeId() + " is wrong.");
        System.out.println("* pretendSell getAvgAskAmount correct");
        assertEquals(10470, pretendSell.getAvgBidPrice(), "getAvgBidPrice for TID " + pretendSell.getTradeId() + " is wrong.");
        System.out.println("* pretendSell getAvgBidPrice correct");
        assertEquals(0.03, pretendSell.getAvgBidAmount(), "getAvgBidAmount for TID " + pretendSell.getTradeId() + " is wrong.");
        System.out.println("* pretendSell getTradePrice correct");
        assertEquals(10500, pretendSell.getTradePrice(), "getTradePrice for TID " + pretendSell.getTradeId() + " is wrong.");
        System.out.println("* pretendSell getTradePrice correct");
        // priceCloserToBestAsk = distanceToAsk - distanceToBid; so if positive, closer to ask
        assertTrue(pretendSell.getPriceCloserToBestAsk() > 0, "getPriceCloserToBestAsk is not positive: ask 520 trade 500 bid 490");
        System.out.println("* pretendSell getPriceCloserToBestAsk = " + pretendSell.getPriceCloserToBestAsk());
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