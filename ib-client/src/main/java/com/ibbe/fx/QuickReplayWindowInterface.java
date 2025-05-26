package com.ibbe.fx;

import com.ibbe.entity.ChunkInfo;
import com.ibbe.entity.PerformanceData;
import java.math.BigDecimal;
import java.util.List;

/**
 * Interface defining methods required by the PerformanceAnalysisClient
 * for any window that wants to display performance analysis data.
 */
public interface PerformanceWindowInterface {
    
    /**
     * Called when a new chunk is received from the server.
     * @param chunk The new chunk data
     */
    void onNewChunk(ChunkInfo chunk);
    
    /**
     * Called when a new pretend trade is received from the server.
     * @param trade The new pretend trade data
     */
    void onNewPretendTrade(PerformanceData trade);
    
    /**
     * Gets the mode of operation (1 for visual replay, 2 for quick replay)
     */
    int getMode();
    
    /**
     * Gets the next sequence number for data points
     */
    int nextSequenceNumber();
    
    /**
     * Reset the sequence counter
     */
    void resetSequence();
    
    /**
     * Update status message in the UI
     */
    void updateStatus(String status);
    
    /**
     * Update status message in the UI with an error flag
     * 
     * @param status The status message to display
     * @param isError Whether the status message represents an error
     */
    void updateStatus(String status, boolean isError);
    
    /**
     * Process a list of window data and update the display
     */
    void updateTradeHistory(List<PerformanceData> windowData);
    
    /**
     * Update the balance display with current values
     */
    void updateBalanceDisplay(BigDecimal currency, BigDecimal coin, BigDecimal profit);
} 