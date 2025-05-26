package com.ibbe.fx;

import com.ibbe.entity.TradeSnapshot;
import com.ibbe.entity.ChunkInfo;
import com.ibbe.websocket.QuickReplayClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.control.ScrollPane;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedList;
import java.time.Instant;

/**
 * Java FX window client for quick performance analysis;
 * - connects to the server to analyze performance of a given configuration in quick mode
 * - focuses on trade history display and final performance metrics
 */
public class QuickReplayWindow extends TradeConfigWindow implements QuickReplayWindowInterface {

    // Always mode 2 (quick replay) for this window
    private final int mode = 2;
    
    // Maximum number of trade history entries to keep
    private static final int MAX_TRADE_HISTORY = 200;
    private static final int MAX_CHUNKS = 100;
    
    private Button startButton = new Button("Start Quick Replay");
    private Button stopButton = new Button("Stop Replay");
    private Label statusLabel = new Label("Status: Ready");
    
    // WebSocket client for server-based analysis
    private QuickReplayClient quiclReplayClient;
    
    // Sequence number tracking
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    
    // Trade history components
    private VBox tradeHistoryContainer = new VBox(5);
    private ScrollPane tradeHistoryScrollPane = new ScrollPane();
    private final SimpleDateFormat tradeHistoryDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Set to track processed chunk IDs to prevent duplicates
    private final Set<Integer> processedChunkIds = Collections.synchronizedSet(new HashSet<>());
    
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
    
    // Trade and chunk count statistics
    private Label totalTradesLabel = new Label("Total Trades: 0");
    private Label pretendTradesLabel = new Label("Pretend Trades: 0");
    private Label chunksCountLabel = new Label("Chunks: 0");
    
    // Flag to prevent recursive updates
    private AtomicBoolean isProcessing = new AtomicBoolean(false);
    
    // Add a version label to track changes
    private Label versionLabel = new Label("Quick Replay v1.3");
    
    // Add a container for displaying trading chunks
    private VBox chunksContainer = new VBox(5);
    private ScrollPane chunksScrollPane = new ScrollPane();
    private Label chunksLabel = new Label("Trading Chunks");
    private Label totalChunkProfitLabel = new Label("Total Chunk Profit: $0.00");
    
    // For display only - recent trade history (newest first)
    private final LinkedList<TradeSnapshot> pretendTrades = new LinkedList<>();
    
    // For display only - recent chunks (newest first)
    private final LinkedList<ChunkInfo> tradingChunks = new LinkedList<>();
    
    // Format for currency display
    private final DecimalFormat currencyFormat = new DecimalFormat("$#,##0.00");
    private final DecimalFormat coinFormat = new DecimalFormat("#,##0.00000000");
    
    /**
     * Converts an ISO-8601 timestamp string to milliseconds since epoch
     * @param timestampStr String in format "2025-05-22T13:20:49.930Z"
     * @return milliseconds since epoch
     */
    private long convertTimestampToMillis(String timestampStr) {
        try {
            return Instant.parse(timestampStr).toEpochMilli();
        } catch (Exception e) {
            System.err.println("Error parsing timestamp: " + timestampStr);
            return System.currentTimeMillis(); // fallback to current time
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        super.start(primaryStage);
        
        primaryStage.setTitle("Quick Performance Analysis");

        // Add a handler to clean up when the window is closed
        primaryStage.setOnCloseRequest(event -> {
            // Clean up configuration and disconnect clients
            cleanupConnections();
        });

        // Style the version label
        versionLabel.setFont(new Font("Arial", 24));
        versionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555555;");
        versionLabel.setPadding(new Insets(5, 10, 5, 10));

        // Style the chunks label
        chunksLabel.setFont(new Font("Arial", 32));
        chunksLabel.setStyle("-fx-font-weight: bold;");
        chunksLabel.setPadding(new Insets(5, 0, 5, 0));
        
        // Set up start button
        startButton.setPrefSize(200, 30);
        startButton.setFont(new Font("Arial", 28));
        startButton.setOnAction(event -> startQuickReplayAnalysis());
        
        // Set up stop button
        stopButton.setPrefSize(150, 30);
        stopButton.setFont(new Font("Arial", 28));
        stopButton.setDisable(true); // Initially disabled
        stopButton.setOnAction(event -> stopQuickReplayAnalysis());
        
        // Set up status label
        statusLabel.setFont(new Font("Arial", 28));
        
        // Style the trade and chunk count statistics
        totalTradesLabel.setFont(new Font("Arial", 32));
        totalTradesLabel.setStyle("-fx-font-weight: bold;");
        pretendTradesLabel.setFont(new Font("Arial", 32));
        pretendTradesLabel.setStyle("-fx-font-weight: bold;");
        chunksCountLabel.setFont(new Font("Arial", 32));
        chunksCountLabel.setStyle("-fx-font-weight: bold;");
        
        // Create trade criteria checkboxes container
        VBox tradeCriteriaBox = new VBox(5);
        tradeCriteriaBox.setPadding(new Insets(5, 10, 5, 10));
        tradeCriteriaBox.setStyle("-fx-border-color: #ddd; -fx-border-width: 1px; -fx-background-color: #f8f8f8;");
        
        // Create a label for the criteria section
        Label criteriaLabel = new Label("Trade Criteria");
        criteriaLabel.setFont(new Font("Arial", 28));
        criteriaLabel.setStyle("-fx-font-weight: bold;");
        
        // Add checkboxes to the container but remove DirectKafka checkbox
        tradeCriteriaBox.getChildren().addAll(
            criteriaLabel,
            avgBidVsAvgAskCheckBox,
            shortVsLongMovAvgCheckBox,
            sumAmtUpVsDownCheckBox,
            tradePriceCloserToAskVsBuyCheckBox
        );
        
        // All checkboxes unselected by default
        
        // Create input layout
        HBox inputBox = new HBox(10);
        VBox tradeConfigBox = new VBox(5);
        
        // Create labels for the ups and downs inputs
        Label upsLabel = new Label("Ups:");
        Label downsLabel = new Label("Downs:");
        upsLabel.setFont(new Font("Arial", 28));
        downsLabel.setFont(new Font("Arial", 28));
        
        // Create HBox for the ups/downs inputs
        HBox upsDownsBox = new HBox(10, upsLabel, upsField, downsLabel, downsField);
        upsDownsBox.setAlignment(Pos.CENTER_LEFT);
        
        // Create a button box for the start and stop buttons
        HBox buttonBox = new HBox(10, startButton, stopButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        // Add the components to the trade config box
        tradeConfigBox.getChildren().addAll(upsDownsBox, buttonBox);
        tradeConfigBox.setAlignment(Pos.CENTER_LEFT);
        
        // Add trade config and criteria to the input box
        inputBox.getChildren().addAll(tradeConfigBox, tradeCriteriaBox);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(10));
        
        // Create a container for the version label and status label
        HBox statusBox = new HBox(10);
        statusBox.getChildren().addAll(versionLabel, statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPadding(new Insets(5, 10, 5, 10));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        // Style the balance and profit labels - Increase font sizes to match the profit label
        balanceLabel.setFont(new Font("Arial", 32));
        balanceLabel.setStyle("-fx-font-weight: bold;");
        currentBalanceLabel.setFont(new Font("Arial", 32));
        currentBalanceLabel.setStyle("-fx-font-weight: bold;");
        profitLabel.setFont(new Font("Arial", 40));
        profitLabel.setStyle("-fx-font-weight: bold;");
        usdBalanceLabel.setFont(new Font("Arial", 32));
        usdBalanceLabel.setStyle("-fx-font-weight: bold;");
        coinBalanceLabel.setFont(new Font("Arial", 32));
        coinBalanceLabel.setStyle("-fx-font-weight: bold;");
        
        // Create a HBox for the main balance and profit labels
        HBox balanceBox = new HBox(20, balanceLabel, currentBalanceLabel, profitLabel);
        balanceBox.setAlignment(Pos.CENTER);
        balanceBox.setPadding(new Insets(5, 0, 5, 0));
        
        // Create a HBox for the detailed USD and coin balance labels
        HBox detailedBalanceBox = new HBox(20, usdBalanceLabel, coinBalanceLabel);
        detailedBalanceBox.setAlignment(Pos.CENTER);
        detailedBalanceBox.setPadding(new Insets(0, 0, 5, 0));
        
        // Create a HBox for statistics labels
        HBox statsBox = new HBox(20, totalTradesLabel, pretendTradesLabel, chunksCountLabel);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.setPadding(new Insets(5, 0, 5, 0));
        
        // Set up trade history components
        tradeHistoryContainer = new VBox(5);
        tradeHistoryContainer.setPadding(new Insets(10));
        tradeHistoryContainer.setStyle("-fx-background-color: #f8f8f8;");
        tradeHistoryContainer.setMinWidth(1600); // Doubled from 800
        tradeHistoryContainer.setPrefWidth(1600); // Doubled from 800
        
        // Create scrollable trade history container
        tradeHistoryScrollPane = new ScrollPane(tradeHistoryContainer);
        tradeHistoryScrollPane.setFitToWidth(true);
        tradeHistoryScrollPane.setPrefHeight(250);
        tradeHistoryScrollPane.setStyle("-fx-background-color: transparent;");
        
        // Create fixed header for trade history
        HBox headerBox = new HBox(10);
        headerBox.setPadding(new Insets(5, 5, 5, 5));
        headerBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 0 0 1 0; -fx-background-color: #f0f0f0;");
        
        // Create and style column headers
        Label dateHeader = new Label("Date");
        dateHeader.setPrefWidth(225);
        dateHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
        
        Label typeHeader = new Label("Type");
        typeHeader.setPrefWidth(90);
        typeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
        
        Label priceHeader = new Label("Price");
        priceHeader.setPrefWidth(150);
        priceHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
        
        Label amtHeader = new Label("Amount");
        amtHeader.setPrefWidth(150);
        amtHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
        
        Label seqHeader = new Label("Seq");
        seqHeader.setPrefWidth(90);
        seqHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
        
        Label balUsdHeader = new Label("Bal$");
        balUsdHeader.setPrefWidth(150);
        balUsdHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
        
        Label balCoinHeader = new Label("BalC");
        balCoinHeader.setPrefWidth(150);
        balCoinHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
        
        Label profitHeader = new Label("Profit");
        profitHeader.setPrefWidth(150);
        profitHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
        
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
        tradeHistorySection.getChildren().addAll(headerBox, tradeHistoryScrollPane);
        
        // Chunks display section
        Label chunksSectionLabel = new Label("Trading Chunks");
        chunksSectionLabel.setFont(Font.font(null, FontWeight.BOLD, 28));
        
        chunksLabel.setFont(Font.font(null, FontWeight.NORMAL, 26));
        
        // Container for chunks section
        VBox chunksSectionBox = new VBox(5);
        chunksSectionBox.setPadding(new Insets(10));
        chunksSectionBox.setStyle("-fx-border-color: #ddd; -fx-border-width: 1px; -fx-border-radius: 5px;");
        
        // Header and statistics for chunks
        HBox chunksHeaderBox = new HBox(10);
        chunksHeaderBox.setAlignment(Pos.CENTER_LEFT);
        chunksHeaderBox.getChildren().addAll(chunksLabel, totalChunkProfitLabel, chunksCountLabel);
        
        // Initialize chunks container
        chunksContainer = new VBox(5);
        chunksContainer.setPadding(new Insets(5));
        chunksContainer.getChildren().add(new Label("No trading chunks yet"));
        
        // Set up scroll pane for chunks display
        chunksScrollPane = new ScrollPane();
        chunksScrollPane.setFitToWidth(true);
        chunksScrollPane.setPrefHeight(250); // Increase default height for better visibility
        chunksScrollPane.setStyle("-fx-background: white;");
        
        // Create an initial placeholder content for the scroll pane
        VBox placeholderContent = new VBox(5);
        placeholderContent.setPadding(new Insets(10));
        placeholderContent.getChildren().add(new Label("Chunks will appear here when available"));
        chunksScrollPane.setContent(placeholderContent);
        
        // Add components to chunks section
        chunksSectionBox.getChildren().addAll(chunksSectionLabel, chunksHeaderBox, chunksScrollPane);

        // Create main layout
        VBox mainLayout = new VBox(10);
        mainLayout.getChildren().addAll(
            inputBox,
            statusBox,
            balanceBox,
            detailedBalanceBox,
            statsBox,
            chunksSectionBox,
            tradeHistorySection
        );

        // Create and set the scene
        Scene scene = new Scene(mainLayout, 1700, 800); // Doubled width from 850
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initialize clients (but don't connect yet)
        quiclReplayClient = new QuickReplayClient(this);
    }

    /**
     * Starts a quick replay performance analysis with the current settings
     */
    private void startQuickReplayAnalysis() {
        // Set mode to quick analysis
        // mode = 2; (already set as final field)

        // Clear previous data and reset sequence number
        sequenceNumber.set(0);
        pretendTrades.clear();
        processedChunkIds.clear();

        // Clean up any existing connections
        cleanupConnections();

        // Update UI to show we're starting
        statusLabel.setText("Starting analysis...");
        startButton.setDisable(true);
        stopButton.setDisable(false);

        // Initialize starting balances
        startingCurrencyBalance = BigDecimal.valueOf(10000);
        startingCoinBalance = BigDecimal.ZERO;

        // Initialize the current balances with the starting values
        currentCurrencyBalance = startingCurrencyBalance;
        currentCoinBalance = startingCoinBalance;
        
        // Update the balance displays
        balanceLabel.setText("Starting Balance: " + currencyFormat.format(startingCurrencyBalance) + 
                          " | " + coinFormat.format(startingCoinBalance) + " BTC");
        currentBalanceLabel.setText("Current Balance: " + currencyFormat.format(currentCurrencyBalance) + 
                                 " | " + coinFormat.format(currentCoinBalance) + " BTC");
        profitLabel.setText("Profit: $0.00");
        
        // Reset statistics
        totalTradesLabel.setText("Total Trades: 0");
        pretendTradesLabel.setText("Pretend Trades: 0");
        chunksCountLabel.setText("Chunks: 0");
        
        // Reset chunk profit display
        totalChunkProfitLabel.setText("Total Chunk Profit: $0.00");

        // Rebuild trade history table with proper headers
        tradeHistoryContainer.getChildren().clear();
        Label loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: #888888;");
        tradeHistoryContainer.getChildren().add(loadingLabel);
        
        // Make sure the trade history container is set as the scroll pane content
        tradeHistoryScrollPane.setContent(tradeHistoryContainer);
        
        // Reset chunks container
        chunksContainer.getChildren().clear();
        VBox placeholderContent = new VBox(5);
        placeholderContent.setPadding(new Insets(10));
        placeholderContent.getChildren().add(new Label("Chunks will appear here when available"));
        chunksScrollPane.setContent(placeholderContent);

        // Force garbage collection before starting new analysis
        System.gc();

        // Generate a new configuration ID
        String configId = generateConfigId();
        setCurrentConfigId(configId);

        // Get settings from the input fields and checkboxes
        String ups = upsField.getText();
        String downs = downsField.getText();
        
        // Build additional parameters for trade criteria
        StringBuilder criteria = new StringBuilder();
        
        if (avgBidVsAvgAskCheckBox.isSelected()) {
            criteria.append("avgBidVsAvgAsk,");
        }
        
        if (shortVsLongMovAvgCheckBox.isSelected()) {
            criteria.append("shortVsLongMovAvg,");
        }
        
        if (sumAmtUpVsDownCheckBox.isSelected()) {
            criteria.append("sumAmtUpVsDown,");
        }
        
        if (tradePriceCloserToAskVsBuyCheckBox.isSelected()) {
            criteria.append("tradePriceCloserToAskVsBuy,");
        }
        
        // Remove trailing comma if present
        String criteriaStr = criteria.toString();
        if (criteriaStr.endsWith(",")) {
            criteriaStr = criteriaStr.substring(0, criteriaStr.length() - 1);
        }
        
        // Use whatever criteria the user has selected, even if none
        // If criteriaStr is empty, that's fine - server will use default behavior

        // Start using WebSocket connection to server
        quiclReplayClient = new QuickReplayClient(this);
        quiclReplayClient.startPerformanceAnalysis(ups, downs, criteriaStr, configId);
    }

    /**
     * Stops the current quick replay analysis and cleans up resources
     */
    private void stopQuickReplayAnalysis() {
        // Clean up connections and configuration
        cleanupConnections();
        
        // Update UI state
        Platform.runLater(() -> {
            statusLabel.setText("Replay stopped");
            startButton.setDisable(false);
            stopButton.setDisable(true);
        });
    }
    
    /**
     * Clean up all connections and resources
     */
    private void cleanupConnections() {
        // Clean up the current configuration on the server if needed
        if (currentConfigId != null && !currentConfigId.isEmpty() && quiclReplayClient != null) {
            endCurrentConfiguration(new StatusHandler() {
                @Override
                public void handleStatus(String message, boolean isError) {
                    Platform.runLater(() -> {
                        System.out.println("Config cleanup: " + message);
                    });
                }
            });
        }
        
        // Disconnect the WebSocket client if active
        if (quiclReplayClient != null && quiclReplayClient.isConnected()) {
            quiclReplayClient.disconnect();
        }
    }

    /**
     * Updates the trade history from a list of performance data points.
     * Implementation of PerformanceWindowInterface method.
     * Now maintains a fixed-size rolling list for display while using all data for calculations.
     */
    @Override
    public void updateTradeHistory(List<TradeSnapshot> data) {
        try {
            if (isProcessing.getAndSet(true)) {
                // Already processing an update, skip this one to avoid UI overload
                isProcessing.set(false);
                return;
            }

            // Process new chunks first
            processNewChunks(data);

            // Create a defensive copy of the data to prevent ConcurrentModificationException
            List<TradeSnapshot> dataSnapshot = new ArrayList<>();
            
            // Only collect entries with pretend trades - ignore everything else
            for (TradeSnapshot point : data) {
                if (point != null && point.getPretendTrade() != null) {
                    dataSnapshot.add(point);
                }
            }

            // Skip update if there are no pretend trades
            if (dataSnapshot.isEmpty()) {
                isProcessing.set(false);
                return;
            }

            // Sort by timestamp to ensure chronological order
//            dataSnapshot.sort(Comparator.comparingLong(TradeSnapshot::getTimestamp));
            
            // Update the recentTradeHistory list (already limited to MAX_TRADE_HISTORY)
            synchronized (pretendTrades) {
                // Add new trades to the recent trade history, maintaining the maximum size
                for (TradeSnapshot dp : dataSnapshot) {
                    if (dp.getPretendTrade() != null) {
                        if (pretendTrades.size() >= MAX_TRADE_HISTORY) {
                            pretendTrades.removeLast();
                        }
                        pretendTrades.addFirst(dp); // Add to beginning (newest)
                    }
                }
            }
            
            // Only use most recent MAX_TRADE_HISTORY entries for UI display
            final List<TradeSnapshot> displayData;
            synchronized (pretendTrades) {
                displayData = new ArrayList<>(pretendTrades);
            }

            // Initial balances if not set yet
            final BigDecimal initialCurrency = startingCurrencyBalance != null ?
                startingCurrencyBalance : BigDecimal.valueOf(10000);
            final BigDecimal initialCoin = startingCoinBalance != null ?
                startingCoinBalance : BigDecimal.ZERO;

            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                try {
                    // Calculate final balances using the latest data
                    BigDecimal runningCurrency = initialCurrency;
                    BigDecimal runningCoin = initialCoin;
                    BigDecimal lastPrice = BigDecimal.ZERO;

                    // Calculate final balances (only use displayed trades for UI consistency)
                    for (TradeSnapshot dp : displayData) {
                        if (dp.getPretendTrade() != null) {
                            lastPrice = dp.getPretendTrade().getPrice();
                            boolean isBuy = dp.getPretendTrade().getMakerSide().toLowerCase().contains("buy");
                            BigDecimal tradeValue = dp.getPretendTrade().getPrice().multiply(dp.getPretendTrade().getAmount());
                            
                            if (isBuy) { // Buy
                                runningCurrency = runningCurrency.subtract(tradeValue);
                                runningCoin = runningCoin.add(dp.getPretendTrade().getAmount());
                            } else { // Sell
                                runningCurrency = runningCurrency.add(tradeValue);
                                runningCoin = runningCoin.subtract(dp.getPretendTrade().getAmount());
                            }
                        }
                    }

                    // Update current balances
                    currentCurrencyBalance = runningCurrency;
                    currentCoinBalance = runningCoin;

//                    // Calculate profit
//                    BigDecimal finalTotalValue = runningCurrency;
//                    if (lastPrice.compareTo(BigDecimal.ZERO) > 0) {
//                        finalTotalValue = finalTotalValue.add(runningCoin.multiply(lastPrice));
//                    }
//                    BigDecimal finalProfit = finalTotalValue.subtract(initialCurrency);
//                    currentProfit = finalProfit;
                    
                    // Clear and rebuild the trade history display
                    tradeHistoryContainer.getChildren().clear();

                    // Add trades to the display (most recent trades)
                    for (TradeSnapshot currentPoint : displayData) {
                        HBox row = new HBox(10);
                        row.setPadding(new Insets(5, 5, 5, 5));
                        row.setStyle("-fx-border-color: #eeeeee; -fx-border-width: 0 0 1 0;");

                        // Date column
                        Label dateLabel = new Label(tradeHistoryDateFormat.format(new Date(currentPoint.getTimestamp())));
                        dateLabel.setPrefWidth(225);

                        // Trade type column (buy/sell)
                        boolean isBuy = currentPoint.getPretendTrade().getMakerSide().toLowerCase().contains("buy");
                        Label typeLabel = new Label(isBuy ? "BUY" : "SELL");
                        typeLabel.setPrefWidth(90);
                        typeLabel.setTextFill(isBuy ? Color.GREEN : Color.RED);

                        // Price and amount with formatting
                        Label priceLabel = new Label(currencyFormat.format(currentPoint.getPretendTrade().getPrice()));
                        priceLabel.setPrefWidth(150);

                        Label amtLabel = new Label(coinFormat.format(currentPoint.getPretendTrade().getAmount()));
                        amtLabel.setPrefWidth(150);

                        Label seqLabel = new Label(Integer.toString(currentPoint.getSequence()));
                        seqLabel.setPrefWidth(90);

                        // For UI simplicity, show placeholder values for per-trade balances
                        Label balUsdLabel = new Label("-");
                        balUsdLabel.setPrefWidth(150);

                        Label balCoinLabel = new Label("-");
                        balCoinLabel.setPrefWidth(150);

                        Label profitValueLabel = new Label("-");
                        profitValueLabel.setPrefWidth(150);

                        // Add all columns to the row
                        row.getChildren().addAll(
                            dateLabel, typeLabel, priceLabel, amtLabel,
                            seqLabel, balUsdLabel, balCoinLabel, profitValueLabel
                        );

                        // Add the row to the container
                        tradeHistoryContainer.getChildren().add(row);
                    }

                    // Update balance displays
                    if (startingCurrencyBalance == null) {
                        startingCurrencyBalance = BigDecimal.valueOf(10000);
                    }
                    
                    if (startingCoinBalance == null) {
                        startingCoinBalance = BigDecimal.ZERO;
                    }
                    
                    // Now safe to display starting balance
                    balanceLabel.setText("Starting Balance: " + currencyFormat.format(startingCurrencyBalance) +
                                      " | " + coinFormat.format(startingCoinBalance) + " BTC");

                    // Update final balance display
                    currentBalanceLabel.setText("Current Balance: " + currencyFormat.format(runningCurrency) +
                                                " | " + coinFormat.format(runningCoin) + " BTC");
                    profitLabel.setText("Total Profit Over Chunks: " + currencyFormat.format(calculateTotalProfit()));

                    usdBalanceLabel.setText("USD Balance: " + currencyFormat.format(runningCurrency));
                    coinBalanceLabel.setText("Coin Balance: " + coinFormat.format(runningCoin) + " BTC");

                    // Important: Set the trade history container as the content of the scroll pane
                    tradeHistoryScrollPane.setContent(tradeHistoryContainer);

                    // Update status with summary info
                    if (mode == 2 && !displayData.isEmpty()) {
                        // Build criteria string
                        StringBuilder criteriaStr = new StringBuilder();
                        if (avgBidVsAvgAskCheckBox.isSelected()) criteriaStr.append("AvgBid/Ask ");
                        if (shortVsLongMovAvgCheckBox.isSelected()) criteriaStr.append("MovAvg ");
                        if (sumAmtUpVsDownCheckBox.isSelected()) criteriaStr.append("SumAmt ");
                        if (tradePriceCloserToAskVsBuyCheckBox.isSelected()) criteriaStr.append("PriceCloser ");
                        
                        // Update trade statistics
                        int tradeCount = displayData.size();
                        pretendTradesLabel.setText("Pretend Trades: " + tradeCount);
                        totalTradesLabel.setText("Total Trades: " + quiclReplayClient.getTotalRecordsReceived());
                    }
                } catch (Exception e) {
                    System.err.println("Error updating UI in trade history: " + e.getMessage());
                    e.printStackTrace();
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
     * Processes data looking specifically for new chunk information.
     * 
     * @param data The list of performance data to check
     */
    private void processNewChunks(List<TradeSnapshot> data) {
        // Check if any of the data points contain new chunk information
        List<ChunkInfo> newChunks = new ArrayList<>();
        
        for (TradeSnapshot point : data) {
            if (point != null && point.getCompletedChunk() != null) {
                ChunkInfo newChunk = point.getCompletedChunk();
                
                // Skip if we've already processed this chunk ID
                if (processedChunkIds.contains(newChunk.getChunkNumber())) {
                    continue;
                }
                
                // Mark as processed
                processedChunkIds.add(newChunk.getChunkNumber());
                
                newChunks.add(newChunk);
                System.out.println("Received new completed chunk #" + newChunk.getChunkNumber() + 
                                  " with profit: " + currencyFormat.format(newChunk.getProfit()));
            }
        }
        
        // If we found new chunks, update the display immediately
        if (!newChunks.isEmpty() && quiclReplayClient != null) {
            Platform.runLater(() -> {
                try {
                    // Update chunk statistics
                    int chunksCount = quiclReplayClient.getAccumulatedChunks().size();
                    chunksCountLabel.setText("Chunks: " + chunksCount);
                    
                    // Update total chunk profit
                    BigDecimal totalChunkProfit = quiclReplayClient.getTotalChunkProfit();
                    totalChunkProfitLabel.setText("Total Chunk Profit: " + currencyFormat.format(totalChunkProfit));
                    
                    // Update trade statistics
                    if (quiclReplayClient != null) {
                        updateTradeStatistics(
                            quiclReplayClient.getTotalRecordsReceived(),
                            quiclReplayClient.getPretendTradeCount()
                        );
                    }
                } catch (Exception e) {
                    System.err.println("Error processing new chunks: " + e.getMessage());
                    e.printStackTrace();
                }
            });
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
    public void updateBalanceDisplay(BigDecimal currencyBalance, BigDecimal coinBalance, BigDecimal profit) {
        Platform.runLater(() -> {
            try {
                // Save the current values for later use
                currentCurrencyBalance = currencyBalance;
                currentCoinBalance = coinBalance;
                currentProfit = profit;
                
                // Initialize starting balances from the first update if not already set
                if (startingCurrencyBalance == null) {
                    startingCurrencyBalance = currencyBalance;
                }
                
                if (startingCoinBalance == null) {
                    startingCoinBalance = coinBalance != null ? coinBalance : BigDecimal.ZERO;
                }
                
                // Now we can safely display the starting balance
                balanceLabel.setText("Starting Balance: " + currencyFormat.format(startingCurrencyBalance) +
                                   " | " + coinFormat.format(startingCoinBalance) + " BTC");
                
                // Update the current balance display
                currentBalanceLabel.setText("Current Balance: " + currencyFormat.format(currencyBalance) + 
                                         " | " + coinFormat.format(coinBalance) + " BTC");
                
                // Update profit display and color code it
                String profitText = "Profit: " + currencyFormat.format(profit);
                profitLabel.setText(profitText);
                
                // Color code profit - green for profit, red for loss
                if (profit.compareTo(BigDecimal.ZERO) >= 0) {
                    profitLabel.setTextFill(Color.GREEN);
                } else {
                    profitLabel.setTextFill(Color.RED);
                }
                
                // Update individual balance labels
                usdBalanceLabel.setText("USD Balance: " + currencyFormat.format(currencyBalance));
                coinBalanceLabel.setText("Coin Balance: " + coinFormat.format(coinBalance) + " BTC");
                
                // No need to log this information as it's displayed in the UI
            } catch (Exception e) {
                System.err.println("Error updating balance display: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Called when new data is available.
     * Implementation of PerformanceWindowInterface method.
     */
    @Override
    public void onNewChunk(ChunkInfo chunk) {
        Platform.runLater(() -> {
            try {
                // Add the new chunk to the chunks list
                tradingChunks.addFirst(chunk);
                
                // Remove oldest chunk if we exceed MAX_CHUNKS
                if (tradingChunks.size() > MAX_CHUNKS) {
                    tradingChunks.removeLast();
                }
                
                // Update the chunks display
                updateChunksDisplay(chunk);
            } catch (Exception e) {
                System.err.println("Error updating chunks display: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onNewPretendTrade(TradeSnapshot trade) {
        Platform.runLater(() -> {
            try {
                // Add the new trade to the trade history
                pretendTrades.addFirst(trade);
                
                // Remove oldest trade if we exceed MAX_TRADE_HISTORY
                if (pretendTrades.size() > MAX_TRADE_HISTORY) {
                    pretendTrades.removeLast();
                }
                
                // Update the trades display
                updatePretendTradesDisplay(trade);
            } catch (Exception e) {
                System.err.println("Error updating trades display: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Updates the display of a single pretend trade in the UI
     */
    private void updatePretendTradesDisplay(TradeSnapshot newTrade) {
        // Remove or comment out the updatePretendTradesDisplay method since we are not displaying pretend trades
        // private void updatePretendTradesDisplay(PerformanceData newTrade) {
        //     // ... existing code ...
        // }
    }

    /**
     * Updates the display of a single chunk in the UI
     */
    private void updateChunksDisplay(ChunkInfo newChunk) {
        try {
            System.out.println("Updating chunks display for chunk #" + newChunk.getChunkNumber());
            
            // Calculate total profit
            final BigDecimal totalChunkProfit = tradingChunks.stream()
                .filter(chunk -> chunk != null && chunk.getProfit() != null)
                .map(ChunkInfo::getProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                try {
                    // Create a container for the header row
                    HBox headerRow = new HBox(10);
                    headerRow.setPadding(new Insets(5));
                    headerRow.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");

                    Label numHeader = new Label("Chunk #");
                    numHeader.setPrefWidth(105);
                    numHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");

                    Label profitHeader = new Label("Profit");
                    profitHeader.setPrefWidth(150);
                    profitHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");

                    Label startPriceHeader = new Label("Start Price");
                    startPriceHeader.setPrefWidth(150);
                    startPriceHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");

                    Label endPriceHeader = new Label("End Price");
                    endPriceHeader.setPrefWidth(150);
                    endPriceHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");

                    Label tradeCountHeader = new Label("Trades");
                    tradeCountHeader.setPrefWidth(105);
                    tradeCountHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");
                    
                    Label startTimeHeader = new Label("Start Time");
                    startTimeHeader.setPrefWidth(225);
                    startTimeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");

                    Label endTimeHeader = new Label("End Time");
                    endTimeHeader.setPrefWidth(225);
                    endTimeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 24px;");

                    headerRow.getChildren().addAll(
                        numHeader, profitHeader, startPriceHeader, endPriceHeader,
                        tradeCountHeader, startTimeHeader, endTimeHeader
                    );
                    
                    // Create a container for the scrollable chunk rows
                    VBox chunksContent = new VBox(5);
                    chunksContent.setPadding(new Insets(0, 0, 0, 0));
                    
                    // Add rows for each chunk (newest first)
                    BigDecimal totalProfit = BigDecimal.ZERO;
                    System.out.println("Starting total profit: 0");

                    for (ChunkInfo chunk : tradingChunks) {
                        if (chunk == null) continue;

                        System.out.println("\nProcessing chunk #" + chunk.getChunkNumber());
                        System.out.println("Chunk profit: " + chunk.getProfit());

                        // Create row for this chunk
                        HBox chunkRow = new HBox(10);
                        chunkRow.setPadding(new Insets(5));
                        chunkRow.setStyle("-fx-background-color: #f8f8f8;");

                        // Format chunk number
                        Label chunkNumLabel = new Label(String.valueOf(chunk.getChunkNumber()));
                        chunkNumLabel.setPrefWidth(105);
                        chunkNumLabel.setStyle("-fx-font-size: 24px;");

                        // Format profit with color coding
                        Label profitLabel = new Label(currencyFormat.format(chunk.getProfit()));
                        profitLabel.setPrefWidth(150);
                        profitLabel.setStyle("-fx-font-size: 24px;");
                        
                        // Color code profit - green for profit, red for loss
                        if (chunk.getProfit().compareTo(BigDecimal.ZERO) >= 0) {
                            profitLabel.setTextFill(Color.GREEN);
                        } else {
                            profitLabel.setTextFill(Color.RED);
                        }

                        // Format prices
                        Label startPriceLabel = new Label(currencyFormat.format(chunk.getStartingTradePrice()));
                        startPriceLabel.setPrefWidth(150);
                        startPriceLabel.setStyle("-fx-font-size: 24px;");
                        
                        Label endPriceLabel = new Label(currencyFormat.format(chunk.getEndingTradePrice()));
                        endPriceLabel.setPrefWidth(150);
                        endPriceLabel.setStyle("-fx-font-size: 24px;");

                        // Format trade count
                        Label tradeCountLabel = new Label(String.valueOf(chunk.getTradeCount()));
                        tradeCountLabel.setPrefWidth(105);
                        tradeCountLabel.setStyle("-fx-font-size: 24px;");

                        // Format timestamps
                        String startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new Date(chunk.getStartTimeMillis()));
                        Label startTimeLabel = new Label(startTime);
                        startTimeLabel.setPrefWidth(225);
                        startTimeLabel.setStyle("-fx-font-size: 24px;");
                        
                        String endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new Date(chunk.getEndTimeMillis()));
                        Label endTimeLabel = new Label(endTime);
                        endTimeLabel.setPrefWidth(225);
                        endTimeLabel.setStyle("-fx-font-size: 24px;");

                        // Add all labels to the row
                        chunkRow.getChildren().addAll(
                            chunkNumLabel, profitLabel, startPriceLabel, endPriceLabel,
                            tradeCountLabel, startTimeLabel, endTimeLabel
                        );
                        
                        // Add the row to the content container
                        chunksContent.getChildren().add(chunkRow);
                        
                        // Add chunk profit to total
                        totalProfit = totalProfit.add(chunk.getProfit());
                        System.out.println("Updated total profit: " + totalProfit);
                    }
                    
                    // Update total profit label
                    totalChunkProfitLabel.setText("Total Chunk Profit: " + currencyFormat.format(totalProfit));
                    
                    // Create a scroll pane for just the content (not the header)
                    ScrollPane chunksScrollContent = new ScrollPane(chunksContent);
                    chunksScrollContent.setFitToWidth(true);
                    chunksScrollContent.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    chunksScrollContent.setPrefHeight(200);
                    
                    // Create a VBox that contains the fixed header and scrollable content
                    VBox chunksTableContainer = new VBox(0); // No spacing between header and content
                    chunksTableContainer.getChildren().addAll(headerRow, chunksScrollContent);
                    
                    // Set this VBox as the content of the main chunks scroll pane
                    chunksScrollPane.setContent(chunksTableContainer);
                    
                    // Update the chunks container
                    chunksContainer.getChildren().clear();
                    chunksContainer.getChildren().add(new Label("Chunks: " + tradingChunks.size()));
                    
                    // Update labels
                    chunksLabel.setText("Trading Chunks (" + tradingChunks.size() + ")");
                    chunksCountLabel.setText("Chunks: " + tradingChunks.size());
                } catch (Exception e) {
                    System.err.println("Error updating UI in chunks display: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            System.err.println("Error updating chunks display: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Updates the trade statistics labels with current counts
     * 
     * @param totalTrades The total number of trades processed
     * @param pretendTrades The number of pretend trades
     */
    public void updateTradeStatistics(int totalTrades, int pretendTrades) {
        Platform.runLater(() -> {
            totalTradesLabel.setText("Total Trades: " + totalTrades);
            pretendTradesLabel.setText("Pretend Trades: " + pretendTrades);
        });
    }

    /**
     * Calculate the total profit across all chunks plus the current chunk
     *
     * @return total profit across all trading chunks
     */
    public BigDecimal calculateTotalProfit() {
      BigDecimal totalChunkProfit = BigDecimal.ZERO;
      for (ChunkInfo chunkInfo : tradingChunks) {
        totalChunkProfit = totalChunkProfit.add(chunkInfo.getProfit());
      }
      return totalChunkProfit.setScale(2, RoundingMode.DOWN);
    }


} 