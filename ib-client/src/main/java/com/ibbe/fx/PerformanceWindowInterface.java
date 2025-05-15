package com.ibbe.fx;

import com.ibbe.entity.PerformanceData;
import java.math.BigDecimal;
import java.util.List;

/**
 * Interface defining methods required by the PerformanceAnalysisClient
 * for any window that wants to display performance analysis data.
 */
public interface PerformanceWindowInterface {
    
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
     * Called when the client has new data available
     */
    default void onNewDataAvailable() {
        // Default implementation is empty
    }
    
    /**
     * Update the balance display with current values
     */
    default void updateBalanceDisplay(BigDecimal currency, BigDecimal coin, BigDecimal profit) {
        // Default implementation is empty
    }
} 