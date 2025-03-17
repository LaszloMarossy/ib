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

/**
 * Java FX window client for performance analysis;
 * - connects to the server to analyze performance of a given configuration
 * - displays a chart of trade prices, average ask prices, and average bid prices
 */
public class PerformanceWindow extends Application {

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
    
    // Amount chart components
    private CategoryAxis amountXAxis = new CategoryAxis();
    private NumberAxis amountYAxis = new NumberAxis();
    private BarChart<String, Number> amountChart;
    private XYChart.Series<String, Number> tradeAmountSeries = new XYChart.Series<>();
    private XYChart.Series<String, Number> avgAskAmountSeries = new XYChart.Series<>();
    private XYChart.Series<String, Number> avgBidAmountSeries = new XYChart.Series<>();
    
    private TextField upsField = new TextField();
    private TextField downsField = new TextField();
    private Button examineButton = new Button("Examine Config Performance");
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
    
    private Timer resizeTimer;
    
    // Balance and profit tracking
    private Label balanceLabel = new Label("Starting Balance: $0.00 | 0.00000000 BTC");
    private Label currentBalanceLabel = new Label("Current Balance: $0.00 | 0.00000000 BTC");
    private Label profitLabel = new Label("Profit: $0.00");
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
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        // Print a message to confirm we're running the updated version
        // System.out.println("*******************************************");
        // System.out.println("* PerformanceWindow - Created: 2025-03-12 *");
        // System.out.println("*******************************************");
        
        primaryStage.setTitle("Performance Analysis");
        
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
        
        // Make the symbols larger and more visible
        lineChart.setStyle(".chart-series-line { -fx-stroke-width: 2px; } " +
                          ".chart-symbol { -fx-background-radius: 5px; -fx-padding: 5px; }");
        
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
        lineChart.getData().addAll(tradePriceSeries, avgAskPriceSeries, avgBidPriceSeries);
        amountChart.getData().addAll(tradeAmountSeries, avgAskAmountSeries, avgBidAmountSeries);
        
        // Apply custom colors to the series
        applySeriesStyles();
        
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
        examineButton.setPrefSize(250, 30);
        examineButton.setFont(new Font("Arial", 14));
        examineButton.setOnAction(event -> startPerformanceAnalysis());
        
        // Set up status label
        statusLabel.setFont(new Font("Arial", 14));
        
        // Style the data summary label
        dataSummaryLabel.setFont(new Font("Arial", 14));
        dataSummaryLabel.setStyle("-fx-font-weight: bold;");
        
        // Create input layout
        HBox inputBox = new HBox(10, upsLabel, upsField, downsLabel, downsField, examineButton);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.setPadding(new Insets(10));
        
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
        balanceLabel.setFont(new Font("Arial", 16));
        balanceLabel.setStyle("-fx-font-weight: bold;");
        currentBalanceLabel.setFont(new Font("Arial", 16));
        currentBalanceLabel.setStyle("-fx-font-weight: bold;");
        profitLabel.setFont(new Font("Arial", 16));
        profitLabel.setStyle("-fx-font-weight: bold;");
        
        // Style the data summary label
        dataSummaryLabel.setFont(new Font("Arial", 14));
        dataSummaryLabel.setStyle("-fx-font-weight: bold;");
        
        // Create a HBox for the balance and profit labels
        HBox balanceBox = new HBox(20, balanceLabel, currentBalanceLabel, profitLabel);
        balanceBox.setAlignment(Pos.CENTER);
        balanceBox.setPadding(new Insets(5, 0, 5, 0));
        
        // Create a separate HBox for the data summary
        HBox dataSummaryBox = new HBox(dataSummaryLabel);
        dataSummaryBox.setAlignment(Pos.CENTER);
        dataSummaryBox.setPadding(new Insets(0, 0, 5, 0));
        
        // Set up trade history components
        tradeHistoryContainer.setPadding(new Insets(10));
        tradeHistoryContainer.setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ddd; -fx-border-width: 1px;");
        tradeHistoryContainer.setMaxWidth(600); // Limit width to half of typical screen width
        
        // Add a header to the trade history
        Label tradeHistoryHeader = new Label("Trade History (newest first)");
        tradeHistoryHeader.setFont(new Font("Arial", 14));
        tradeHistoryHeader.setStyle("-fx-font-weight: bold;");
        tradeHistoryContainer.getChildren().add(tradeHistoryHeader);
        
        // Add placeholder entries to ensure minimum height
        Label placeholder1 = new Label("No trades yet");
        placeholder1.setFont(new Font("Arial", 12));
        placeholder1.setStyle("-fx-text-fill: #888888; -fx-padding: 3px 0px;");
        
        Label placeholder2 = new Label("Trades will appear here when executed");
        placeholder2.setFont(new Font("Arial", 12));
        placeholder2.setStyle("-fx-text-fill: #888888; -fx-padding: 3px 0px;");
        
        tradeHistoryContainer.getChildren().addAll(placeholder1, placeholder2);
        
        tradeHistoryScrollPane = new ScrollPane(tradeHistoryContainer);
        tradeHistoryScrollPane.setFitToWidth(true);
        tradeHistoryScrollPane.setPrefHeight(100); // Reduced height for initial display
        tradeHistoryScrollPane.setVisible(true); // Make visible by default
        tradeHistoryScrollPane.setManaged(true); // Make managed by default
        tradeHistoryVisible = true; // Set to visible by default
        
        // Remove toggle button functionality since we always want to show trade history
        toggleTradeHistoryButton.setVisible(false);
        toggleTradeHistoryButton.setManaged(false);
        
        // Create a VBox for the trade history components
        VBox tradeHistoryBox = new VBox(5, tradeHistoryScrollPane);
        tradeHistoryBox.setAlignment(Pos.CENTER);
        
        // Update the root VBox to include the new dataSummaryBox
        VBox root = new VBox(10, inputBox, statusLabel, balanceBox, dataSummaryBox, tradeHistoryBox, chartsBox, sliderBox);
        root.setPadding(new Insets(10));
        VBox.setVgrow(chartsBox, Priority.ALWAYS);  // Allow charts box to grow vertically
        
        // Create scene
        Scene scene = new Scene(root, 1280, 800);  // Set initial window size
        scene.getStylesheets().add(getClass().getResource("/chart.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Add width listener to handle window resizing efficiently
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> {
            // Use a timer to debounce resize events
            if (resizeTimer != null) {
                resizeTimer.cancel();
            }
            resizeTimer = new Timer();
            resizeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                        // Update chart layout after resize
                        lineChart.layout();
                        amountChart.layout();
                        
                        // If in live mode, update the view
                        if (liveMode.get()) {
                            updateChartView(1.0);
                        }
                }
            }, 200); // 200ms delay
        });
        
        // Add height listener to handle window resizing efficiently
        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            // Use a timer to debounce resize events
            if (resizeTimer != null) {
                resizeTimer.cancel();
            }
            resizeTimer = new Timer();
            resizeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        // Update chart layout after resize
                        lineChart.layout();
                        amountChart.layout();
                        
                        // If in live mode, update the view
                        if (liveMode.get()) {
                            updateChartView(1.0);
                        }
                    });
                }
            }, 200); // 200ms delay
        });
        
        // Initialize WebSocket client
        performanceClient = new PerformanceAnalysisClient(this);
        
        // Initialize chart with some default values to ensure it displays properly
        initializeChart();
        
        // Print debug info about the chart
        // System.out.println("Chart initialized with dimensions: " + lineChart.getWidth() + "x" + lineChart.getHeight());
        // System.out.println("Y-axis range: " + yAxis.getLowerBound() + " to " + yAxis.getUpperBound());
    }
    
    /**
     * Updates the chart view based on the slider position.
     * 
     * @param sliderValue The value of the slider (0.0 to 1.0)
     */
    private void updateChartView(double sliderValue) {
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
        
        // Update the chart with the window data
        updateChartWithWindowData(windowData, startIndex);
    }
    
    /**
     * Updates the chart with a window of data from the client.
     * Optimized for memory efficiency with large datasets.
     * 
     * @param windowData The window of data to display
     * @param startIndex The start index of the window in the complete dataset
     */
    private void updateChartWithWindowData(List<PerformanceData> windowData, int startIndex) {
        Platform.runLater(() -> {
            try {
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
                
                // Remove any pretend trade series from the price chart
                // Keep only the main series (trade price, ask price, bid price)
                while (lineChart.getData().size() > 3) {
                    lineChart.getData().remove(3);
                }
            
            // Find min and max Y values in the visible range to auto-scale Y-axis
            double minY = Double.MAX_VALUE;
            double maxY = Double.MIN_VALUE;
                double maxAmount = 0.0;
                
                // Batch data points for more efficient rendering
                List<XYChart.Data<Number, Number>> tradePriceData = new ArrayList<>(windowData.size());
                List<XYChart.Data<Number, Number>> avgAskPriceData = new ArrayList<>(windowData.size());
                List<XYChart.Data<Number, Number>> avgBidPriceData = new ArrayList<>(windowData.size());
                
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
                        
                        // Create a new series for this single trade point
                        XYChart.Series<Number, Number> tradeSeries = new XYChart.Series<>();
                        tradeSeries.setName(trade.getMakerSide()); // Set name to identify the trade type
                        XYChart.Data<Number, Number> tradePoint = new XYChart.Data<>(seq, pretendTradePrice);
                        tradeSeries.getData().add(tradePoint);
                        
                        // Only add the series if it's not already in the chart
                        boolean seriesExists = false;
                        for (XYChart.Series<Number, Number> existingSeries : lineChart.getData()) {
                            if (existingSeries.getData().size() == 1 && 
                                existingSeries.getData().get(0).getXValue().equals(seq) &&
                                existingSeries.getData().get(0).getYValue().equals(pretendTradePrice)) {
                                seriesExists = true;
                                break;
                            }
                        }
                        
                        if (!seriesExists) {
                            lineChart.getData().add(tradeSeries);
                            
                            // Style the trade point based on trade type
                            String tradeType = trade.getMakerSide();
                            String tradeColor = tradeType.toLowerCase().contains("buy") ? "#00AA00" : "#AA0000";
                            
                            // Add styling immediately instead of in a separate runLater
                            if (tradePoint.getNode() != null) {
                                tradePoint.getNode().setStyle("-fx-background-color: " + tradeColor + ", white; -fx-background-radius: 8px; -fx-padding: 8px;");
                                
                                // Add tooltip to the trade point
                                StringBuilder tooltipText = new StringBuilder();
                                tooltipText.append("Trade Type: ").append(tradeType.toUpperCase()).append("\n");
                                tooltipText.append("Price: ").append(String.format("%.2f", trade.getPrice())).append("\n");
                                tooltipText.append("Amount: ").append(String.format("%.4f", trade.getAmount())).append("\n");
                                tooltipText.append("Time: ").append(dateFormatter.format(new Date(point.getTimestamp()))).append("\n");
                                
                                Tooltip tooltip = new Tooltip(tooltipText.toString());
                                Tooltip.install(tradePoint.getNode(), tooltip);
                            } else {
                                // If node is not available yet, use a separate runLater with a delay
                                final XYChart.Data<Number, Number> finalTradePoint = tradePoint;
                                final XYChart.Series<Number, Number> finalTradeSeries = tradeSeries;
                                final String finalTradeColor = tradeColor;
                                final String finalTradeType = tradeType;
                                final long finalTimestamp = point.getTimestamp();
                                
                                // Use a separate runLater for styling with a small delay to ensure node is created
                                Platform.runLater(() -> {
                                    try {
                                        Thread.sleep(50); // Small delay to ensure node is created
                                    } catch (InterruptedException e) {
                                        // Ignore
                                    }
                                    
                                    if (finalTradePoint.getNode() != null) {
                                        finalTradePoint.getNode().setStyle("-fx-background-color: " + finalTradeColor + ", white; -fx-background-radius: 8px; -fx-padding: 8px;");
                                        
                                        // Add tooltip to the trade point
                                        StringBuilder tooltipText = new StringBuilder();
                                        tooltipText.append("Trade Type: ").append(finalTradeType.toUpperCase()).append("\n");
                                        tooltipText.append("Price: ").append(String.format("%.2f", trade.getPrice())).append("\n");
                                        tooltipText.append("Amount: ").append(String.format("%.4f", trade.getAmount())).append("\n");
                                        tooltipText.append("Time: ").append(dateFormatter.format(new Date(finalTimestamp))).append("\n");
                                        
                                        Tooltip tooltip = new Tooltip(tooltipText.toString());
                                        Tooltip.install(finalTradePoint.getNode(), tooltip);
                                    }
                                    
                                    // Style the series to have no line
                                    if (finalTradeSeries.getNode() != null) {
                                        finalTradeSeries.getNode().setStyle("-fx-stroke: transparent;");
                                    }
                                });
                            }
                            
                            // Style the series to have no line
                            if (tradeSeries.getNode() != null) {
                                tradeSeries.getNode().setStyle("-fx-stroke: transparent;");
                            }
                        }
                    }
                    
                    // Add data to the amount chart (using the same sequence numbers as the price chart)
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
                
                // Update X-axis range
                int minSeq = windowData.get(0).getSequence();
                int maxSeq = windowData.get(windowData.size() - 1).getSequence();
                xAxis.setLowerBound(minSeq);
                xAxis.setUpperBound(maxSeq);
                xAxis.setTickUnit(Math.max(1, (maxSeq - minSeq) / 10)); // Adjust tick unit for readability
                
                // Apply styles to the data points - do this in a separate runLater to avoid blocking the UI
                Platform.runLater(this::applySeriesStyles);
            
            // Force a layout pass to ensure both charts are updated
                lineChart.layout();
                amountChart.layout();
                
                // Update the trade history with all pretend trades in the window
                updateTradeHistoryFromWindow(windowData);
            } catch (Exception e) {
                System.err.println("Error updating chart with window data: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Updates the trade history with all pretend trades in the window.
     * 
     * @param windowData The window of data to display
     */
    private void updateTradeHistoryFromWindow(List<PerformanceData> windowData) {
        // Clear existing trade history
        Platform.runLater(() -> {
            // Keep only the header
            while (tradeHistoryContainer.getChildren().size() > 1) {
                tradeHistoryContainer.getChildren().remove(1);
            }
            
            // Add placeholder entries if no trades
            boolean hasAnyTrades = false;
            
            // Process each data point in the window to find pretend trades
            for (PerformanceData point : windowData) {
                if (point.getPretendTrade() != null && point.getPretendTrade().getPrice() != null) {
                    hasAnyTrades = true;
                    com.ibbe.entity.Trade trade = point.getPretendTrade();
                    
                    String tradeType = trade.getMakerSide();
                    BigDecimal price = trade.getPrice();
                    BigDecimal amount = trade.getAmount();
                    long timestamp = point.getTimestamp(); // Use data point timestamp
                    
                    // Format the trade information with timestamp
                    String tradeInfo = String.format("[%s] %s at $%.2f for %.8f BTC (Seq: %d)", 
                        tradeHistoryDateFormat.format(new Date(timestamp)),
                        tradeType.toUpperCase(), price, amount, point.getSequence());
                    
                    // Create a label with the trade information
                    Label tradeLabel = new Label(tradeInfo);
                    tradeLabel.setFont(new Font("Arial", 12));
                    tradeLabel.setMaxWidth(580); // Ensure it fits within the container
                    
                    // Set color based on trade type (green for buy, red for sell)
                    String tradeColor = tradeType.toLowerCase().contains("buy") ? "#00AA00" : "#AA0000";
                    tradeLabel.setStyle("-fx-text-fill: " + tradeColor + "; -fx-padding: 3px 0px; -fx-font-weight: bold;");
                    
                    // Add the label to the trade history container after the header
                    tradeHistoryContainer.getChildren().add(1, tradeLabel); // Add after header for newest first
                }
            }
            
            // Add placeholder entries if no trades
            if (!hasAnyTrades) {
                Label placeholder1 = new Label("No trades yet");
                placeholder1.setFont(new Font("Arial", 12));
                placeholder1.setStyle("-fx-text-fill: #888888; -fx-padding: 3px 0px;");
                
                Label placeholder2 = new Label("Trades will appear here when executed");
                placeholder2.setFont(new Font("Arial", 12));
                placeholder2.setStyle("-fx-text-fill: #888888; -fx-padding: 3px 0px;");
                
                tradeHistoryContainer.getChildren().addAll(placeholder1, placeholder2);
            }
            
            // Limit the number of trade history entries to prevent memory leaks
            while (tradeHistoryContainer.getChildren().size() > MAX_TRADE_HISTORY + 1) { // +1 for the header
                tradeHistoryContainer.getChildren().remove(tradeHistoryContainer.getChildren().size() - 1);
            }
            
            // Ensure the trade history is visible
            if (!tradeHistoryVisible) {
                tradeHistoryVisible = true;
                tradeHistoryScrollPane.setVisible(true);
                tradeHistoryScrollPane.setManaged(true);
                toggleTradeHistoryButton.setText("Hide Trade History");
            }
            
            // Scroll to the top to show the newest trade
            tradeHistoryScrollPane.setVvalue(0);
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
            
            chartInitialized = true;
            
            // System.out.println("Charts initialized with empty series and Y-axis range: " + yAxis.getLowerBound() + " to " + yAxis.getUpperBound());
        });
    }
    
    /**
     * Starts the performance analysis by connecting to the server.
     */
    private void startPerformanceAnalysis() {
        // Clear previous data
        dataPoints.clear();
        sequenceNumber.set(0);  // Reset sequence number to start from 0
        
        Platform.runLater(() -> {
            // Clear price chart
            tradePriceSeries.getData().clear();
            avgAskPriceSeries.getData().clear();
            avgBidPriceSeries.getData().clear();
            
            // Clear amount chart
            tradeAmountSeries.getData().clear();
            avgAskAmountSeries.getData().clear();
            avgBidAmountSeries.getData().clear();
            
            // Remove any pretend trade series from the price chart
            // Keep only the main series (trade price, ask price, bid price)
            while (lineChart.getData().size() > 3) {
                lineChart.getData().remove(3);
            }
            
            // Reset slider
            timeSlider.setValue(0.0);  // Start from the beginning
            
            // Set to historical mode
            liveMode.set(false);  // Start in historical mode to see data from the beginning
            liveButton.setText("Go Live");
            
            // Reset balance and profit display - we'll get values from server
            startingCurrencyBalance = null;
            startingCoinBalance = null;
            currentCurrencyBalance = BigDecimal.ZERO;
            currentCoinBalance = BigDecimal.ZERO;
            currentProfit = BigDecimal.ZERO;
            
            // Update the labels with initial values
            balanceLabel.setText("Starting Balance: Waiting for server data...");
            currentBalanceLabel.setText("Current Balance: Waiting for server data...");
            profitLabel.setText("Profit: $0.00");
            profitLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #000000;");
            
            // Reset trade history
            tradeHistoryContainer.getChildren().clear();
            Label tradeHistoryHeader = new Label("Trade History (newest first)");
            tradeHistoryHeader.setFont(new Font("Arial", 14));
            tradeHistoryHeader.setStyle("-fx-font-weight: bold;");
            tradeHistoryContainer.getChildren().add(tradeHistoryHeader);
            
            // Add placeholder entries to ensure minimum height
            Label placeholder1 = new Label("No trades yet");
            placeholder1.setFont(new Font("Arial", 12));
            placeholder1.setStyle("-fx-text-fill: #888888; -fx-padding: 3px 0px;");
            
            Label placeholder2 = new Label("Trades will appear here when executed");
            placeholder2.setFont(new Font("Arial", 12));
            placeholder2.setStyle("-fx-text-fill: #888888; -fx-padding: 3px 0px;");
            
            tradeHistoryContainer.getChildren().addAll(placeholder1, placeholder2);
        });
        
        // Reset chart initialization flag
        chartInitialized = false;
        
        // Initialize chart with default values
        initializeChart();
        
        // Update status
        statusLabel.setText("Status: Connecting...");
        
        // Get configuration values
        String ups = upsField.getText();
        String downs = downsField.getText();
        
        // Connect to server and start analysis
        performanceClient.startPerformanceAnalysis(ups, downs);
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
     * Updates the status label with a message.
     * 
     * @param message The message to display
     */
    public void updateStatus(String message) {
        updateStatus(message, false);
    }
    
    /**
     * Updates the balance and profit display with the provided values.
     * 
     * @param currencyBalance The current currency balance
     * @param coinBalance The current coin balance
     * @param profit The current profit
     */
    private void updateBalanceAndProfit(BigDecimal currencyBalance, BigDecimal coinBalance, BigDecimal profit) {
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
        // Update the chart view with the current slider position
        updateChartView(timeSlider.getValue());
        
        // Update the status label with the total records received
        int totalRecords = performanceClient.getTotalRecordsReceived();
        int datasetSize = performanceClient.getDatasetSize();
        updateStatus(String.format("Received %,d records (keeping %,d in memory)", totalRecords, datasetSize));
    }
    
    /**
     * Updates the balance display after a trade is executed.
     * This ensures the current balance is properly updated based on the trade.
     * 
     * @param trade The trade that was executed
     */
    private void updateBalanceDisplayAfterTrade(com.ibbe.entity.Trade trade) {
        if (trade == null || trade.getPrice() == null || trade.getAmount() == null) {
            System.err.println("Cannot update balance display with null trade data");
            return;
        }
        
        // Calculate the trade value
        BigDecimal tradeValue = trade.getPrice().multiply(trade.getAmount());
        
        // Update balances based on trade type
        if (trade.getMakerSide().toLowerCase().contains("buy")) {
            // For a buy trade, decrease currency balance and increase coin balance
            currentCurrencyBalance = currentCurrencyBalance.subtract(tradeValue);
            currentCoinBalance = currentCoinBalance.add(trade.getAmount());
        } else if (trade.getMakerSide().toLowerCase().contains("sell")) {
            // For a sell trade, increase currency balance and decrease coin balance
            currentCurrencyBalance = currentCurrencyBalance.add(tradeValue);
            currentCoinBalance = currentCoinBalance.subtract(trade.getAmount());
        }
        
        // Calculate profit
        BigDecimal currentAccountValue = currentCoinBalance.multiply(trade.getPrice()).add(currentCurrencyBalance);
        BigDecimal startingAccountValue = startingCoinBalance.multiply(trade.getPrice()).add(startingCurrencyBalance);
        currentProfit = currentAccountValue.subtract(startingAccountValue);
        
        // Update the labels
        Platform.runLater(() -> {
            currentBalanceLabel.setText(String.format("Current Balance: $%.2f | %.8f BTC", 
                currentCurrencyBalance, currentCoinBalance));
            
            // Set color based on profit (green for positive, red for negative)
            String profitColor = currentProfit.compareTo(BigDecimal.ZERO) >= 0 ? "#00AA00" : "#AA0000";
            profitLabel.setText(String.format("Profit: $%.2f", currentProfit));
            profitLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + profitColor + ";");
        });
    }
    
    /**
     * Adds a data point to the chart (overloaded method for backward compatibility).
     */
    public void addDataPoint(
            BigDecimal tradePrice, 
            BigDecimal tradeAmount, 
            BigDecimal avgAskPrice, 
            BigDecimal avgAskAmount, 
            BigDecimal avgBidPrice, 
            BigDecimal avgBidAmount,
            long timestamp,
            boolean amountMissing) {
        
        // Call the full version with null values for the new parameters
        addDataPoint(tradePrice, tradeAmount, avgAskPrice, avgAskAmount, avgBidPrice, avgBidAmount,
                    timestamp, amountMissing, null, null, null, null);
    }
    
    /**
     * Adds a data point to the chart.
     */
    public void addDataPoint(
            BigDecimal tradePrice, 
            BigDecimal tradeAmount, 
            BigDecimal avgAskPrice, 
            BigDecimal avgAskAmount, 
            BigDecimal avgBidPrice, 
            BigDecimal avgBidAmount,
            long timestamp,
            boolean amountMissing,
            com.ibbe.entity.Trade pretendTrade,
            BigDecimal currencyBalance,
            BigDecimal coinBalance,
            BigDecimal profit) {
        
        // Skip data points with null trade price or amount
        if (tradePrice == null || tradeAmount == null) {
            System.err.println("Skipping data point with null trade price or amount");
            return;
        }
        
        // Create a new data point
        int seq = sequenceNumber.getAndIncrement();
        PerformanceData dataPoint = new PerformanceData();
        dataPoint.setSequence(seq);
        dataPoint.setTradePrice(tradePrice);
        dataPoint.setTradeAmount(tradeAmount);
        dataPoint.setAvgAskPrice(avgAskPrice);
        dataPoint.setAvgAskAmount(avgAskAmount);
        dataPoint.setAvgBidPrice(avgBidPrice);
        dataPoint.setAvgBidAmount(avgBidAmount);
        dataPoint.setTimestamp(timestamp);
        
        // Add pretend trade if present
        if (pretendTrade != null) {
            dataPoint.setPretendTrade(pretendTrade);
            // Update balance display based on the trade
            updateBalanceDisplayAfterTrade(pretendTrade);
        }
        
        // Update balance and profit display if provided
        // Only update starting balance on the first data point
        if (currencyBalance != null && coinBalance != null && profit != null) {
            if (dataPoints.isEmpty()) {
                // First data point - set starting balances
                updateBalanceAndProfit(currencyBalance, coinBalance, profit);
            } else if (pretendTrade != null) {
                // Only update current balance and profit when trades occur
                // Already handled by updateBalanceDisplayAfterTrade above
            } else {
                // Regular update without changing starting balance
                currentCurrencyBalance = currencyBalance;
                currentCoinBalance = coinBalance;
                currentProfit = profit;
                
                Platform.runLater(() -> {
                    // Update the current balance label
                    currentBalanceLabel.setText(String.format("Current Balance: $%.2f | %.8f BTC", 
                        currentCurrencyBalance, currentCoinBalance));
                    
                    // Set color based on profit
                    String profitColor = currentProfit.compareTo(BigDecimal.ZERO) >= 0 ? "#00AA00" : "#AA0000";
                    profitLabel.setText(String.format("Profit: $%.2f", currentProfit));
                    profitLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + profitColor + ";");
                });
            }
        }

        // Add to the data points list
        boolean updateCharts = false;
        boolean firstBatch = false;
        boolean needTrimSeries = false;
        synchronized (dataPoints) {
            dataPoints.add(dataPoint);
            
            // Limit the number of data points to avoid memory issues
            needTrimSeries = dataPoints.size() > MAX_DATA_POINTS;
            while (dataPoints.size() > MAX_DATA_POINTS) {
                dataPoints.removeFirst();
            }
            
            // Update the charts if we're in live mode or this is the first data point
            updateCharts = liveMode.get() || dataPoints.size() == 1;
            
            // Check if we've received a batch of data points (e.g., 50 points)
            // This helps us detect when we've loaded a significant amount of historical data
            firstBatch = dataPoints.size() == 50;
        }
        
        // Always add the data point to the series (even if not updating the view)
        // This ensures all data is in the series when we need to display it
        final boolean shouldUpdateCharts = updateCharts;
        final boolean isFirstBatch = firstBatch;
        final boolean shouldTrimSeries = needTrimSeries;
        
        // Batch UI updates to reduce memory pressure
        Platform.runLater(() -> {
            if (!chartInitialized) {
                initializeChart();
            }
            
            try {
                // Trim series data if we've exceeded the maximum number of data points
                if (shouldTrimSeries) {
                    // Keep only the most recent MAX_DATA_POINTS in each series
                    while (tradePriceSeries.getData().size() > MAX_DATA_POINTS) {
                        tradePriceSeries.getData().remove(0);
                    }
                    while (avgAskPriceSeries.getData().size() > MAX_DATA_POINTS) {
                        avgAskPriceSeries.getData().remove(0);
                    }
                    while (avgBidPriceSeries.getData().size() > MAX_DATA_POINTS) {
                        avgBidPriceSeries.getData().remove(0);
                    }
                    
                    // Also trim any additional series (pretend trades)
                    while (lineChart.getData().size() > 3) {
                        if (lineChart.getData().get(3).getData().size() > MAX_DATA_POINTS) {
                            lineChart.getData().remove(3);
                        } else {
                            break;
                        }
                    }
                }
                
                // Add data to the price series
                XYChart.Data<Number, Number> tradeData = new XYChart.Data<>(seq, dataPoint.getTradePrice());
                XYChart.Data<Number, Number> askData = new XYChart.Data<>(seq, dataPoint.getAvgAskPrice());
                XYChart.Data<Number, Number> bidData = new XYChart.Data<>(seq, dataPoint.getAvgBidPrice());
                
                tradePriceSeries.getData().add(tradeData);
                avgAskPriceSeries.getData().add(askData);
                avgBidPriceSeries.getData().add(bidData);
                
                // Apply styles to the new data points - only if they're visible
                // This reduces the number of DOM nodes created
                boolean isVisible = liveMode.get() || 
                    (seq >= xAxis.getLowerBound() && seq <= xAxis.getUpperBound());
                
                if (isVisible) {
                // Apply styles to the new data points
                if (tradeData.getNode() != null) {
                    tradeData.getNode().setStyle("-fx-background-color: #ff0000, white; -fx-background-radius: 5px; -fx-padding: 5px;");
                }
                
                if (askData.getNode() != null) {
                    askData.getNode().setStyle("-fx-background-color: #00ff00, white; -fx-background-radius: 5px; -fx-padding: 5px;");
                }
                
                if (bidData.getNode() != null) {
                    bidData.getNode().setStyle("-fx-background-color: #0000ff, white; -fx-background-radius: 5px; -fx-padding: 5px;");
                }
                
                    // Add tooltips to the data points - only for visible points
                    // and only every 5th point to reduce memory usage
                    if (seq % 5 == 0) {
                addTooltipToLastDataPoint(tradePriceSeries, dataPoint);
                addTooltipToLastDataPoint(avgAskPriceSeries, dataPoint);
                addTooltipToLastDataPoint(avgBidPriceSeries, dataPoint);
                    }
                }
                
                // If we've received a batch of data or we're updating the charts, update the view
                if (shouldUpdateCharts || isFirstBatch) {
                    // If we're in live mode, show the most recent data
                    // Otherwise, show the beginning of the data (for historical view)
                    double sliderValue = liveMode.get() ? 1.0 : 0.0;
                    updateChartView(sliderValue);
                }
            } catch (Exception e) {
                System.err.println("Error adding data point to chart: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Adds a tooltip to the last data point in a series.
     */
    private void addTooltipToLastDataPoint(XYChart.Series<Number, Number> series, PerformanceData dataPoint) {
        if (series.getData().isEmpty()) {
            return;
        }
        
        XYChart.Data<Number, Number> lastData = series.getData().get(series.getData().size() - 1);
        Node node = lastData.getNode();
        
        if (node != null) {
            // Format the tooltip text
            StringBuilder tooltipText = new StringBuilder();
            tooltipText.append("Sequence: ").append(dataPoint.getSequence()).append("\n");
            tooltipText.append("Time: ").append(dateFormatter.format(new Date(dataPoint.getTimestamp()))).append("\n");
            
            if (series == tradePriceSeries) {
                tooltipText.append("Trade Price: ").append(String.format("%.2f", dataPoint.getTradePrice())).append("\n");
                tooltipText.append("Trade Amount: ").append(String.format("%.4f", dataPoint.getTradeAmount()));
            } else if (series == avgAskPriceSeries) {
                tooltipText.append("Avg Ask Price: ").append(String.format("%.2f", dataPoint.getAvgAskPrice())).append("\n");
                tooltipText.append("Avg Ask Amount: ").append(String.format("%.4f", dataPoint.getAvgAskAmount()));
            } else if (series == avgBidPriceSeries) {
                tooltipText.append("Avg Bid Price: ").append(String.format("%.2f", dataPoint.getAvgBidPrice())).append("\n");
                tooltipText.append("Avg Bid Amount: ").append(String.format("%.4f", dataPoint.getAvgBidAmount()));
            }
            
            // Create and install the tooltip
            Tooltip tooltip = new Tooltip(tooltipText.toString());
            Tooltip.install(node, tooltip);
        }
    }
    
    /**
     * Updates the amount chart to only show data points within the visible range.
     * 
     * @param minSeq The minimum sequence number to display
     * @param maxSeq The maximum sequence number to display
     */
    private void updateVisibleAmountData(int minSeq, int maxSeq) {
        try {
            // Clear existing categories
            amountXAxis.getCategories().clear();
            
            // Create a list of sequence numbers that should be displayed
            List<String> visibleSequences = new ArrayList<>();
            
            // Add categories for the visible range - use the same sequence numbers as the price chart
            // This ensures both charts show the same data points
            // Only add every other sequence number to reduce memory usage
            for (int i = minSeq; i <= maxSeq; i += 2) {
                visibleSequences.add(String.valueOf(i));
            }
            
            // Set the categories directly to ensure they match the price chart's X-axis
            amountXAxis.getCategories().addAll(visibleSequences);
            
            // We need to completely rebuild the amount series data to ensure it matches the price chart
            // First, clear all existing data
            tradeAmountSeries.getData().clear();
            avgAskAmountSeries.getData().clear();
            avgBidAmountSeries.getData().clear();
            
            // Now add only the data points that match the visible sequence range
            // Use a single runLater for all tooltip installations to reduce overhead
            final List<Runnable> tooltipTasks = new ArrayList<>();
            
            synchronized (dataPoints) {
                for (PerformanceData point : dataPoints) {
                    int seq = point.getSequence();
                    if (seq >= minSeq && seq <= maxSeq && seq % 2 == 0) { // Only add every other point
                        String seqStr = String.valueOf(seq);
                        
                        // Add trade amount data
                        double tradeAmount = point.getTradeAmount();
                        if (tradeAmount > 0) {
                            final XYChart.Data<String, Number> tradeAmountData = new XYChart.Data<>(seqStr, tradeAmount);
                            tradeAmountSeries.getData().add(tradeAmountData);
                            
                            // Add tooltip task
                            final PerformanceData tooltipPoint = point;
                            tooltipTasks.add(() -> {
                                if (tradeAmountData.getNode() != null) {
                                    addTooltipToBarDataPoint(tradeAmountData, tooltipPoint, "Trade");
                                }
                            });
                        }
                        
                        // Add ask amount data
                        double askAmount = point.getAvgAskAmount();
                        if (askAmount > 0) {
                            final XYChart.Data<String, Number> askAmountData = new XYChart.Data<>(seqStr, askAmount);
                            avgAskAmountSeries.getData().add(askAmountData);
                            
                            // Add tooltip task
                            final PerformanceData tooltipPoint = point;
                            tooltipTasks.add(() -> {
                                if (askAmountData.getNode() != null) {
                                    addTooltipToBarDataPoint(askAmountData, tooltipPoint, "Ask");
                                }
                            });
                        }
                        
                        // Add bid amount data
                        double bidAmount = point.getAvgBidAmount();
                        if (bidAmount > 0) {
                            final XYChart.Data<String, Number> bidAmountData = new XYChart.Data<>(seqStr, bidAmount);
                            avgBidAmountSeries.getData().add(bidAmountData);
                            
                            // Add tooltip task
                            final PerformanceData tooltipPoint = point;
                            tooltipTasks.add(() -> {
                                if (bidAmountData.getNode() != null) {
                                    addTooltipToBarDataPoint(bidAmountData, tooltipPoint, "Bid");
                                }
                            });
                        }
                    }
                }
            }
            
            // Apply styles to the new data points and install tooltips in a single runLater
            Platform.runLater(() -> {
                // Apply styles to the new data points
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
                
                // Install tooltips - but only for a subset of points to reduce memory usage
                for (int i = 0; i < tooltipTasks.size(); i += 5) { // Only add tooltips for every 5th point
                    if (i < tooltipTasks.size()) {
                        tooltipTasks.get(i).run();
                    }
                }
            });
            
            // Set appropriate Y-axis range for amount chart
            double maxAmount = 0.0;
            
            for (XYChart.Data<String, Number> data : tradeAmountSeries.getData()) {
                if (data.getYValue() != null && data.getYValue().doubleValue() > maxAmount) {
                    maxAmount = data.getYValue().doubleValue();
                }
            }
            
            for (XYChart.Data<String, Number> data : avgAskAmountSeries.getData()) {
                if (data.getYValue() != null && data.getYValue().doubleValue() > maxAmount) {
                    maxAmount = data.getYValue().doubleValue();
                }
            }
            
            for (XYChart.Data<String, Number> data : avgBidAmountSeries.getData()) {
                if (data.getYValue() != null && data.getYValue().doubleValue() > maxAmount) {
                    maxAmount = data.getYValue().doubleValue();
                }
            }
            
            // Set a reasonable range for the amount chart
            if (maxAmount > 0) {
                amountYAxis.setLowerBound(0);
                amountYAxis.setUpperBound(maxAmount * 1.1); // Add 10% padding
            } else {
                amountYAxis.setLowerBound(0);
                amountYAxis.setUpperBound(1);
            }
        } catch (Exception e) {
            System.err.println("Error updating amount chart: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Adds a tooltip to a bar chart data point.
     */
    private void addTooltipToBarDataPoint(XYChart.Data<String, Number> data, PerformanceData point, String type) {
        if (data.getNode() == null) {
            return;
        }
        
        // Format the tooltip text
        StringBuilder tooltipText = new StringBuilder();
        tooltipText.append("Sequence: ").append(point.getSequence()).append("\n");
        tooltipText.append("Time: ").append(dateFormatter.format(new Date(point.getTimestamp()))).append("\n");
        
        if ("Trade".equals(type)) {
            tooltipText.append("Trade Amount: ").append(String.format("%.4f", point.getTradeAmount())).append("\n");
            tooltipText.append("Trade Price: ").append(String.format("%.2f", point.getTradePrice()));
        } else if ("Ask".equals(type)) {
            tooltipText.append("Avg Ask Amount: ").append(String.format("%.4f", point.getAvgAskAmount())).append("\n");
            tooltipText.append("Avg Ask Price: ").append(String.format("%.2f", point.getAvgAskPrice()));
        } else if ("Bid".equals(type)) {
            tooltipText.append("Avg Bid Amount: ").append(String.format("%.4f", point.getAvgBidAmount())).append("\n");
            tooltipText.append("Avg Bid Price: ").append(String.format("%.2f", point.getAvgBidPrice()));
        }
        
        // Create and install the tooltip
        Tooltip tooltip = new Tooltip(tooltipText.toString());
        Tooltip.install(data.getNode(), tooltip);
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
     * Clean up resources when the application is closed.
     */
    @Override
    public void stop() {
        // Cancel any pending timers
        if (resizeTimer != null) {
            resizeTimer.cancel();
            resizeTimer = null;
        }
        
        // Clear data structures to help with garbage collection
        synchronized (dataPoints) {
            dataPoints.clear();
        }
        
        // Clear chart data
        Platform.runLater(() -> {
            tradePriceSeries.getData().clear();
            avgAskPriceSeries.getData().clear();
            avgBidPriceSeries.getData().clear();
            tradeAmountSeries.getData().clear();
            avgAskAmountSeries.getData().clear();
            avgBidAmountSeries.getData().clear();
            
            // Remove all series from charts
            lineChart.getData().clear();
            amountChart.getData().clear();
            
            // Clear trade history
            tradeHistoryContainer.getChildren().clear();
        });
        
        // Disconnect from server
        if (performanceClient != null) {
            performanceClient.disconnect();
        }
        
        // Suggest garbage collection
        System.gc();
    }

    /**
     * Updates the balance display with the latest values.
     * This method is called from the PerformanceAnalysisClient.
     * 
     * @param currencyBalance The current currency balance
     * @param coinBalance The current coin balance
     * @param profit The current profit
     */
    public void updateBalanceDisplay(BigDecimal currencyBalance, BigDecimal coinBalance, BigDecimal profit) {
        // Call the private method to update the balance and profit
        updateBalanceAndProfit(currencyBalance, coinBalance, profit);
    }
} 