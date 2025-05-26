package com.ibbe.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.entity.TradeSnapshot;
import com.ibbe.entity.ChunkInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class TestDataLoader {
    private static final Logger logger = LoggerFactory.getLogger(TestDataLoader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static class TestData {
        private List<TradeSnapshot> records;
        private List<ChunkInfo> chunks;
        
        public List<TradeSnapshot> getRecords() {
            return records;
        }
        
        public List<ChunkInfo> getChunks() {
            return chunks;
        }
    }
    
    public static TestData loadTestData() {
        try (InputStream is = TestDataLoader.class.getResourceAsStream("/kafka-test-records.json")) {
            if (is == null) {
                throw new RuntimeException("Could not find test data file: kafka-test-records.json");
            }
            return objectMapper.readValue(is, TestData.class);
        } catch (IOException e) {
            logger.error("Error loading test data", e);
            throw new RuntimeException("Failed to load test data", e);
        }
    }
    
    public static List<TradeSnapshot> loadTradeRecords() {
        return loadTestData().getRecords();
    }
    
    public static List<ChunkInfo> loadChunks() {
        return loadTestData().getChunks();
    }
} 