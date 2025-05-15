package com.ibbe.fx;

import com.ibbe.entity.PerformanceData;
import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.util.PropertiesUtil;
import com.ibbe.websocket.PerformanceAnalysisClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Comparator;

/**
 * Java FX window client for quick performance analysis;
 * - connects to the server to analyze performance of a given configuration in quick mode
 * - focuses on trade history display and final performance metrics
 */
public class QuickReplayWindow extends Application implements PerformanceWindowInterface {

    // Always mode 2 (quick replay) for this window
    private final int mode = 2;
    
    // Maximum number of trade history entries to keep
    private static final int MAX_TRADE_HISTORY = 100;
    
    private TextField upsField = new TextField();
    private TextField downsField = new TextField();
    private Button startButton = new Button("Start Quick Replay");
    private Label statusLabel = new Label("Status: Ready");
    
    private PerformanceAnalysisClient performanceClient;
    
    // Sequence number tracking
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    
    // Trade history components
    private VBox tradeHistoryContainer = new VBox(5);
    private final SimpleDateFormat tradeHistoryDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Balance and profit tracking
    private Label balanceLabel = new Label("Starting Balance: $0.00 | 0.00000000 BTC");
    private Label currentBalanceLabel = new Label("Current Balance: $0.00 | 0.00000000 BTC");
    private Label profitLabel = new Label("Profit: $0.00");
    private Label usdBalanceLabel = new Label("USD Balance: $0.00");
    private Label coinBalanceLabel = new Label("Coin Balance: 0.00000000 BTC");
    private BigDecimal startingCurrencyBalance = null;
    private BigDecimal startingCoinBalance = null;
    private BigDecimal currentCurrencyBalance = BigDecimal.ZERO;
    private BigDecimal currentCoinBalance = BigDecimal.ZERO;
    private BigDecimal currentProfit = BigDecimal.ZERO;
    
    // Flag to prevent recursive updates
    private AtomicBoolean isProcessing = new AtomicBoolean(false);
    
    // Add a version label to track changes
    private Label versionLabel = new Label("Quick Replay v1.0");
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Quick Performance Analysis");
        
        // Style the version label
        versionLabel.setFont(new Font("Arial", 12));
        versionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555555;");
        versionLabel.setPadding(new Insets(5, 10, 5, 10));
        
        // Set up input fields
        upsField.setText(PropertiesUtil.getProperty("trade.up_m"));
        downsField.setText(PropertiesUtil.getProperty("trade.down_n"));
        upsField.setPrefWidth(100);
        downsField.setPrefWidth(100);
        
        // Set up labels
        Label upsLabel = new Label("Ups:");
        Label downsLabel = new Label("Downs:");
        upsLabel.setFont(new Font("Arial", 14));
        downsLabel.setFont(new Font("Arial", 14));
        
        // Set up start button
        startButton.setPrefSize(200, 30);
        startButton.setFont(new Font("Arial", 14));
        startButton.setOnAction(event -> startQuickReplayAnalysis());
        
        // Set up status label
        statusLabel.setFont(new Font("Arial", 14));
        
        // Create input layout
        HBox inputBox = new HBox(10, upsLabel, upsField, downsLabel, downsField, startButton);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.setPadding(new Insets(10));
        
        // Create a container for the version label and status label
        HBox statusBox = new HBox(10);
        statusBox.getChildren().addAll(versionLabel, statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPadding(new Insets(5, 10, 5, 10));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        // Style the balance and profit labels
        balanceLabel.setFont(new Font("Arial", 16));
        balanceLabel.setStyle("-fx-font-weight: bold;");
        currentBalanceLabel.setFont(new Font("Arial", 16));
        currentBalanceLabel.setStyle("-fx-font-weight: bold;");
        profitLabel.setFont(new Font("Arial", 20));
        profitLabel.setStyle("-fx-font-weight: bold;");
        usdBalanceLabel.setFont(new Font("Arial", 14));
        usdBalanceLabel.setStyle("-fx-font-weight: bold;");
        coinBalanceLabel.setFont(new Font("Arial", 14));
        coinBalanceLabel.setStyle("-fx-font-weight: bold;");
        
        // Create a HBox for the main balance and profit labels
        HBox balanceBox = new HBox(20, balanceLabel, currentBalanceLabel, profitLabel);
        balanceBox.setAlignment(Pos.CENTER);
        balanceBox.setPadding(new Insets(5, 0, 5, 0));
        
        // Create a HBox for the detailed USD and coin balance labels
        HBox detailedBalanceBox = new HBox(20, usdBalanceLabel, coinBalanceLabel);
        detailedBalanceBox.setAlignment(Pos.CENTER);
        detailedBalanceBox.setPadding(new Insets(0, 0, 5, 0));
        
        // Set up trade history components
        tradeHistoryContainer = new VBox(5);
        tradeHistoryContainer.setPadding(new Insets(10));
        tradeHistoryContainer.setStyle("-fx-background-color: #f8f8f8;");
        tradeHistoryContainer.setMinWidth(800); // Accommodate all columns
        tradeHistoryContainer.setPrefWidth(800); // Preferred width
        
        // Create fixed header for trade history
        HBox headerBox = new HBox(10);
        headerBox.setPadding(new Insets(5, 5, 5, 5));
        headerBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 0 0 1 0; -fx-background-color: #f0f0f0;");
        
        // Create and style column headers
        Label dateHeader = new Label("Date");
        dateHeader.setPrefWidth(150);
        dateHeader.setStyle("-fx-font-weight: bold;");
        
        Label typeHeader = new Label("Type");
        typeHeader.setPrefWidth(60);
        typeHeader.setStyle("-fx-font-weight: bold;");
        
        Label priceHeader = new Label("Price");
        priceHeader.setPrefWidth(100);
        priceHeader.setStyle("-fx-font-weight: bold;");
        
        Label amtHeader = new Label("Amount");
        amtHeader.setPrefWidth(100);
        amtHeader.setStyle("-fx-font-weight: bold;");
        
        Label seqHeader = new Label("Seq");
        seqHeader.setPrefWidth(60);
        seqHeader.setStyle("-fx-font-weight: bold;");
        
        Label balUsdHeader = new Label("Bal$");
        balUsdHeader.setPrefWidth(100);
        balUsdHeader.setStyle("-fx-font-weight: bold;");
        
        Label balCoinHeader = new Label("BalC");
        balCoinHeader.setPrefWidth(100);
        balCoinHeader.setStyle("-fx-font-weight: bold;");
        
        Label profitHeader = new Label("Profit");
        profitHeader.setPrefWidth(100);
        profitHeader.setStyle("-fx-font-weight: bold;");
        
        // Add headers to the header box
        headerBox.getChildren().addAll(
            dateHeader, typeHeader, priceHeader, amtHeader, 
            seqHeader, balUsdHeader, balCoinHeader, profitHeader
        );
        
        // Add placeholder to trade history container
        Label placeholder = new Label("No trades yet - Trades will appear here when executed");
        placeholder.setStyle("-fx-text-fill: #888888;");
        tradeHistoryContainer.getChildren().add(placeholder);
        
        // Create a container that includes both fixed header and scrollable content
        VBox tradeHistorySection = new VBox(0);
        tradeHistorySection.setStyle("-fx-border-color: #ddd; -fx-border-width: 1px;");
        tradeHistorySection.getChildren().addAll(headerBox, tradeHistoryContainer);
        
        // Update the root VBox
        VBox root = new VBox(10, inputBox, statusBox, balanceBox, detailedBalanceBox, tradeHistorySection);
        root.setPadding(new Insets(10));
        
        // Create scene
        Scene scene = new Scene(root, 950, 700);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Initialize WebSocket client
        performanceClient = new PerformanceAnalysisClient(this);
    }
    
    /**
     * Starts a quick replay performance analysis with the current settings
     */
    private void startQuickReplayAnalysis() {
        // Set mode to quick analysis
        // mode = 2; (already set as final field)
        
        // Clear previous data and reset sequence number
        sequenceNumber.set(0);
        
        // Disconnect any existing performance client to free resources
        if (performanceClient != null && performanceClient.isConnected()) {
            performanceClient.disconnect();
        }
        
        // Update UI to show we're starting
        statusLabel.setText("Connecting to server...");
        
        // Reset balance displays
        startingCurrencyBalance = null;
        startingCoinBalance = null;
        currentCurrencyBalance = BigDecimal.ZERO;
        currentCoinBalance = BigDecimal.ZERO;
        balanceLabel.setText("Starting Balance: $0.00 | 0.00000000 BTC");
        currentBalanceLabel.setText("Current Balance: $0.00 | 0.00000000 BTC");
        profitLabel.setText("Profit: $0.00");
        
        // Rebuild trade history table with proper headers
        tradeHistoryContainer.getChildren().clear();
        Label loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: #888888;");
        tradeHistoryContainer.getChildren().add(loadingLabel);
        
        // Force garbage collection before starting new analysis
        System.gc();
        
        // Start the performance analysis with the settings from the input fields
        String ups = upsField.getText();
        String downs = downsField.getText();
        performanceClient = new PerformanceAnalysisClient(this);
        performanceClient.startPerformanceAnalysis(ups, downs);
    }
    
    /**
     * Updates the trade history from a list of performance data points.
     * Implementation of PerformanceWindowInterface method.
     */
    @Override
    public void updateTradeHistory(List<PerformanceData> data) {
        try {
            isProcessing.set(true);
            
            // Ensure window is properly sized
            // tradeHistoryScrollPane.setPrefHeight(320);
            
            // Process the most recent 1000 entries - more than this can cause memory issues
            List<PerformanceData> limitedData = data;
            if (data.size() > 1000) {
                limitedData = data.subList(data.size() - 1000, data.size());
            }
            
            // Collect all pretend trades in chronological order
            List<PerformanceData> tradesTemp = new ArrayList<>();
            for (PerformanceData point : limitedData) {
                if (point.getPretendTrade() != null) {
                    tradesTemp.add(point);
                }
            }
            
            // Skip update if there are no trades
            if (tradesTemp.isEmpty()) {
                isProcessing.set(false);
                return;
            }
            
            // Sort by timestamp to ensure chronological order
            tradesTemp.sort(Comparator.comparingLong(PerformanceData::getTimestamp));
            
            final List<PerformanceData> tradesInOrder = new ArrayList<>(tradesTemp);
            
            // Setup starting balances for calculation
            final BigDecimal initialCurrency = startingCurrencyBalance != null ? 
                startingCurrencyBalance : BigDecimal.valueOf(10000);
            final BigDecimal initialCoin = startingCoinBalance != null ? 
                startingCoinBalance : BigDecimal.ZERO;
                
            // Remember if we need to update the starting balances
            final boolean updateStartingBalances = startingCurrencyBalance == null;
            
            Platform.runLater(() -> {
                // Clear previous trade history
                tradeHistoryContainer.getChildren().clear();
                
                // Process each trade point with running balance calculations
                BigDecimal runningCurrency = initialCurrency;
                BigDecimal runningCoin = initialCoin;
                
                for (PerformanceData dp : tradesInOrder) {
                    final PerformanceData currentPoint = dp;
                    
                    // Update running balances based on trade type
                    if (currentPoint.getPretendTrade() != null) {
                        if (currentPoint.getPretendTrade().getMakerSide().toLowerCase().contains("buy")) { // Buy
                            BigDecimal tradeValue = currentPoint.getPretendTrade().getPrice().multiply(currentPoint.getPretendTrade().getAmount());
                            runningCurrency = runningCurrency.subtract(tradeValue);
                            runningCoin = runningCoin.add(currentPoint.getPretendTrade().getAmount());
                        } else { // Sell
                            BigDecimal tradeValue = currentPoint.getPretendTrade().getPrice().multiply(currentPoint.getPretendTrade().getAmount());
                            runningCurrency = runningCurrency.add(tradeValue);
                            runningCoin = runningCoin.subtract(currentPoint.getPretendTrade().getAmount());
                        }
                    }
                    
                    // Calculate profit based on current running balances
                    // Profit = current cash + (coin amount × current price) - initial investment
                    final BigDecimal totalValue = runningCurrency.add(runningCoin.multiply(currentPoint.getPretendTrade().getPrice()));
                    final BigDecimal profit = totalValue.subtract(initialCurrency);
                    
                    final BigDecimal currentRunningCurrency = runningCurrency;
                    final BigDecimal currentRunningCoin = runningCoin;
                    
                    HBox row = new HBox(10);
                    row.setPadding(new Insets(5, 5, 5, 5));
                    row.setStyle("-fx-border-color: #eeeeee; -fx-border-width: 0 0 1 0;");
                    
                    // Create and style data columns to match header widths
                    Label dateLabel = new Label(tradeHistoryDateFormat.format(new Date(currentPoint.getTimestamp())));
                    dateLabel.setPrefWidth(150);
                    
                    boolean isBuy = currentPoint.getPretendTrade() != null &&
                                    currentPoint.getPretendTrade().getMakerSide().toLowerCase().contains("buy");
                    Label typeLabel = new Label(isBuy ? "BUY" : "SELL");
                    typeLabel.setPrefWidth(60);
                    typeLabel.setTextFill(isBuy ? Color.GREEN : Color.RED);
                    
                    Label priceLabel = new Label(currentPoint.getPretendTrade().getPrice().toPlainString());
                    priceLabel.setPrefWidth(100);
                    
                    Label amtLabel = new Label(currentPoint.getPretendTrade().getAmount().toPlainString());
                    amtLabel.setPrefWidth(100);
                    
                    Label seqLabel = new Label(Integer.toString(currentPoint.getSequence()));
                    seqLabel.setPrefWidth(60);
                    
                    Label balUsdLabel = new Label(currentRunningCurrency.toPlainString());
                    balUsdLabel.setPrefWidth(100);
                    
                    Label balCoinLabel = new Label(currentRunningCoin.toPlainString());
                    balCoinLabel.setPrefWidth(100);
                    
                    Label profitValueLabel = new Label(profit.toPlainString());
                    profitValueLabel.setPrefWidth(100);
                    
                    // Add components to the row
                    row.getChildren().addAll(
                        dateLabel, typeLabel, priceLabel, amtLabel,
                        seqLabel, balUsdLabel, balCoinLabel, profitValueLabel
                    );
                    
                    tradeHistoryContainer.getChildren().add(row);
                }
                
                // Update the balance displays with the latest values
                if (updateStartingBalances) {
                    startingCurrencyBalance = BigDecimal.valueOf(10000);
                    startingCoinBalance = BigDecimal.ZERO;
                    balanceLabel.setText("Starting Balance: $" + startingCurrencyBalance.toPlainString() + 
                                          " | " + startingCoinBalance.toPlainString() + " BTC");
                }
                
                // Calculate final values
                BigDecimal finalRunningCurrency = initialCurrency;
                BigDecimal finalRunningCoin = initialCoin;
                BigDecimal finalProfit = BigDecimal.ZERO;
                
                // Recalculate to ensure consistency
                for (PerformanceData point : tradesInOrder) {
                    if (point.getPretendTrade() != null) {
                        if (point.getPretendTrade().getMakerSide().toLowerCase().contains("buy")) { // Buy
                            BigDecimal tradeValue = point.getPretendTrade().getPrice().multiply(point.getPretendTrade().getAmount());
                            finalRunningCurrency = finalRunningCurrency.subtract(tradeValue);
                            finalRunningCoin = finalRunningCoin.add(point.getPretendTrade().getAmount());
                        } else { // Sell
                            BigDecimal tradeValue = point.getPretendTrade().getPrice().multiply(point.getPretendTrade().getAmount());
                            finalRunningCurrency = finalRunningCurrency.add(tradeValue);
                            finalRunningCoin = finalRunningCoin.subtract(point.getPretendTrade().getAmount());
                        }
                    }
                }
                
                // Update final balance display
                currentCurrencyBalance = finalRunningCurrency;
                currentCoinBalance = finalRunningCoin;
                
                // Use the last trade point to calculate final profit
                if (!tradesInOrder.isEmpty()) {
                    final PerformanceData lastPoint = tradesInOrder.get(tradesInOrder.size() - 1);
                    // Calculate final profit = current value - initial investment
                    BigDecimal finalTotalValue = finalRunningCurrency.add(finalRunningCoin.multiply(lastPoint.getPretendTrade().getPrice()));
                    finalProfit = finalTotalValue.subtract(initialCurrency);
                    currentProfit = finalProfit;
                    
                    currentBalanceLabel.setText("Current Balance: $" + finalRunningCurrency.toPlainString() + 
                                                 " | " + finalRunningCoin.toPlainString() + " BTC");
                    profitLabel.setText("Profit: $" + finalProfit.toPlainString());
                    
                    usdBalanceLabel.setText("USD Balance: $" + finalRunningCurrency.toPlainString());
                    coinBalanceLabel.setText("Coin Balance: " + finalRunningCoin.toPlainString() + " BTC");
                }
                
                // If we've completed a quick analysis, update the status
                if (mode == 2 && !tradesInOrder.isEmpty()) {
                    statusLabel.setText("Quick Replay Complete - Final Profit: $" + currentProfit.toPlainString());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error updating trade history: " + e.getMessage());
        } finally {
            isProcessing.set(false);
        }
    }
    
    /**
     * Callback for progress updates from the performance client
     */
    public void updateStatus(String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    @Override
    public void updateStatus(String status, boolean isError) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    /**
     * Gets the mode of operation (2 for quick replay).
     * Implementation of PerformanceWindowInterface method.
     */
    @Override
    public int getMode() {
        return 2; // QuickReplayWindow is always in quick replay mode
    }
    
    /**
     * Gets the next sequence number for data points.
     * Implementation of PerformanceWindowInterface method.
     */
    @Override
    public int nextSequenceNumber() {
        return sequenceNumber.incrementAndGet();
    }
    
    /**
     * Resets the sequence counter.
     * Implementation of PerformanceWindowInterface method.
     */
    @Override
    public void resetSequence() {
        sequenceNumber.set(0);
    }
    
    /**
     * Update balance display with currency, coin and profit values.
     * Implementation of PerformanceWindowInterface method.
     */
    @Override
    public void updateBalanceDisplay(BigDecimal currency, BigDecimal coin, BigDecimal profit) {
        Platform.runLater(() -> {
            currentCurrencyBalance = currency;
            currentCoinBalance = coin;
            currentProfit = profit;
            
            currentBalanceLabel.setText("Current Balance: $" + currency.toPlainString() + 
                                        " | " + coin.toPlainString() + " BTC");
            profitLabel.setText("Profit: $" + profit.toPlainString());
            
            usdBalanceLabel.setText("USD Balance: $" + currency.toPlainString());
            coinBalanceLabel.setText("Coin Balance: " + coin.toPlainString() + " BTC");
        });
    }
    
    /**
     * Called when new data is available.
     * Implementation of PerformanceWindowInterface method.
     */
    @Override
    public void onNewDataAvailable() {
        // Quick replay mode doesn't need to do anything special here
    }
} 