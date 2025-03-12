package com.ibbe.fx;

import com.ibbe.entity.PerformanceData;
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

    // Maximum number of data points to keep in memory
    private static final int MAX_DATA_POINTS = 5000;
    
    // Number of data points to display in the visible window
    private static final int VISIBLE_DATA_POINTS = 50;
    
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
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        // Print a message to confirm we're running the updated version
        System.out.println("*******************************************");
        System.out.println("* PerformanceWindow - Created: 2025-03-12 *");
        System.out.println("*******************************************");
        
        primaryStage.setTitle("Performance Analysis");
        
        // Initialize chart with improved axis configuration
        xAxis.setLabel("Record #");
        yAxis.setLabel("Price/Amount");
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
        
        VBox root = new VBox(10, inputBox, statusLabel, chartsBox, sliderBox);
        root.setPadding(new Insets(10));
        VBox.setVgrow(chartsBox, Priority.ALWAYS);  // Allow charts box to grow vertically
        
        // Create scene
        Scene scene = new Scene(root, 1280, 800);  // Set initial window size
        scene.getStylesheets().add(getClass().getResource("/chart.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Add resize listener to handle window resizing efficiently
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> {
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
        System.out.println("Chart initialized with dimensions: " + lineChart.getWidth() + "x" + lineChart.getHeight());
        System.out.println("Y-axis range: " + yAxis.getLowerBound() + " to " + yAxis.getUpperBound());
    }
    
    /**
     * Updates the chart view based on the slider position.
     * 
     * @param sliderValue The value of the slider (0.0 to 1.0)
     */
    private void updateChartView(double sliderValue) {
        synchronized (dataPoints) {
            if (dataPoints.isEmpty()) {
                System.out.println("No data points available to update chart view");
                return;
            }
            
            // Calculate the visible range based on slider value
            int totalPoints = dataPoints.size();
            int visiblePoints = Math.min(VISIBLE_DATA_POINTS, totalPoints);
            
            // Calculate the start index based on slider value
            int startIndex = (int) Math.round((totalPoints - visiblePoints) * sliderValue);
            startIndex = Math.max(0, Math.min(startIndex, totalPoints - visiblePoints));
            
            // Get the sequence numbers for the visible range
            int minSeq = dataPoints.get(startIndex).getSequence();
            int maxSeq = dataPoints.get(Math.min(startIndex + visiblePoints - 1, totalPoints - 1)).getSequence();
            
            System.out.println("Updating chart view with sequence range: " + minSeq + " to " + maxSeq + 
                              " (slider value: " + sliderValue + ")");
            
            // Update the X-axis range
            xAxis.setLowerBound(minSeq);
            xAxis.setUpperBound(maxSeq);
            xAxis.setTickUnit(Math.max(1, (maxSeq - minSeq) / 10)); // Adjust tick unit for readability
            
            // Find min and max Y values in the visible range to auto-scale Y-axis
            double minY = Double.MAX_VALUE;
            double maxY = Double.MIN_VALUE;
            
            for (int i = startIndex; i < Math.min(startIndex + visiblePoints, totalPoints); i++) {
                PerformanceData point = dataPoints.get(i);
                
                // Get trade price (already a primitive double, no need for null check)
                double tradePrice = point.getTradePrice();
                if (tradePrice > 0) {  // Only consider positive prices
                    minY = Math.min(minY, tradePrice);
                    maxY = Math.max(maxY, tradePrice);
                }
                
                // Get ask price (already a primitive double, no need for null check)
                double askPrice = point.getAvgAskPrice();
                if (askPrice > 0) {  // Only consider positive prices
                    minY = Math.min(minY, askPrice);
                    maxY = Math.max(maxY, askPrice);
                }
                
                // Get bid price (already a primitive double, no need for null check)
                double bidPrice = point.getAvgBidPrice();
                if (bidPrice > 0) {  // Only consider positive prices
                    minY = Math.min(minY, bidPrice);
                    maxY = Math.max(maxY, bidPrice);
                }
            }
            
            // Add some padding to the Y-axis range (5%)
            double padding = (maxY - minY) * 0.05;
            if (padding < 1) padding = 1000; // Default padding if range is too small
            
            // Update Y-axis range if we found valid min/max values
            if (minY != Double.MAX_VALUE && maxY != Double.MIN_VALUE) {
                yAxis.setLowerBound(minY - padding);
                yAxis.setUpperBound(maxY + padding);
                System.out.println("Updated Y-axis range to: " + yAxis.getLowerBound() + " to " + yAxis.getUpperBound() + 
                                  " based on visible data points from index " + startIndex + " to " + 
                                  Math.min(startIndex + visiblePoints - 1, totalPoints - 1));
            } else {
                System.out.println("Could not find valid min/max Y values in visible range");
            }
            
            // Update the amount chart categories - ensure it's synchronized with the price chart
            updateVisibleAmountData(minSeq, maxSeq);
            
            // Force a layout pass to ensure both charts are updated
            Platform.runLater(() -> {
                lineChart.layout();
                amountChart.layout();
            });
            
            System.out.println("Chart view updated with X-axis range: " + xAxis.getLowerBound() + " to " + xAxis.getUpperBound() + 
                              ", visible points: " + visiblePoints + " out of " + totalPoints + " total points");
        }
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
            
            System.out.println("Charts initialized with empty series and Y-axis range: " + yAxis.getLowerBound() + " to " + yAxis.getUpperBound());
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
            
            // Reset slider
            timeSlider.setValue(0.0);  // Start from the beginning
            
            // Set to historical mode
            liveMode.set(false);  // Start in historical mode to see data from the beginning
            liveButton.setText("Go Live");
            
            System.out.println("All series cleared for new analysis");
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
     * Updates the status label.
     */
    public void updateStatus(String status) {
        Platform.runLater(() -> statusLabel.setText("Status: " + status));
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
            boolean amountMissing) {
        
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
        dataPoint.setAmountMissing(amountMissing);
        
        // Add to the data points list
        boolean updateCharts = false;
        boolean firstBatch = false;
        synchronized (dataPoints) {
            dataPoints.add(dataPoint);
            
            // Limit the number of data points to avoid memory issues
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
        Platform.runLater(() -> {
            if (!chartInitialized) {
                initializeChart();
            }
            
            try {
                // Add data to the price series
                XYChart.Data<Number, Number> tradeData = new XYChart.Data<>(seq, dataPoint.getTradePrice());
                XYChart.Data<Number, Number> askData = new XYChart.Data<>(seq, dataPoint.getAvgAskPrice());
                XYChart.Data<Number, Number> bidData = new XYChart.Data<>(seq, dataPoint.getAvgBidPrice());
                
                tradePriceSeries.getData().add(tradeData);
                avgAskPriceSeries.getData().add(askData);
                avgBidPriceSeries.getData().add(bidData);
                
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
                
                // Add tooltips to the data points
                addTooltipToLastDataPoint(tradePriceSeries, dataPoint);
                addTooltipToLastDataPoint(avgAskPriceSeries, dataPoint);
                addTooltipToLastDataPoint(avgBidPriceSeries, dataPoint);
                
                // Log every 100th point to avoid flooding the console
                if (seq % 100 == 0) {
                    System.out.println("Added data point to series - Trade: " + dataPoint.getTradePrice() + 
                                      ", Ask: " + dataPoint.getAvgAskPrice() + 
                                      ", Bid: " + dataPoint.getAvgBidPrice() + 
                                      ", Sequence: " + seq);
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
                if (!dataPoint.isAmountMissing()) {
                    tooltipText.append("Trade Amount: ").append(String.format("%.4f", dataPoint.getTradeAmount()));
                }
            } else if (series == avgAskPriceSeries) {
                tooltipText.append("Avg Ask Price: ").append(String.format("%.2f", dataPoint.getAvgAskPrice())).append("\n");
                if (!dataPoint.isAmountMissing()) {
                    tooltipText.append("Avg Ask Amount: ").append(String.format("%.4f", dataPoint.getAvgAskAmount()));
                }
            } else if (series == avgBidPriceSeries) {
                tooltipText.append("Avg Bid Price: ").append(String.format("%.2f", dataPoint.getAvgBidPrice())).append("\n");
                if (!dataPoint.isAmountMissing()) {
                    tooltipText.append("Avg Bid Amount: ").append(String.format("%.4f", dataPoint.getAvgBidAmount()));
                }
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
            System.out.println("Updating amount chart to match sequence range: " + minSeq + " to " + maxSeq);
            
            // Clear existing categories
            amountXAxis.getCategories().clear();
            
            // Create a list of sequence numbers that should be displayed
            List<String> visibleSequences = new ArrayList<>();
            
            // Add categories for the visible range - use the same sequence numbers as the price chart
            // This ensures both charts show the same data points
            for (int i = minSeq; i <= maxSeq; i++) {
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
            synchronized (dataPoints) {
                for (PerformanceData point : dataPoints) {
                    int seq = point.getSequence();
                    if (seq >= minSeq && seq <= maxSeq && !point.isAmountMissing()) {
                        String seqStr = String.valueOf(seq);
                        
                        // Add trade amount data
                        double tradeAmount = point.getTradeAmount();
                        if (tradeAmount > 0) {
                            final XYChart.Data<String, Number> tradeAmountData = new XYChart.Data<>(seqStr, tradeAmount);
                            tradeAmountSeries.getData().add(tradeAmountData);
                            
                            // Add tooltip in a separate runLater to ensure node is created
                            final PerformanceData tooltipPoint = point;
                            Platform.runLater(() -> {
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
                            
                            // Add tooltip in a separate runLater to ensure node is created
                            final PerformanceData tooltipPoint = point;
                            Platform.runLater(() -> {
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
                            
                            // Add tooltip in a separate runLater to ensure node is created
                            final PerformanceData tooltipPoint = point;
                            Platform.runLater(() -> {
                                if (bidAmountData.getNode() != null) {
                                    addTooltipToBarDataPoint(bidAmountData, tooltipPoint, "Bid");
                                }
                            });
                        }
                    }
                }
            }
            
            // Apply styles to the new data points
            Platform.runLater(() -> {
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
                System.out.println("Updated amount Y-axis range to: 0 to " + amountYAxis.getUpperBound());
            } else {
                amountYAxis.setLowerBound(0);
                amountYAxis.setUpperBound(1);
                System.out.println("Reset amount Y-axis range to default: 0 to 1");
            }
            
            System.out.println("Amount chart updated with " + amountXAxis.getCategories().size() + 
                              " categories, from " + minSeq + " to " + maxSeq +
                              " and " + tradeAmountSeries.getData().size() + " trade amount data points");
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
     */
    private void applySeriesStyles() {
        // Apply custom colors to the series
        Platform.runLater(() -> {
            // Apply styles to price chart series
            if (!tradePriceSeries.getData().isEmpty()) {
                tradePriceSeries.getNode().setStyle("-fx-stroke: #ff0000; -fx-stroke-width: 2px;");
                for (XYChart.Data<Number, Number> data : tradePriceSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-background-color: #ff0000, white; -fx-background-radius: 5px; -fx-padding: 5px;");
                    }
                }
            }
            
            if (!avgAskPriceSeries.getData().isEmpty()) {
                avgAskPriceSeries.getNode().setStyle("-fx-stroke: #00ff00; -fx-stroke-width: 2px;");
                for (XYChart.Data<Number, Number> data : avgAskPriceSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-background-color: #00ff00, white; -fx-background-radius: 5px; -fx-padding: 5px;");
                    }
                }
            }
            
            if (!avgBidPriceSeries.getData().isEmpty()) {
                avgBidPriceSeries.getNode().setStyle("-fx-stroke: #0000ff; -fx-stroke-width: 2px;");
                for (XYChart.Data<Number, Number> data : avgBidPriceSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-background-color: #0000ff, white; -fx-background-radius: 5px; -fx-padding: 5px;");
                    }
                }
            }
            
            // Apply styles to amount chart series
            if (!tradeAmountSeries.getData().isEmpty()) {
                for (XYChart.Data<String, Number> data : tradeAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #ff0000;");
                    }
                }
            }
            
            if (!avgAskAmountSeries.getData().isEmpty()) {
                for (XYChart.Data<String, Number> data : avgAskAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #00ff00;");
                    }
                }
            }
            
            if (!avgBidAmountSeries.getData().isEmpty()) {
                for (XYChart.Data<String, Number> data : avgBidAmountSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-bar-fill: #0000ff;");
                    }
                }
            }
        });
    }
} 