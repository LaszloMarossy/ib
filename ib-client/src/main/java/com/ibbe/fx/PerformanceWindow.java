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
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.scene.CacheHint;
import javafx.scene.control.SplitPane;
import javafx.scene.paint.Color;
import javafx.geometry.Orientation;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * Java FX window client for performance analysis;
 * - connects to the server to analyze performance of a given configuration
 * - displays a chart of trade prices, average ask prices, and average bid prices
 */
public class PerformanceWindow extends Application implements PerformanceWindowInterface {

    // mode of run depending on which button they push
    private int mode = 1;
    
    // Maximum number of data points to keep in memory - reduced from 1000 to 500
    private static final int MAX_DATA_POINTS = 500;
    
    // Number of data points to display in the visible window - increased from 30 to 50
    private static final int VISIBLE_DATA_POINTS = 50;
    
    // Maximum number of trade history entries to keep
    private static final int MAX_TRADE_HISTORY = 100;
    
    // Date formatter for X-axis
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd HH:mm:ss");
    private final SimpleDateFormat xAxisFormatter = new SimpleDateFormat("yy.MM.dd.HH.mm");
    
    private NumberAxis xAxis = new NumberAxis();
    private NumberAxis yAxis = new NumberAxis();
    private LineChart<Number, Number> lineChart;
    private XYChart.Series<Number, Number> tradePriceSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> avgAskPriceSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> avgBidPriceSeries = new XYChart.Series<>();
    
    // Add permanent pretend trade series
    private XYChart.Series<Number, Number> pretendBuySeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> pretendSellSeries = new XYChart.Series<>();
    
    // Amount chart components
    private CategoryAxis amountXAxis = new CategoryAxis();
    private NumberAxis amountYAxis = new NumberAxis();
    private BarChart<String, Number> amountChart;
    private XYChart.Series<String, Number> tradeAmountSeries = new XYChart.Series<>();
    private XYChart.Series<String, Number> avgAskAmountSeries = new XYChart.Series<>();
    private XYChart.Series<String, Number> avgBidAmountSeries = new XYChart.Series<>();
    
    private TextField upsField = new TextField();
    private TextField downsField = new TextField();
    private Button visualReplayButton = new Button("Visual Replay");
//    private Button quickReplayButton = new Button("Quick Replay");
    private Label statusLabel = new Label("Status: Ready");
    private ScrollPane chartScrollPane;
    private Slider timeSlider;
    private Button liveButton;
    
    private PerformanceAnalysisClient performanceClient;
    
    // Queue to store data points for updating the chart
    private final LinkedList<PerformanceData> dataPoints = new LinkedList<>();
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    
    // Flag to indicate if chart is initialized
    private boolean chartInitialized = false;
    
    // Flag to indicate if we're in live mode (auto-scrolling to latest data)
    private final AtomicBoolean liveMode = new AtomicBoolean(true);
    
    // Flag to prevent recursive slider updates
    private boolean isUpdatingSlider = false;
    
    private Timer resizeTimer;
    
    // Add primaryStage as a class member
    private Stage primaryStage;
    
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
    
    // Trade history components
    private ScrollPane tradeHistoryScrollPane;
    private VBox tradeHistoryContainer = new VBox(5);
    private Button toggleTradeHistoryButton = new Button("Show Trade History");
    private boolean tradeHistoryVisible = false;
    private final SimpleDateFormat tradeHistoryDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Add a data summary label to show total records and visible window
    private Label dataSummaryLabel = new Label("Data: 0 records (showing 0-0)");
    
    // Add a version label to track changes
    private Label versionLabel = new Label("VERSION #24");
    
    // Add a timer for continuous pretend trade visibility checks
    private Timer continuousCheckTimer;
    
    // Flag to prevent slider updates during data loading
    private AtomicBoolean isDataLoading = new AtomicBoolean(false);
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        // Store the primaryStage reference for use in setupChartControls
        this.primaryStage = primaryStage;
        
        primaryStage.setTitle("Performance Analysis");
        
        // Style the version label
        versionLabel.setFont(new Font("Arial", 24));
        versionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #AA0000;");
        versionLabel.setPadding(new Insets(5, 10, 5, 10));
        
        // Set version label style
        versionLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 8pt;");
        
        // Initialize chart with improved axis configuration
        xAxis.setLabel("Record #");
        yAxis.setLabel("$USD");
        xAxis.setAutoRanging(false);  // We'll control the range manually
        yAxis.setAutoRanging(false);  // We'll control the range manually
        yAxis.setForceZeroInRange(false); // Don't force zero in the range for better zoom
        
        // Simple number formatter for X-axis
        xAxis.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number value) {
                // The value is the sequence number
                int seq = value.intValue();
                
                // Find the corresponding data point if available
                synchronized (dataPoints) {
                    for (PerformanceData point : dataPoints) {
                        if (point.getSequence() == seq) {
                            // Format as "[offset #] - [2 digit year].[month].[day].[hour].[min]"
                            return seq + " - " + xAxisFormatter.format(new Date(point.getTimestamp()));
                        }
                    }
                }
                
                // Fallback if no matching data point is found
                return String.valueOf(seq);
            }

            @Override
            public Number fromString(String string) {
                try {
                    // Extract the sequence number from the beginning
                    if (string.contains(" - ")) {
                        return Integer.parseInt(string.substring(0, string.indexOf(" - ")));
                    }
                    return Integer.parseInt(string);
                } catch (Exception e) {
                    return 0;
                }
            }
        });
        
        // Set up series with different styles
        tradePriceSeries.setName("Trade Price");
        avgAskPriceSeries.setName("Avg Ask Price");
        avgBidPriceSeries.setName("Avg Bid Price");
        
        // Set up pretend trade series
        pretendBuySeries.setName("Pretend Buy");
        pretendSellSeries.setName("Pretend Sell");
        
        // Create the line chart
        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Performance Analysis");
        lineChart.setPrefHeight(600);  // Initial height, will be adjusted by layout constraints
        lineChart.setPrefWidth(1000);
        lineChart.setAnimated(false); // Disable animations for better performance
        lineChart.setCreateSymbols(true); // Show data points
        lineChart.setHorizontalGridLinesVisible(true);
        lineChart.setVerticalGridLinesVisible(true);
        lineChart.setHorizontalZeroLineVisible(false);
        lineChart.setVerticalZeroLineVisible(false);
        lineChart.setCache(true); // Enable caching for better performance
        lineChart.setCacheHint(CacheHint.SPEED); // Optimize for speed
        
        // Add more bottom padding for diagonal X-axis labels
        lineChart.setPadding(new Insets(10, 10, 40, 10));
        
        // Make the symbols larger and more visible and add specific styling for pretend trade series
        lineChart.setStyle(
            ".chart-series-line { -fx-stroke-width: 2px; } " +
            ".chart-symbol { -fx-background-radius: 5px; -fx-padding: 5px; } " +
            ".default-color3.chart-series-line { -fx-stroke: transparent; -fx-stroke-width: 0; } " + // Pretend Buy series (index 3)
            ".default-color4.chart-series-line { -fx-stroke: transparent; -fx-stroke-width: 0; }"    // Pretend Sell series (index 4)
        );
        
        // Initialize and configure the amount chart
        amountXAxis.setLabel("Record #");
        amountYAxis.setLabel("Amount");
        amountYAxis.setAutoRanging(false);   // We'll control the range manually
        amountYAxis.setForceZeroInRange(false); // Don't force zero in the range for better zoom
        
        // Use the same formatter for the amount chart X-axis
        amountXAxis.setTickLabelRotation(90); // Rotate labels 90 degrees
        
        // Set up amount series with the same colors as the price series
        tradeAmountSeries.setName("Trade Amount");
        avgAskAmountSeries.setName("Avg Ask Amount");
        avgBidAmountSeries.setName("Avg Bid Amount");
        
        // Create the bar chart
        amountChart = new BarChart<>(amountXAxis, amountYAxis);
        amountChart.setTitle("Amount Analysis");
        amountChart.setPrefHeight(200);  // Initial height, will be adjusted by layout constraints
        amountChart.setPrefWidth(1000);
        amountChart.setAnimated(false);
        amountChart.setHorizontalGridLinesVisible(true);
        amountChart.setVerticalGridLinesVisible(true);
        amountChart.setHorizontalZeroLineVisible(true);
        amountChart.setVerticalZeroLineVisible(false);
        amountChart.setCache(true); // Enable caching for better performance
        amountChart.setCacheHint(CacheHint.SPEED); // Optimize for speed
        
        // Configure bar chart specific properties with more space between bars
        amountChart.setCategoryGap(10);  // Increased from 2 for more space between categories
        amountChart.setBarGap(2);        // Increased from 1 for more space between bars in the same category
        
        // Add more bottom padding for diagonal X-axis labels
        amountChart.setPadding(new Insets(10, 10, 40, 10));
        
        // Add series to charts - do this before applying styles
        lineChart.getData().addAll(tradePriceSeries, avgAskPriceSeries, avgBidPriceSeries, pretendBuySeries, pretendSellSeries);
        amountChart.getData().addAll(tradeAmountSeries, avgAskAmountSeries, avgBidAmountSeries);
        
        // Apply custom colors to the series
        applySeriesStyles();
        
        // Style the pretend trade series to have no line
        if (pretendBuySeries.getNode() != null) {
            pretendBuySeries.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
        }
        if (pretendSellSeries.getNode() != null) {
            pretendSellSeries.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
        }
        
        // Style the pretend trade series legend symbols
        Platform.runLater(() -> {
            Node buyLegendSymbol = pretendBuySeries.getNode().lookup(".chart-legend-item-symbol");
            if (buyLegendSymbol != null) {
                buyLegendSymbol.setStyle("-fx-background-color: #00AA00; -fx-background-radius: 0px; -fx-padding: 8px;");
                buyLegendSymbol.setRotate(180); // Point upward
            }
            
            Node sellLegendSymbol = pretendSellSeries.getNode().lookup(".chart-legend-item-symbol");
            if (sellLegendSymbol != null) {
                sellLegendSymbol.setStyle("-fx-background-color: #AA0000; -fx-background-radius: 0px; -fx-padding: 8px;");
                sellLegendSymbol.setRotate(0); // Point downward
            }
        });
        
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
        
        // Set up examine button
        visualReplayButton.setPrefSize(250, 30);
        visualReplayButton.setFont(new Font("Arial", 14));
        visualReplayButton.setOnAction(event -> startPerformanceAnalysis());
//        quickReplayButton.setPrefSize(250, 30);
//        quickReplayButton.setFont(new Font("Arial", 14));
//        quickReplayButton.setOnAction(event -> startQuickReplayAnalysis());
        
        // Set up status label
        statusLabel.setFont(new Font("Arial", 14));
        
        // Style the data summary label
        dataSummaryLabel.setFont(new Font("Arial", 14));
        dataSummaryLabel.setStyle("-fx-font-weight: bold;");
        
        // Create input layout
        HBox inputBox = new HBox(10, upsLabel, upsField, downsLabel, downsField, visualReplayButton);

        inputBox.setAlignment(Pos.CENTER);
        inputBox.setPadding(new Insets(10));
        
        // Create a container for the version label and status label
        HBox statusBox = new HBox(10);
        statusBox.getChildren().addAll(versionLabel, statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPadding(new Insets(5, 10, 5, 10));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        // Create time slider for navigating through historical data
        timeSlider = new Slider(0, 1, 1);
        timeSlider.setPrefWidth(1200);  // Make slider longer (80% of typical window width)
        timeSlider.setMaxWidth(Double.MAX_VALUE);  // Allow slider to stretch with window
        timeSlider.setShowTickMarks(true);
        timeSlider.setShowTickLabels(true);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);  // Allow slider to grow horizontally
        
        // Add listener to time slider
        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (oldVal.doubleValue() != newVal.doubleValue() && !tradePriceSeries.getData().isEmpty()) {
                // When slider is moved, exit live mode
                if (liveMode.get() && oldVal.doubleValue() != 1.0) {
                    liveMode.set(false);
                    liveButton.setText("Go Live");
                }
                
                // Use a timer to debounce slider events
                if (resizeTimer != null) {
                    resizeTimer.cancel();
                }
                resizeTimer = new Timer();
                resizeTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            updateChartView(newVal.doubleValue());
                        });
                    }
                }, 100); // 100ms delay for smoother updates
            }
        });
        
        // Create live button
        liveButton = new Button("Live");
        liveButton.setPrefSize(100, 30);
        liveButton.setFont(new Font("Arial", 14));
        liveButton.setOnAction(event -> {
            liveMode.set(!liveMode.get());
            if (liveMode.get()) {
                liveButton.setText("Live");
                timeSlider.setValue(1.0);
                updateChartView(1.0);
            } else {
                liveButton.setText("Go Live");
            }
        });
        
        // Create slider layout
        HBox sliderBox = new HBox(10, timeSlider, liveButton);
        sliderBox.setAlignment(Pos.CENTER);
        sliderBox.setPadding(new Insets(10, 10, 0, 10));
        
        // Wrap the price chart in a ScrollPane
        chartScrollPane = new ScrollPane(lineChart);
        chartScrollPane.setFitToHeight(true);
        chartScrollPane.setFitToWidth(true);
        chartScrollPane.setPannable(true);
        chartScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Use our custom slider instead
        chartScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(chartScrollPane, Priority.ALWAYS);  // Allow to grow vertically
        
        // Wrap the amount chart in a ScrollPane
        ScrollPane amountScrollPane = new ScrollPane(amountChart);
        amountScrollPane.setFitToHeight(true);
        amountScrollPane.setFitToWidth(true);
        amountScrollPane.setPannable(true);
        amountScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Use our custom slider instead
        amountScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(amountScrollPane, Priority.ALWAYS);  // Allow to grow vertically
        
        // Create a VBox to hold both charts with a SplitPane to allow resizing
        VBox chartsBox = new VBox(10);
        VBox.setVgrow(chartsBox, Priority.ALWAYS);  // Allow charts box to grow vertically
        
        // Create a SplitPane to hold both charts with vertical orientation
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(chartScrollPane, amountScrollPane);
        splitPane.setDividerPositions(0.75);
        VBox.setVgrow(splitPane, Priority.ALWAYS);  // Allow split pane to grow vertically
        
        chartsBox.getChildren().add(splitPane);
        
        // Style the balance and profit labels
        balanceLabel.setFont(new Font("Arial", 20));
        balanceLabel.setStyle("-fx-font-weight: bold;");
        currentBalanceLabel.setFont(new Font("Arial", 20));
        currentBalanceLabel.setStyle("-fx-font-weight: bold;");
        profitLabel.setFont(new Font("Arial", 20));
        profitLabel.setStyle("-fx-font-weight: bold;");
        usdBalanceLabel.setFont(new Font("Arial", 16));
        usdBalanceLabel.setStyle("-fx-font-weight: bold;");
        coinBalanceLabel.setFont(new Font("Arial", 16));
        coinBalanceLabel.setStyle("-fx-font-weight: bold;");
        
        // Style the data summary label
        dataSummaryLabel.setFont(new Font("Arial", 14));
        dataSummaryLabel.setStyle("-fx-font-weight: bold;");
        
        // Create a HBox for the main balance and profit labels
        HBox balanceBox = new HBox(20, balanceLabel, currentBalanceLabel, profitLabel);
        balanceBox.setAlignment(Pos.CENTER);
        balanceBox.setPadding(new Insets(5, 0, 5, 0));

        // Create a separate HBox for the data summary
        HBox dataSummaryBox = new HBox(dataSummaryLabel);
        dataSummaryBox.setAlignment(Pos.CENTER);
        dataSummaryBox.setPadding(new Insets(0, 0, 5, 0));
        
        // Set up trade history components
        tradeHistoryContainer = new VBox(5);
        tradeHistoryContainer.setPadding(new Insets(10));
        tradeHistoryContainer.setStyle("-fx-background-color: #f8f8f8;");
        tradeHistoryContainer.setMinWidth(800); // Increased to accommodate all columns
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
        VBox scrollContent = new VBox();
        HBox placeholderRow = new HBox();
        placeholderRow.setPadding(new Insets(10, 5, 5, 5));
        
        Label placeholder = new Label("No trades yet - Trades will appear here when executed");
        placeholder.setStyle("-fx-text-fill: #888888;");
        placeholderRow.getChildren().add(placeholder);
        
        scrollContent.getChildren().add(placeholderRow);
        
        // Create scroll pane with just the trade data (not headers)
        tradeHistoryScrollPane = new ScrollPane(scrollContent);
        tradeHistoryScrollPane.setFitToWidth(true);
        tradeHistoryScrollPane.setPrefHeight(180); // Height for approximately 5 rows
        tradeHistoryScrollPane.setVisible(true); // Make visible by default
        tradeHistoryScrollPane.setManaged(true); // Make managed by default
        tradeHistoryVisible = true; // Set to visible by default
        
        // Create a container that includes both fixed header and scrollable content
        VBox tradeHistoryWithFixedHeader = new VBox(0);
        tradeHistoryWithFixedHeader.setStyle("-fx-border-color: #ddd; -fx-border-width: 1px;");
        tradeHistoryWithFixedHeader.getChildren().addAll(headerBox, tradeHistoryScrollPane);
        
        // Remove toggle button functionality since we always want to show trade history
        toggleTradeHistoryButton.setVisible(false);
        toggleTradeHistoryButton.setManaged(false);
        
        // Create a VBox for the trade history components
        VBox tradeHistoryBox = new VBox(5, tradeHistoryWithFixedHeader);
        tradeHistoryBox.setAlignment(Pos.CENTER);
        
        // Update the root VBox to include the new dataSummaryBox
        VBox root = new VBox(10, inputBox, statusBox, balanceBox, dataSummaryBox, tradeHistoryBox, chartsBox, sliderBox);
        root.setPadding(new Insets(10));
        VBox.setVgrow(chartsBox, Priority.ALWAYS);  // Allow charts box to grow vertically
        
        // Create scene with increased height (1000 instead of 800)
        Scene scene = new Scene(root, 1280, 1000);  // Increased height by 200 pixels
        scene.getStylesheets().add(getClass().getResource("/chart.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Initialize WebSocket client
        performanceClient = new PerformanceAnalysisClient(this);
        
        // Initialize chart with some default values to ensure it displays properly
        initializeChart();
        
        // Setup chart controls
        setupChartControls();
        
        // Start continuous checks for pretend trade visibility
        startContinuousChecksForPretendTrades();
        
        // Schedule immediate checks for pretend trades visibility
        scheduleMultipleChecksForPretendTrades();
        
        // Force a layout pass to ensure everything is properly displayed
                    Platform.runLater(() -> {
                        lineChart.layout();
                        amountChart.layout();
                        
            // Ensure pretend trade series are in the chart
            ensurePretendTradesVisible();
        });
    }
    
    /**
     * Sets up the chart controls, including slider and window resize listeners.
     */
    private void setupChartControls() {
        // Add scroll listener to the slider
        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingSlider && !isDataLoading.get() && performanceClient != null) {
                isUpdatingSlider = true;
                try {
                    // Only update if the value has actually changed
                    if (oldVal == null || oldVal.doubleValue() != newVal.doubleValue()) {
                        // Cancel any pending resize timer
                        if (resizeTimer != null) {
                            resizeTimer.cancel();
                        }
                        
                        // Update the chart view with the new slider value
                        updateChartView(newVal.doubleValue());
                        
                        // Ensure pretend trades are visible when scrolling starts
                        ensurePretendTradesVisible();
                    }
                } finally {
                    isUpdatingSlider = false;
                }
            }
        });

        // Add scroll stop listener to the slider
        timeSlider.setOnMouseReleased(event -> {
            // Only process if not loading data
            if (!isDataLoading.get()) {
                // Schedule multiple checks to ensure pretend trades remain visible after scrolling stops
                scheduleMultipleChecksForPretendTrades();
            }
        });

        // Add mouse pressed listener to ensure pretend trades are visible when scrolling starts
        timeSlider.setOnMousePressed(event -> {
            // Only process if not loading data
            if (!isDataLoading.get()) {
                ensurePretendTradesVisible();
            }
        });

        // Add window resize listener
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Schedule multiple checks to ensure pretend trades remain visible after resize
                scheduleMultipleChecksForPretendTrades();
            }
        });

        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Schedule multiple checks to ensure pretend trades remain visible after resize
                scheduleMultipleChecksForPretendTrades();
            }
        });

        // Add a listener for when the user releases the mouse after dragging the slider
        timeSlider.setOnMouseReleased(event -> {
            if (!isUpdatingSlider) {
                    Platform.runLater(() -> {
                    ensurePretendTradesVisible();
                    disablePretendTradeLines();
                    
                    // Force a layout pass
                        lineChart.layout();
                    
                    // Schedule a final check after a short delay
                    PauseTransition pause = new PauseTransition(Duration.millis(100));
                    pause.setOnFinished(e -> {
                        ensurePretendTradesVisible();
                        disablePretendTradeLines();
                        lineChart.layout();
                    });
                    pause.play();
                });
            }
        });

        // Add window resize listeners
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                ensurePretendTradesVisible();
                disablePretendTradeLines();
                lineChart.layout();
            });
        });
        
        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                ensurePretendTradesVisible();
                disablePretendTradeLines();
                lineChart.layout();
            });
        });
    }
    
    /**
     * Updates the chart view based on the slider position.
     * 
     * @param sliderValue The value of the slider (0.0 to 1.0)
     */
    private void updateChartView(double sliderValue) {
        // Check if performanceClient is null
        if (performanceClient == null) {
            return;
        }
        
        // Get the current dataset size from the client
        final int totalPoints = performanceClient.getDatasetSize();
        final int totalRecords = performanceClient.getTotalRecordsReceived();
        
        if (totalPoints == 0) {
                return;
            }
            
            // Calculate the visible range based on slider value
        final int visiblePoints = Math.min(VISIBLE_DATA_POINTS, totalPoints);
            
            // Calculate the start index based on slider value
        final int startIndex = Math.max(0, Math.min(
            (int) Math.round((totalPoints - visiblePoints) * sliderValue),
            totalPoints - visiblePoints));
        
        // Update the data summary label
        final int endIndex = Math.min(startIndex + visiblePoints - 1, totalPoints - 1);
        Platform.runLater(() -> {
            dataSummaryLabel.setText(String.format("Data: %,d records (showing %,d-%,d of %,d)", 
                totalRecords, startIndex + 1, endIndex + 1, totalPoints));
        });
        
        // Get the window of data from the client
        List<PerformanceData> windowData = 
            performanceClient.getDataWindow(startIndex, visiblePoints);
        
        if (windowData.isEmpty()) {
            return;
        }
        
        // First, update the trade history with all pretend trades in the window
        updateTradeHistoryFromWindow(windowData);
        
        // Then update the chart with the window data
        updateChartWithWindowData(windowData, startIndex);
    }
    
    /**
     * Update the chart with data from the current window
     */
    private void updateChartWithWindowData(List<PerformanceData> windowData, int startIndex) {
        // First, ensure pretend trades are visible
        ensurePretendTradesVisible();
        
        // Store all pretend trades from the window data for later re-addition
        // CRITICAL: Filter ONLY for pretend trades by checking if pretendTrade is not null
        List<PerformanceData> pretendTradePoints = windowData.stream()
            .filter(p -> p.getPretendTrade() != null)
            .toList();
        

        // Set data loading flag to true
        isDataLoading.set(true);
        
        Platform.runLater(() -> {
            try {
                // Store current pretend trade data before clearing
//                List<XYChart.Data<Number, Number>> currentPretendBuyData = new ArrayList<>(pretendBuySeries.getData());
//                List<XYChart.Data<Number, Number>> currentPretendSellData = new ArrayList<>(pretendSellSeries.getData());
                
                // Clear existing data in price chart
                tradePriceSeries.getData().clear();
                avgAskPriceSeries.getData().clear();
                avgBidPriceSeries.getData().clear();
                
                // Clear existing data in amount chart
                tradeAmountSeries.getData().clear();
                avgAskAmountSeries.getData().clear();
                avgBidAmountSeries.getData().clear();
                
                // Clear existing categories in amount chart
                amountXAxis.getCategories().clear();
                
                // Clear existing pretend trade data
                pretendBuySeries.getData().clear();
                pretendSellSeries.getData().clear();
                
                // Ensure pretend trade series are in the chart and properly styled
                if (!lineChart.getData().contains(pretendBuySeries)) {
                    lineChart.getData().add(pretendBuySeries);
                }
                if (!lineChart.getData().contains(pretendSellSeries)) {
                    lineChart.getData().add(pretendSellSeries);
                }
                
                // Style the pretend trade series to have no line
                if (pretendBuySeries.getNode() != null) {
                    pretendBuySeries.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
                }
                if (pretendSellSeries.getNode() != null) {
                    pretendSellSeries.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
                }
            
            // Find min and max Y values in the visible range to auto-scale Y-axis
            double minY = Double.MAX_VALUE;
            double maxY = Double.MIN_VALUE;
                double maxAmount = 0.0;
                
                // Batch data points for more efficient rendering
                List<XYChart.Data<Number, Number>> tradePriceData = new ArrayList<>();
                List<XYChart.Data<Number, Number>> avgAskPriceData = new ArrayList<>();
                List<XYChart.Data<Number, Number>> avgBidPriceData = new ArrayList<>();
                
                // Lists for amount chart data
                List<String> amountCategories = new ArrayList<>();
                List<XYChart.Data<String, Number>> tradeAmountData = new ArrayList<>();
                List<XYChart.Data<String, Number>> avgAskAmountData = new ArrayList<>();
                List<XYChart.Data<String, Number>> avgBidAmountData = new ArrayList<>();
                
                // Process each data point in the window
                for (int i = 0; i < windowData.size(); i++) {
                    PerformanceData point = windowData.get(i);
                    int seq = point.getSequence();
                    
                    // Add data to the price series
                double tradePrice = point.getTradePrice();
                    tradePriceData.add(new XYChart.Data<>(seq, tradePrice));
                    if (tradePrice > 0) {
                    minY = Math.min(minY, tradePrice);
                    maxY = Math.max(maxY, tradePrice);
                }
                
                double askPrice = point.getAvgAskPrice();
                    avgAskPriceData.add(new XYChart.Data<>(seq, askPrice));
                    if (askPrice > 0) {
                    minY = Math.min(minY, askPrice);
                    maxY = Math.max(maxY, askPrice);
                }
                
                double bidPrice = point.getAvgBidPrice();
                    avgBidPriceData.add(new XYChart.Data<>(seq, bidPrice));
                    if (bidPrice > 0) {
                    minY = Math.min(minY, bidPrice);
                    maxY = Math.max(maxY, bidPrice);
                }
                    
                    // Add pretend trade to the price chart if present
                    if (point.getPretendTrade() != null && point.getPretendTrade().getPrice() != null) {
                        com.ibbe.entity.Trade trade = point.getPretendTrade();
                        double pretendTradePrice = trade.getPrice().doubleValue();
                        if (pretendTradePrice > 0) {
                            minY = Math.min(minY, pretendTradePrice);
                            maxY = Math.max(maxY, pretendTradePrice);
                        }
                        
                        // Add the trade point to the appropriate pretend trade series
                        XYChart.Data<Number, Number> tradePoint = new XYChart.Data<>(seq, pretendTradePrice);
                        String tradeType = trade.getMakerSide();
                        
                        if (tradeType.toLowerCase().contains("buy")) {
                            pretendBuySeries.getData().add(tradePoint);
                            
                            // Style the trade point as a solid green triangle
                            if (tradePoint.getNode() != null) {
                                tradePoint.getNode().setStyle("-fx-background-color: #00AA00; -fx-background-radius: 0px; -fx-padding: 8px;");
                                tradePoint.getNode().setRotate(180); // Point upward
                            }
                        } else {
                            pretendSellSeries.getData().add(tradePoint);
                            
                            // Style the trade point as a solid red triangle
                            if (tradePoint.getNode() != null) {
                                tradePoint.getNode().setStyle("-fx-background-color: #AA0000; -fx-background-radius: 0px; -fx-padding: 8px;");
                                tradePoint.getNode().setRotate(0); // Point downward
                            }
                        }
                        
                        // Add tooltip to the trade point
                        StringBuilder tooltipText = new StringBuilder();
                        tooltipText.append("Trade Type: ").append(tradeType.toUpperCase()).append("\n");
                        tooltipText.append("Price: ").append(String.format("%.2f", trade.getPrice())).append("\n");
                        tooltipText.append("Amount: ").append(String.format("%.4f", trade.getAmount())).append("\n");
                        tooltipText.append("Time: ").append(dateFormatter.format(new Date(point.getTimestamp()))).append("\n");
                        
                        Tooltip tooltip = new Tooltip(tooltipText.toString());
                        Tooltip.install(tradePoint.getNode(), tooltip);
                    }
                    
                    // Add data to the amount chart
                    String seqStr = String.valueOf(seq);
                    amountCategories.add(seqStr);
                    
                    double tradeAmount = point.getTradeAmount();
                    if (tradeAmount > 0) {
                        tradeAmountData.add(new XYChart.Data<>(seqStr, tradeAmount));
                        maxAmount = Math.max(maxAmount, tradeAmount);
                    }
                    
                    double askAmount = point.getAvgAskAmount();
                    if (askAmount > 0) {
                        avgAskAmountData.add(new XYChart.Data<>(seqStr, askAmount));
                        maxAmount = Math.max(maxAmount, askAmount);
                    }
                    
                    double bidAmount = point.getAvgBidAmount();
                    if (bidAmount > 0) {
                        avgBidAmountData.add(new XYChart.Data<>(seqStr, bidAmount));
                        maxAmount = Math.max(maxAmount, bidAmount);
                    }
                }
                
                // Add all data points to the series at once for better performance
                tradePriceSeries.getData().addAll(tradePriceData);
                avgAskPriceSeries.getData().addAll(avgAskPriceData);
                avgBidPriceSeries.getData().addAll(avgBidPriceData);
                
                // Add categories to amount chart
                amountXAxis.getCategories().addAll(amountCategories);
                
                // Add amount data to series
                tradeAmountSeries.getData().addAll(tradeAmountData);
                avgAskAmountSeries.getData().addAll(avgAskAmountData);
                avgBidAmountSeries.getData().addAll(avgBidAmountData);
                
                // Apply styles to the amount chart series
                for (XYChart.Data<String, Number> data : tradeAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #ff0000;");
                    }
                }
                
                for (XYChart.Data<String, Number> data : avgAskAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #00ff00;");
                    }
                }
                
                for (XYChart.Data<String, Number> data : avgBidAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #0000ff;");
                }
            }
            
            // Add some padding to the Y-axis range (5%)
            double padding = (maxY - minY) * 0.05;
            if (padding < 1) padding = 1000; // Default padding if range is too small
            
            // Update Y-axis range if we found valid min/max values
            if (minY != Double.MAX_VALUE && maxY != Double.MIN_VALUE) {
                yAxis.setLowerBound(minY - padding);
                yAxis.setUpperBound(maxY + padding);
                }
                
                // Set a reasonable range for the amount chart
                if (maxAmount > 0) {
                    amountYAxis.setLowerBound(0);
                    amountYAxis.setUpperBound(maxAmount * 1.1); // Add 10% padding
            } else {
                    amountYAxis.setLowerBound(0);
                    amountYAxis.setUpperBound(1);
                }
                
                // Update X-axis range to match the window data
                int minSeq = windowData.get(0).getSequence();
                int maxSeq = windowData.get(windowData.size() - 1).getSequence();
                xAxis.setLowerBound(minSeq);
                xAxis.setUpperBound(maxSeq);
                xAxis.setTickUnit(Math.max(1, (maxSeq - minSeq) / 10)); // Adjust tick unit for readability
                
                // Apply styles to the data points
                applySeriesStyles();
            
            // Force a layout pass to ensure both charts are updated
                lineChart.layout();
                amountChart.layout();
                
                // Update the trade history with all pretend trades in the window
                updateTradeHistoryFromWindow(windowData);
                
                // Double-check that all pretend trades are still in the chart
                // This is a critical step to ensure they remain visible
                ensurePretendTradesVisible();
                disablePretendTradeLines();
                
                // Schedule multiple checks to ensure pretend trades remain visible
                scheduleMultipleChecksForPretendTrades();
                
                // Add a final check after a longer delay to catch any late rendering issues
                Timer finalCheckTimer = new Timer();
                finalCheckTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
            Platform.runLater(() -> {
                            try {
                                // Re-add all pretend trades from the window data
                                for (PerformanceData point : pretendTradePoints) {
                                    if (point.getPretendTrade() != null && point.getPretendTrade().getPrice() != null) {
                                        com.ibbe.entity.Trade trade = point.getPretendTrade();
                                        int seq = point.getSequence();
                                        double pretendTradePrice = trade.getPrice().doubleValue();
                                        
                                        // Add the trade point to the appropriate pretend trade series
                                        XYChart.Data<Number, Number> tradePoint = new XYChart.Data<>(seq, pretendTradePrice);
                                        String tradeType = trade.getMakerSide();
                                        
                                        // Check if this point already exists in the series
                                        boolean exists = false;
                                        if (tradeType.toLowerCase().contains("buy")) {
                                            for (XYChart.Data<Number, Number> existingPoint : pretendBuySeries.getData()) {
                                                if (existingPoint.getXValue().equals(seq)) {
                                                    exists = true;
                                                    break;
                                                }
                                            }
                                            
                                            if (!exists) {
                                                pretendBuySeries.getData().add(tradePoint);
                                                
                                                // Style the trade point as a solid green triangle
                                                if (tradePoint.getNode() != null) {
                                                    tradePoint.getNode().setStyle("-fx-background-color: #00AA00; -fx-background-radius: 0px; -fx-padding: 8px;");
                                                    tradePoint.getNode().setRotate(180); // Point upward
                                                }
                                            }
                                        } else {
                                            for (XYChart.Data<Number, Number> existingPoint : pretendSellSeries.getData()) {
                                                if (existingPoint.getXValue().equals(seq)) {
                                                    exists = true;
                                                    break;
                                                }
                                            }
                                            
                                            if (!exists) {
                                                pretendSellSeries.getData().add(tradePoint);
                                                
                                                // Style the trade point as a solid red triangle
                                                if (tradePoint.getNode() != null) {
                                                    tradePoint.getNode().setStyle("-fx-background-color: #AA0000; -fx-background-radius: 0px; -fx-padding: 8px;");
                                                    tradePoint.getNode().setRotate(0); // Point downward
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // Ensure pretend trades are visible one final time
                                ensurePretendTradesVisible();
                                disablePretendTradeLines();
                                
                                // Force one final layout pass
                lineChart.layout();
                amountChart.layout();
                            } catch (Exception e) {
                                System.err.println("Error in final pretend trade check: " + e.getMessage());
                                e.printStackTrace();
                            } finally {
                                finalCheckTimer.cancel();
                            }
                        });
                    }
                }, 1000); // 1 second delay for final check
            } catch (Exception e) {
                System.err.println("Error updating chart with window data: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Set data loading flag to false
                isDataLoading.set(false);
            }
        });
    }
    
    /**
     * Adds a data point to the chart
     */
    private void addDataPoint(long timestamp, double price, double amount) {
        // ... existing code ...
        
        // Ensure pretend trades remain visible after adding new data
        if (!isDataLoading.get()) {
            Platform.runLater(() -> {
                ensurePretendTradesVisible();
                disablePretendTradeLines();
            });
        }
    }
    
    /**
     * Schedule multiple checks to ensure pretend trades remain visible
     * This helps with both initial loading and after scrolling
     */
    private void scheduleMultipleChecksForPretendTrades() {
        // Schedule checks at different intervals
        schedulePretendTradeVisibilityCheck(500);  // Check after 500ms
        schedulePretendTradeVisibilityCheck(1000); // Check after 1 second
        schedulePretendTradeVisibilityCheck(2000); // Check after 2 seconds
        schedulePretendTradeVisibilityCheck(5000); // Check after 5 seconds
    }
    
    /**
     * Schedule a delayed check for pretend trades visibility
     */
    private void scheduleDelayedPretendTradesCheck(int delayMs) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    ensurePretendTradesVisible();
                    timer.cancel();
                });
            }
        }, delayMs);
    }
    
    /**
     * Completely disable the connecting lines for the pretend trade series
     * This is a more aggressive approach to ensure no lines are shown
     */
    private void disablePretendTradeLines() {
        try {
            // Apply CSS to completely disable the lines
            for (XYChart.Series<Number, Number> series : lineChart.getData()) {
                if (series == pretendBuySeries || series == pretendSellSeries) {
                    // Get all line segments in the series
                    for (Node node : series.getNode().lookupAll(".chart-series-line")) {
                        // Make the line completely invisible
                        node.setVisible(false);
                        node.setManaged(false);
                        node.setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
                    }
                    
                    // Apply style to the series node itself
                    series.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
                }
            }
        } catch (Exception e) {
            System.err.println("Error disabling pretend trade lines: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Ensure pretend trades are visible in the chart and fix legend appearance
     */
    private void ensurePretendTradesVisible() {
        try {
            // Ensure pretend trade series are in the chart
            if (!lineChart.getData().contains(pretendBuySeries)) {
                lineChart.getData().add(pretendBuySeries);
            }
            if (!lineChart.getData().contains(pretendSellSeries)) {
                lineChart.getData().add(pretendSellSeries);
            }
            
            // Reapply styles to ensure they persist
            if (pretendBuySeries.getNode() != null) {
                pretendBuySeries.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
            }
            if (pretendSellSeries.getNode() != null) {
                pretendSellSeries.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
            }
            
            // Fix the legend items to match the actual appearance of the pretend trades
            // This corrects the legend display to show triangles with proper colors
            Node buyLegendSymbol = pretendBuySeries.getNode().lookup(".chart-legend-item-symbol");
            if (buyLegendSymbol != null) {
                buyLegendSymbol.setStyle("-fx-background-color: #00AA00; -fx-background-radius: 0px; -fx-padding: 8px;");
                buyLegendSymbol.setRotate(180); // Point upward
            }
            
            Node sellLegendSymbol = pretendSellSeries.getNode().lookup(".chart-legend-item-symbol");
            if (sellLegendSymbol != null) {
                sellLegendSymbol.setStyle("-fx-background-color: #AA0000; -fx-background-radius: 0px; -fx-padding: 8px;");
                sellLegendSymbol.setRotate(0); // Point downward
            }
            
            // Apply styles to individual data points in the pretend trade series
            for (XYChart.Data<Number, Number> data : pretendBuySeries.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-background-color: #00AA00; -fx-background-radius: 0px; -fx-padding: 8px;");
                    data.getNode().setRotate(180); // Point upward
                } else {
                    // If node is null, force creation by accessing it
                    Platform.runLater(() -> {
                        if (data.getNode() != null) {
                            data.getNode().setStyle("-fx-background-color: #00AA00; -fx-background-radius: 0px; -fx-padding: 8px;");
                            data.getNode().setRotate(180); // Point upward
                        }
                    });
                }
            }
            
            for (XYChart.Data<Number, Number> data : pretendSellSeries.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-background-color: #AA0000; -fx-background-radius: 0px; -fx-padding: 8px;");
                    data.getNode().setRotate(0); // Point downward
                } else {
                    // If node is null, force creation by accessing it
                    Platform.runLater(() -> {
                        if (data.getNode() != null) {
                            data.getNode().setStyle("-fx-background-color: #AA0000; -fx-background-radius: 0px; -fx-padding: 8px;");
                            data.getNode().setRotate(0); // Point downward
                        }
                    });
                }
            }
            
            // Ensure the pretend trade series are at the top of the chart (last in the list)
            // This makes them more visible
            if (lineChart.getData().contains(pretendBuySeries)) {
                lineChart.getData().remove(pretendBuySeries);
                lineChart.getData().add(pretendBuySeries);
            }
            if (lineChart.getData().contains(pretendSellSeries)) {
                lineChart.getData().remove(pretendSellSeries);
                lineChart.getData().add(pretendSellSeries);
            }
            
            // Completely disable the connecting lines
            disablePretendTradeLines();
            
            // Force a layout pass to ensure everything is properly displayed
            lineChart.layout();
            amountChart.layout();
            
            // Force a repaint of the chart
            lineChart.setAnimated(true);
            lineChart.setAnimated(false);
        } catch (Exception e) {
            System.err.println("Error ensuring pretend trades visibility: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Updates the trade history with ONLY the pretend trades in the window.
     * 
     * @param windowData The window of data to display
     */
    private void updateTradeHistoryFromWindow(List<PerformanceData> windowData) {

        // STRICT FILTER: Only take points where pretendTrade is not null
        final List<PerformanceData> pretendTradePointsOnly = windowData.stream()
            .filter(p -> p.getPretendTrade() != null) 
            .sorted(Comparator.comparingLong(PerformanceData::getTimestamp))
            .collect(Collectors.toList());
        

        // If no pretend trades, show empty message
        if (pretendTradePointsOnly.isEmpty()) {
            Platform.runLater(() -> {
                VBox tradeDataContainer = new VBox(2);
                Label emptyLabel = new Label("NO PRETEND TRADES IN CURRENT VIEW");
                emptyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #AA0000;");
                tradeDataContainer.getChildren().add(emptyLabel);
                
                tradeHistoryScrollPane.setContent(tradeDataContainer);
                tradeHistoryScrollPane.setPrefHeight(150);
                tradeHistoryScrollPane.setMaxHeight(150);
                tradeHistoryScrollPane.setMinHeight(150);
            });
            return;
        }
        
        // Reset running balances to ensure accurate calculations
        final BigDecimal startCurrency = startingCurrencyBalance == null ? 
            BigDecimal.ZERO : new BigDecimal(startingCurrencyBalance.toString());
        final BigDecimal startCoin = startingCoinBalance == null ?
            BigDecimal.ZERO : new BigDecimal(startingCoinBalance.toString());
        
        // Update trade history UI components
        Platform.runLater(() -> {
            try {
                // Create completely new container for trade data
                VBox tradeDataContainer = new VBox(0);
                tradeDataContainer.setStyle("-fx-background-color: white;");
                
//                // Create table header row
//                HBox headerBox = new HBox(5);
//                headerBox.setPadding(new Insets(5, 5, 5, 5));
//                headerBox.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #dddddd; -fx-border-width: 0 0 1 0;");
//
//                // Create column headers with same widths as data rows will use
//                Label dateHeader = new Label("Date");
//                dateHeader.setPrefWidth(150);
//                dateHeader.setStyle("-fx-font-weight: bold;");
//
//                Label typeHeader = new Label("Type");
//                typeHeader.setPrefWidth(60);
//                typeHeader.setStyle("-fx-font-weight: bold;");
//
//                Label priceHeader = new Label("Price");
//                priceHeader.setPrefWidth(100);
//                priceHeader.setStyle("-fx-font-weight: bold;");
//
//                Label amtHeader = new Label("Amount");
//                amtHeader.setPrefWidth(100);
//                amtHeader.setStyle("-fx-font-weight: bold;");
//
//                Label seqHeader = new Label("Seq");
//                seqHeader.setPrefWidth(60);
//                seqHeader.setStyle("-fx-font-weight: bold;");
//
//                Label balUsdHeader = new Label("Bal$$");
//                balUsdHeader.setPrefWidth(100);
//                balUsdHeader.setStyle("-fx-font-weight: bold;");
//
//                Label balCoinHeader = new Label("BalC");
//                balCoinHeader.setPrefWidth(100);
//                balCoinHeader.setStyle("-fx-font-weight: bold;");
//
//                Label profitHeader = new Label("Profit");
//                profitHeader.setPrefWidth(100);
//                profitHeader.setStyle("-fx-font-weight: bold;");
                
//                // Add headers to row
//                headerBox.getChildren().addAll(
//                    dateHeader, typeHeader, priceHeader, amtHeader,
//                    seqHeader, balUsdHeader, balCoinHeader, profitHeader
//                );
                
                // Add header row to container
//                tradeDataContainer.getChildren().add(headerBox);
                
                // Initialize running balances
                BigDecimal runningCurrency = startCurrency;
                BigDecimal runningCoin = startCoin;
                BigDecimal runningProfit = new BigDecimal(0);

                
                // Process ONLY pretend trades
                for (PerformanceData dp : pretendTradePointsOnly) {
                    // Skip if somehow not a valid pretend trade (defensive)
                    if (dp.getPretendTrade() == null) {
//                        System.out.println("WARNING: Skipping null pretend trade at seq " + dp.getSequence());
                        continue;
                    }
                    
                    // Get trade details
                    String tradeType = dp.getPretendTrade().getMakerSide();
                    boolean isBuy = tradeType.toLowerCase().contains("buy");
                    BigDecimal tradePrice = dp.getPretendTrade().getPrice();
                    BigDecimal tradeAmount = dp.getPretendTrade().getAmount();
                    BigDecimal tradeValue = tradePrice.multiply(tradeAmount);
                    runningCurrency = dp.getFxTradesDisplayData().getCurrencyBalance();
                    runningCoin = dp.getFxTradesDisplayData().getCoinBalance();
                    runningProfit = dp.getFxTradesDisplayData().getProfit();
                    // Update running balances
//                    if (isBuy) { // Buy
//                        runningCurrency = runningCurrency.subtract(tradeValue);
//                        runningCoin = runningCoin.add(tradeAmount);
//                    } else { // Sell
//                        runningCurrency = runningCurrency.add(tradeValue);
//                        runningCoin = runningCoin.subtract(tradeAmount);
//                    }
                    
                    // Calculate profit
//                    BigDecimal currentValue = runningCurrency.add(runningCoin.multiply(tradePrice));
//                    BigDecimal profit = currentValue.subtract(startCurrency);
                    
                    // Create trade row
                    HBox row = new HBox(5);
                    row.setPadding(new Insets(3, 5, 3, 5));
                    row.setStyle("-fx-border-color: #eeeeee; -fx-border-width: 0 0 1 0;");
                    
                    // Create data cells
                    Label dateLabel = new Label(tradeHistoryDateFormat.format(new Date(dp.getTimestamp())));
                    dateLabel.setPrefWidth(150);
                    
                    Label typeLabel = new Label(isBuy ? "BUY" : "SELL");
                    typeLabel.setPrefWidth(60);
                    typeLabel.setTextFill(isBuy ? Color.GREEN : Color.RED);
                    
                    Label priceLabel = new Label(tradePrice.toPlainString());
                    priceLabel.setPrefWidth(80);
                    
                    Label amtLabel = new Label(tradeAmount.toPlainString());
                    amtLabel.setPrefWidth(80);
                    
                    Label seqLabel = new Label(Integer.toString(dp.getSequence()));
                    seqLabel.setPrefWidth(50);
                    
                    Label balUsdLabel = new Label(runningCurrency.toPlainString());
                    balUsdLabel.setPrefWidth(80);
                    
                    Label balCoinLabel = new Label(runningCoin.toPlainString());
                    balCoinLabel.setPrefWidth(80);
                    
                    Label profitLabel = new Label(runningProfit.toPlainString());
                    profitLabel.setPrefWidth(80);
                    profitLabel.setTextFill(runningProfit.compareTo(BigDecimal.ZERO) >= 0 ? Color.GREEN : Color.RED);
                    
                    // Add cells to row
                    row.getChildren().addAll(
                        dateLabel, typeLabel, priceLabel, amtLabel, seqLabel,
                        balUsdLabel, balCoinLabel, profitLabel
                    );
                    
                    // Add row to container
                    tradeDataContainer.getChildren().add(row);
//                    runningProfit = dp.getFxTradesDisplayData().getProfit();
                }
                
                // Update balance display if we have any pretend trades
                if (!pretendTradePointsOnly.isEmpty()) {
                    PerformanceData lastPoint = pretendTradePointsOnly.get(pretendTradePointsOnly.size() - 1);
                    BigDecimal lastPrice = lastPoint.getPretendTrade().getPrice();
                    
//                    BigDecimal finalValue = runningCurrency.add(runningCoin.multiply(lastPrice));
//                    BigDecimal runningProfit = finalValue.subtract(startCurrency);

                    updateBalanceDisplay(runningCurrency, runningCoin, runningProfit);
                }
                
                // Set the trade history content
                tradeHistoryScrollPane.setContent(tradeDataContainer);
                
                // Set fixed height to prevent UI shifting
                tradeHistoryScrollPane.setPrefHeight(150);
                tradeHistoryScrollPane.setMaxHeight(150);
                tradeHistoryScrollPane.setMinHeight(150);
                
            } catch (Exception e) {
                System.err.println("Error updating pretend trade history: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Initialize chart with some default values to ensure it displays properly
     */
    private void initializeChart() {
        // Add some initial data points to ensure the chart displays properly
        Platform.runLater(() -> {
            // Clear existing data in price chart
            tradePriceSeries.getData().clear();
            avgAskPriceSeries.getData().clear();
            avgBidPriceSeries.getData().clear();
            
            // Clear existing data in amount chart
            tradeAmountSeries.getData().clear();
            avgAskAmountSeries.getData().clear();
            avgBidAmountSeries.getData().clear();
            
            // Clear existing categories in amount chart
            amountXAxis.getCategories().clear();
            
            // Don't add any dummy data points - let the real data drive the chart
            
            // Set initial axis ranges for price chart - adjusted for actual data values around 87,000-88,000
            xAxis.setLowerBound(0);
            xAxis.setUpperBound(50); // Set to VISIBLE_DATA_POINTS to match our display limit
            yAxis.setLowerBound(75000);  // Changed to match the current data range
            yAxis.setUpperBound(90000);  // Changed to match the current data range
            
            // Set initial axis ranges for amount chart
            amountYAxis.setLowerBound(0);
            amountYAxis.setUpperBound(1);
            
            // Set bar chart category gap and bar gap
            amountChart.setCategoryGap(10);
            amountChart.setBarGap(2);
            
            // Add initial categories to amount chart to match price chart X-axis
            for (int i = 0; i <= 50; i += 5) { // Use step of 5 for readability
                amountXAxis.getCategories().add(String.valueOf(i));
            }
            
            // Ensure pretend trade series are in the chart
            if (!lineChart.getData().contains(pretendBuySeries)) {
                lineChart.getData().add(pretendBuySeries);
            }
            if (!lineChart.getData().contains(pretendSellSeries)) {
                lineChart.getData().add(pretendSellSeries);
            }
            
            // Style the pretend trade series to have no line
            if (pretendBuySeries.getNode() != null) {
                pretendBuySeries.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
            }
            if (pretendSellSeries.getNode() != null) {
                pretendSellSeries.getNode().setStyle("-fx-stroke: transparent; -fx-stroke-width: 0;");
            }
            
            // Force a layout pass
            lineChart.layout();
            amountChart.layout();
            
            chartInitialized = true;
            
            // Schedule multiple checks to ensure pretend trades remain visible
            scheduleMultipleChecksForPretendTrades();
        });
    }
    
    /**
     * Starts the performance analysis by connecting to the server.
     * This is for Visual Replay mode.
     */
    private void startPerformanceAnalysis() {
        // Reset mode to visual replay
        mode = 1;
        
        // Disconnect existing client to free resources
        if (performanceClient != null) {
            performanceClient.disconnect();
            performanceClient = null;
        }
        
        // Update UI to reflect change to visual replay mode
        statusLabel.setText("Connecting to server...");
        
        // Enable controls for visual replay
        timeSlider.setDisable(false);
        liveButton.setDisable(false);
        
        // Reset charts
        clearChart();
        applySeriesStyles();
        
        // Create a parent for the trade history if it exists, otherwise get existing parent
        VBox tradeHistoryParent = (VBox) tradeHistoryScrollPane.getParent();
        if (tradeHistoryParent == null) {
            System.err.println("Trade history parent is null, cannot update trade history");
            return;
        }
        
        // Get the header box if it exists (should be the first child of the parent)
        HBox headerBox = null;
        if (tradeHistoryParent.getChildren().size() > 0 && 
            tradeHistoryParent.getChildren().get(0) instanceof HBox) {
            headerBox = (HBox) tradeHistoryParent.getChildren().get(0);
        }
        
        // Clear all children from tradeHistoryParent to avoid duplicates
        tradeHistoryParent.getChildren().clear();
        
        // Create a new VBox for trade data
        VBox scrollContent = new VBox(5);
        
        // Add a placeholder
        HBox placeholderRow = new HBox();
        placeholderRow.setPadding(new Insets(10, 5, 5, 5));
        
        Label placeholder = new Label("No trades yet - Trades will appear here when executed");
        placeholder.setStyle("-fx-text-fill: #888888;");
        placeholderRow.getChildren().add(placeholder);
        scrollContent.getChildren().add(placeholderRow);
        
        // Create a new scroll pane
        ScrollPane newTradeHistoryScrollPane = new ScrollPane(scrollContent);
        newTradeHistoryScrollPane.setFitToWidth(true);
        newTradeHistoryScrollPane.setPrefHeight(300); // Increased height for Quick Replay
        
        // Update our reference to the new scroll pane
        tradeHistoryScrollPane = newTradeHistoryScrollPane;
        
        // Recreate and add the header box first if needed
        if (headerBox != null) {
            // Create headers for a fixed position above the scroll pane
            HBox newHeaderBox = new HBox(10);
            newHeaderBox.setPadding(new Insets(5, 5, 5, 5));
            newHeaderBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 0 0 1 0; -fx-background-color: #f0f0f0;");
            
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
            
            Label balUsdHeader = new Label("Bal$$$");
            balUsdHeader.setPrefWidth(100);
            balUsdHeader.setStyle("-fx-font-weight: bold;");
            
            Label balCoinHeader = new Label("BalC");
            balCoinHeader.setPrefWidth(100);
            balCoinHeader.setStyle("-fx-font-weight: bold;");
            
            Label profitHeader = new Label("Profit");
            profitHeader.setPrefWidth(100);
            profitHeader.setStyle("-fx-font-weight: bold;");
            
            newHeaderBox.getChildren().addAll(
                dateHeader, typeHeader, priceHeader, amtHeader,
                seqHeader, balUsdHeader, balCoinHeader, profitHeader
            );
            
            tradeHistoryParent.getChildren().add(newHeaderBox);
        }
        
        // Add new scroll pane to parent
        tradeHistoryParent.getChildren().add(tradeHistoryScrollPane);
        
        // Force garbage collection to free memory
        System.gc();
        
        // Create a new client and start the analysis
        performanceClient = new PerformanceAnalysisClient(this);
        String ups = upsField.getText().isEmpty() ? "0" : upsField.getText();
        String downs = downsField.getText().isEmpty() ? "0" : downsField.getText();
        performanceClient.startPerformanceAnalysis(ups, downs);
    }
    
    // Add a new method to handle the parameterized performance analysis
    /**
     * Starts performance analysis with specific ups/downs configuration.
     * This method is especially useful when transitioning from Quick Replay back to Visual Replay.
     */
    private void startPerformanceAnalysis(String ups, String downs) {
        // Reset mode to visual replay (mode 1)
        mode = 1;
        
        // Disconnect from any existing sessions
        if (performanceClient != null) {
            performanceClient.disconnect();
        }
        
        // Reset the view
        statusLabel.setText("Connecting to server...");
        
        // CRITICAL: Re-enable controls when starting from Quick Replay
        // This ensures the time slider works correctly after Quick Replay
        timeSlider.setDisable(false);
        liveButton.setDisable(false);
        
        // Reset trade history to default size
        tradeHistoryScrollPane.setPrefHeight(180);
        
        // Create a new client and start the analysis
        performanceClient = new PerformanceAnalysisClient(this);
        performanceClient.startPerformanceAnalysis(ups, downs);
        
        // Run garbage collection to free memory after a replay
        System.gc();
    }
    

    /**
     * Updates the status label with a message.
     * 
     * @param message The message to display
     */
    public void updateStatus(String message) {
        updateStatus(message, false);
    }
    
    /**
     * Updates the status label with a message.
     * 
     * @param message The message to display
     * @param isError Whether the message is an error message
     */
    public void updateStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            if (isError) {
                statusLabel.setStyle("-fx-text-fill: red;");
            } else {
                statusLabel.setStyle("-fx-text-fill: black;");
            }
        });
    }
    
    /**
     * Updates the balance display with the latest values.
     * 
     * @param currencyBalance The current currency balance
     * @param coinBalance The current coin balance
     * @param profit The current profit
     */
    public void updateBalanceDisplay(BigDecimal currencyBalance, BigDecimal coinBalance, BigDecimal profit) {
        if (currencyBalance == null || coinBalance == null || profit == null) {
            System.err.println("Cannot update balances with null values");
            return;
        }
        
            Platform.runLater(() -> {
            // If this is the first update, store the starting balances and initialize current balances
            if (startingCurrencyBalance == null && startingCoinBalance == null) {
                // Store the initial values as starting balances
                startingCurrencyBalance = currencyBalance;
                startingCoinBalance = coinBalance;
                
                // Update the labels with the initial values
                balanceLabel.setText(String.format("Starting Balance: $%.2f | %.8f BTC", 
                    startingCurrencyBalance, startingCoinBalance));
            }
            
            // Always update current balance and profit
            currentCurrencyBalance = currencyBalance;
            currentCoinBalance = coinBalance;
            currentProfit = profit;
            
            // Update the current balance label
            currentBalanceLabel.setText(String.format("Current Balance: $%.2f | %.8f BTC", 
                currentCurrencyBalance, currentCoinBalance));
            
            // Set color based on profit (green for positive, red for negative)
            String profitColor = currentProfit.compareTo(BigDecimal.ZERO) >= 0 ? "#00AA00" : "#AA0000";
            profitLabel.setText(String.format("Profit: $%.2f", currentProfit));
            profitLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + profitColor + ";");
        });
    }
    
    /**
     * Called when new data is available from the performance client.
     * This method will update the chart view with the current slider position.
     */
    public void onNewDataAvailable() {
        // Update the chart view with the current slider position if Visual Replay mode
        if (mode == 1) {
            updateChartView(timeSlider.getValue());
        } else if (mode == 2) {
            // In Quick Replay mode, only update trade history from all available data
            // This ensures all trades are displayed even without chart updates
            List<PerformanceData> allData = performanceClient.getDataWindow(0, performanceClient.getDatasetSize());
            if (!allData.isEmpty()) {
                updateTradeHistoryFromWindow(allData);
            }
        }
        
        // Update the status label with the total records received
        int totalRecords = performanceClient.getTotalRecordsReceived();
        int datasetSize = performanceClient.getDatasetSize();
        updateStatus(String.format("Received %,d records (keeping %,d in memory)", totalRecords, datasetSize));
    }
    
    /**
     * Clear the chart data
     */
    private void clearChart() {
        Platform.runLater(() -> {
            // Clear price chart
            tradePriceSeries.getData().clear();
            avgAskPriceSeries.getData().clear();
            avgBidPriceSeries.getData().clear();

            // Clear amount chart
            tradeAmountSeries.getData().clear();
            avgAskAmountSeries.getData().clear();
            avgBidAmountSeries.getData().clear();
            
            // Clear pretend trade data but keep the series
            pretendBuySeries.getData().clear();
            pretendSellSeries.getData().clear();

            // Set a flag to prevent slider updates while clearing
            isUpdatingSlider = true;
            try {
                // Reset slider
                timeSlider.setValue(0.0);  // Start from the beginning
            } finally {
                isUpdatingSlider = false;
            }
        });
    }
    
    /**
     * Apply custom styles to the chart series
     * Optimized for large datasets by only styling visible nodes
     */
    private void applySeriesStyles() {
        // Apply custom colors to the series
            // Apply styles to price chart series
        if (tradePriceSeries.getNode() != null) {
                tradePriceSeries.getNode().setStyle("-fx-stroke: #ff0000; -fx-stroke-width: 2px;");
            }
            
        if (avgAskPriceSeries.getNode() != null) {
                avgAskPriceSeries.getNode().setStyle("-fx-stroke: #00ff00; -fx-stroke-width: 2px;");
            }
            
        if (avgBidPriceSeries.getNode() != null) {
                avgBidPriceSeries.getNode().setStyle("-fx-stroke: #0000ff; -fx-stroke-width: 2px;");
        }
        
        // For large datasets, we'll only style the series lines, not individual points
        // This significantly improves performance with large datasets
        
        // Apply styles to amount chart series - only style the series, not individual bars
                for (XYChart.Data<String, Number> data : tradeAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #ff0000;");
                }
            }
            
                for (XYChart.Data<String, Number> data : avgAskAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #00ff00;");
                }
            }
            
                for (XYChart.Data<String, Number> data : avgBidAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #0000ff;");
                    }
                }
            }
    
    /**
     * Start continuous checks for pretend trade visibility
     * This ensures they remain visible during data loading and scrolling
     */
    private void startContinuousChecksForPretendTrades() {
        // Cancel any existing timer
        if (continuousCheckTimer != null) {
            continuousCheckTimer.cancel();
        }
        
        // Create a new timer for continuous checks
        continuousCheckTimer = new Timer();
        continuousCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    ensurePretendTradesVisible();
                });
            }
        }, 0, 500); // Check every 500ms
    }
    
    /**
     * Stop continuous checks for pretend trade visibility
     */
    private void stopContinuousChecksForPretendTrades() {
        if (continuousCheckTimer != null) {
            continuousCheckTimer.cancel();
            continuousCheckTimer = null;
        }
    }
    
    /**
     * Schedule a check to ensure pretend trades are visible after a delay
     */
    private void schedulePretendTradeVisibilityCheck(int delayMs) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    ensurePretendTradesVisible();
                    disablePretendTradeLines();
                    timer.cancel();
                });
            }
        }, delayMs);
    }
    
    /**
     * Ensure pretend trades are visible in the chart
     */
    @Override
    public int getMode() {
        return mode;
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
     * Updates the trade history from the performance data.
     * Implementation of PerformanceWindowInterface method.
     */
    @Override
    public void updateTradeHistory(List<PerformanceData> windowData) {
        updateTradeHistoryFromWindow(windowData);
    }
}
