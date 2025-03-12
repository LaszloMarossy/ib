package com.ibbe.fx;

import com.ibbe.entity.FxTradesDisplayData;
import com.ibbe.entity.Order;
import com.ibbe.entity.Trade;
import com.ibbe.entity.TradeConfig;
import com.ibbe.util.PropertiesUtil;
import com.ibbe.websocket.OrderbookWsClient;
import com.ibbe.websocket.TradeDataWsClient;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import javafx.application.Platform;

import java.util.Arrays;

/**
 * Java FX window client for trading configuration;
 * - starts a configurable trader back-end process based on the given window inputs
 * - connects to the local back-end to monitor a configurable trader that it defines
 */
public class TradingWindow extends Application {

    private TableView<FxTradesDisplayData> performanceTable = new TableView<>();
    private TableView<Order> topAsksTable = new TableView<>();
    private TableView<Order> topBidsTable = new TableView<>();
    private TableView<Trade> recentTradesWsTable = new TableView<>();

    private TextField ups = new TextField();
    private TextField downs = new TextField();
    private TradeConfig tradeConfig = null;
    private int previousDisplayDataHashCode = 0;
    private Label appStatusLabel;
    private TradeDataWsClient tradeDataWsClient;
    private OrderbookWsClient orderbookWsClient;
    private Button createConfigButton = new Button("Monitor Configuration");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Print a message to confirm we're running the updated version
        System.out.println("*******************************************");
        System.out.println("* TradingWindow - Updated: 2025-03-03 20:45 *");
        System.out.println("*******************************************");
        
        // get ws clients to start
        tradeDataWsClient = new TradeDataWsClient(this);
        orderbookWsClient = new OrderbookWsClient(this);

        ups.setText(PropertiesUtil.getProperty("trade.up_m"));
        downs.setText(PropertiesUtil.getProperty("trade.down_n"));

        HBox configBox = new HBox();
        configBox.getChildren().addAll(ups, downs);
        configBox.setSpacing(10);

        appStatusLabel = new Label("App Status: Not yet monitoring... ");
        appStatusLabel.setFont(new Font("Arial", 30));

        createConfigButton.setPrefSize(500, 37);
        createConfigButton.setFont(new Font("Arial", 25));
        // create the monitoring object on the back-end that will work with the specified configuration
        createConfigButton.setOnAction(event -> createTradingConfigurationBackendProcesses(appStatusLabel));

        performanceTable.setEditable(true);
        performanceTable.setFixedCellSize(35);
        performanceTable.prefHeightProperty().bind(Bindings.size(performanceTable.getItems()).multiply(performanceTable.getFixedCellSize()).add(100));
        TableColumn<FxTradesDisplayData, String> currencyBalance = new TableColumn<>("CURR BAL");
        TableColumn<FxTradesDisplayData, String> coinBalance = new TableColumn<>("COIN BAL");
        TableColumn<FxTradesDisplayData, String> latestPrice = new TableColumn<>("LATEST PRICE");
        TableColumn<FxTradesDisplayData, String> startValue = new TableColumn<>("START VAL");
        TableColumn<FxTradesDisplayData, String> accountValue = new TableColumn<>("ACCT VAL");
        TableColumn<FxTradesDisplayData, String> profit = new TableColumn<>("PROFIT");
        currencyBalance.setCellValueFactory(new PropertyValueFactory<FxTradesDisplayData, String>("currencyBalance"));
        coinBalance.setCellValueFactory(new PropertyValueFactory<FxTradesDisplayData, String>("coinBalance"));
        latestPrice.setCellValueFactory(new PropertyValueFactory<FxTradesDisplayData, String>("latestPrice"));
        startValue.setCellValueFactory(new PropertyValueFactory<FxTradesDisplayData, String>("startingAccountValue"));
        accountValue.setCellValueFactory(new PropertyValueFactory<FxTradesDisplayData, String>("accountValue"));
        profit.setCellValueFactory(new PropertyValueFactory<FxTradesDisplayData, String>("profit"));
        currencyBalance.prefWidthProperty().bind(performanceTable.widthProperty().divide(6));
        coinBalance.prefWidthProperty().bind(performanceTable.widthProperty().divide(6));
        latestPrice.prefWidthProperty().bind(performanceTable.widthProperty().divide(6));
        startValue.prefWidthProperty().bind(performanceTable.widthProperty().divide(8));
        accountValue.prefWidthProperty().bind(performanceTable.widthProperty().divide(6));
        profit.prefWidthProperty().bind(performanceTable.widthProperty().divide(6));
        performanceTable.getColumns().addAll(currencyBalance, coinBalance, latestPrice, startValue, accountValue, profit);

        topAsksTable.setEditable(true);
        topAsksTable.setPrefSize(300, 200);
        TableColumn priceCol = new TableColumn("Ask price");
        TableColumn amtCol = new TableColumn("amount");
        priceCol.setCellValueFactory(new PropertyValueFactory<Order, String>("p"));
        amtCol.setCellValueFactory(new PropertyValueFactory<Order, String>("a"));
        priceCol.prefWidthProperty().bind(topAsksTable.widthProperty().divide(2)); // w * 1/2
        amtCol.prefWidthProperty().bind(topAsksTable.widthProperty().divide(2));
        topAsksTable.getColumns().addAll(priceCol, amtCol);

        topBidsTable.setEditable(true);
        topBidsTable.setPrefSize(300, 200);
        TableColumn priceBCol = new TableColumn("Bid price");
        TableColumn amtBCol = new TableColumn("amount");
        priceBCol.setCellValueFactory(new PropertyValueFactory<Order, String>("p"));
        amtBCol.setCellValueFactory(new PropertyValueFactory<Order, String>("a"));
        priceBCol.prefWidthProperty().bind(topBidsTable.widthProperty().divide(2)); // w * 1/2
        amtBCol.prefWidthProperty().bind(topBidsTable.widthProperty().divide(2));
        topBidsTable.getColumns().addAll(priceBCol, amtBCol);

        String numTrades = PropertiesUtil.getProperty("displaydata.numberoftrades");
        String topX = PropertiesUtil.getProperty("displaydata.topx");
        final Label topOrdersLabel = new Label("Top bids/asks");
        topOrdersLabel.setFont(new Font("Arial", 15));
        HBox tops = new HBox(10, topBidsTable, topAsksTable);
        tops.setMinWidth(1000);

        recentTradesWsTable.setEditable(true);
        recentTradesWsTable.setPrefHeight(300);
        TableColumn createdAtColWs = new TableColumn("created at");
        TableColumn amountColWs = new TableColumn("amount");
        TableColumn tradePriceColWs = new TableColumn("price");
        TableColumn tickColWs = new TableColumn("tick");
        TableColumn tidColWs = new TableColumn("trade id");
        TableColumn makerSideColWs = new TableColumn("maker side");
        TableColumn nStatusColWs = new TableColumn("n-th Status");
        createdAtColWs.setCellValueFactory(new PropertyValueFactory<Trade, String>("createdAt"));
        makerSideColWs.setCellValueFactory(new PropertyValueFactory<Trade, String>("makerSide"));
        amountColWs.setCellValueFactory(new PropertyValueFactory<Trade, String>("amount"));
        tradePriceColWs.setCellValueFactory(new PropertyValueFactory<Trade, String>("price"));
        tidColWs.setCellValueFactory(new PropertyValueFactory<Trade, String>("tid"));
        tickColWs.setCellValueFactory(new PropertyValueFactory<Trade, String>("tick"));
        nStatusColWs.setCellValueFactory(new PropertyValueFactory<Trade, String>("nthStatus"));
        createdAtColWs.prefWidthProperty().bind(recentTradesWsTable.widthProperty().divide(7));
        amountColWs.prefWidthProperty().bind(recentTradesWsTable.widthProperty().divide(8));
        tradePriceColWs.prefWidthProperty().bind(recentTradesWsTable.widthProperty().divide(8));
        tidColWs.prefWidthProperty().bind(recentTradesWsTable.widthProperty().divide(8));
        makerSideColWs.prefWidthProperty().bind(recentTradesWsTable.widthProperty().divide(7));
        tickColWs.prefWidthProperty().bind(recentTradesWsTable.widthProperty().divide(7));
        nStatusColWs.prefWidthProperty().bind(recentTradesWsTable.widthProperty().divide(7));
        recentTradesWsTable.getColumns().addAll(
                tidColWs, tickColWs, amountColWs, tradePriceColWs, createdAtColWs, makerSideColWs, nStatusColWs);

        final Label label3 = new Label("Top " + numTrades + " trades");
        label3.setFont(new Font("Arial", 15));

        VBox root = new VBox(10, configBox, createConfigButton, appStatusLabel,
                performanceTable, label3, recentTradesWsTable, topOrdersLabel, tops);
        root.setPadding(new Insets(10));

        primaryStage.setTitle("---ITSY-BITSO Trading---");
        Scene primaryScene = new Scene(root, 1500, 900);
        primaryScene.getStylesheets().add(getClass().getResource("/itsybitso.css").toExternalForm());
        primaryStage.setScene(primaryScene);
        primaryStage.show();
    }

    /**
     * method called by the TradeDataWsClient upon receiving refreshed trade data
     * @param fxTradesDisplayData
     */
    public void displayTradesData(FxTradesDisplayData fxTradesDisplayData) {
        // Ensure we're on the JavaFX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> displayTradesData(fxTradesDisplayData));
            return;
        }
        
        int displayDataHashCode = fxTradesDisplayData.hashCode();
        if (previousDisplayDataHashCode != displayDataHashCode) {
            previousDisplayDataHashCode = displayDataHashCode;
        }
        final ObservableList<FxTradesDisplayData> data = FXCollections.observableArrayList(fxTradesDisplayData);
        final ObservableList<Trade> recentTradesWs = FXCollections.observableList(fxTradesDisplayData.getRecentTrades());
        performanceTable.setItems(data);
        recentTradesWsTable.setItems(recentTradesWs);
        
        // Update the status label to ensure it's refreshed
        if (appStatusLabel != null) {
            String currentText = appStatusLabel.getText();
            appStatusLabel.setText(currentText + " ");
            appStatusLabel.setText(currentText);
        }
    }

    /**
     * Calls localhost servlet REST endpoints to start backend processes. Creates a new ConfigurableTradingExecutor() in the
     * backend that can then be monitored.
     * @param label UI label to update with status
     */
    private void createTradingConfigurationBackendProcesses(Label label) {
        try {
            // Get server URL and deployment path from properties file
            String serverUrl = PropertiesUtil.getProperty("server.rest.url");
            String deployment = PropertiesUtil.getProperty("server.deployment");

            // Create HTTP client for making REST calls
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();

            // If there's an existing configuration, remove it first
            if (tradeConfig != null) {
                // Build URL for REST endpoint to remove existing trading configuration
                String removeUrl = deployment.concat("removeconfiguration/" + tradeConfig.getId());
                
                // Create and execute GET request to backend to remove existing configuration
                HttpGet httpRemoveRequest = new HttpGet(serverUrl + removeUrl);
                CloseableHttpResponse httpRemoveResponse = httpClient.execute(httpRemoveRequest);
                
                // Log the removal response
                System.out.println("Removing existing configuration: " + removeUrl + " " + httpRemoveResponse.getCode());
                httpRemoveResponse.close();
            }

            // Create new trade configuration object with ups/downs parameters from UI
            TradeConfig newTradeConfig = new TradeConfig(ups.getText(), downs.getText());

            // Build URL for REST endpoint to add new trading configuration
            // Format: {deployment}/addconfiguration/{configId}/{ups}/{downs}
            String url = deployment.concat("addconfiguration/" + newTradeConfig.getId()
                    + "/" + ups.getText() + "/" + downs.getText());

            // Create and execute GET request to backend - calling IbbeController.addTradingConfiguration()
            HttpGet httpGetRequest = new HttpGet(serverUrl + url);
            CloseableHttpResponse httpResponse = httpClient.execute(httpGetRequest);

            // Wait 4 seconds before starting to monitor the object
            Thread.sleep(4000);

            // Clean up HTTP resources
            httpResponse.close();
            System.out.println("++++++++++++++++ " + url + " " + httpResponse.getCode());
            httpClient.close();

            // Update UI to show configuration is being monitored - use Platform.runLater to ensure UI update
            final String statusText = "Config " + newTradeConfig.getId() + " ups=" + ups.getText() + ", downs=" + downs.getText();
            Platform.runLater(() -> {
                label.setText(statusText);
                // Force a UI refresh
                label.setVisible(false);
                label.setVisible(true);
            });
            
            // Store the new trade config
            tradeConfig = newTradeConfig;
            
            // Automatically start monitoring after creating the configuration
            tradeDataWsClient.subscribeToTradeConfigMonitoring(tradeConfig);
            orderbookWsClient.subscribeToOrderbookMonitoring();

        } catch (Exception ex) {
            ex.printStackTrace();
            final String errorText = "Error creating configuration: " + ex.getMessage();
            Platform.runLater(() -> {
                label.setText(errorText);
                // Force a UI refresh
                label.setVisible(false);
                label.setVisible(true);
            });
        }
    }

    /**
     * This method is now only used by the TradeDataWsClient to indicate monitoring has started
     * It no longer needs to change any button text since the monitorButton is removed
     */
    public void changeMonitorButtonText() {
        // Method kept for compatibility with TradeDataWsClient
        // No action needed as the monitorButton is removed
    }

    /**
     * Update the top asks and bids tables with data
     * @param asks List of ask orders
     * @param bids List of bid orders
     */
    public void updateOrderTables(Order[] asks, Order[] bids) {
        // Ensure we're on the JavaFX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateOrderTables(asks, bids));
            return;
        }
        
        final ObservableList<Order> listOfAsks = FXCollections.observableList(Arrays.asList(asks));
        final ObservableList<Order> listOfBids = FXCollections.observableList(Arrays.asList(bids));
        topAsksTable.setItems(listOfAsks);
        topBidsTable.setItems(listOfBids);
    }
} 