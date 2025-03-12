package com.ibbe.fx;

import com.ibbe.entity.Order;
import com.ibbe.entity.OrderBook;
import com.ibbe.websocket.OrderbookWsClient;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;

/**
 * Java FX window client for displaying orderbook chart;
 * - connects to the local back-end to monitor orderbook and trades
 */
public class ChartWindow extends Application {

    private NumberAxis xAxis = new NumberAxis();
    private NumberAxis yAxis = new NumberAxis();
    private ScatterChart<Number, Number> scatterChart;
    private XYChart.Series<Number, Number> askOrders = new XYChart.Series<>();
    private XYChart.Series<Number, Number> bidOrders = new XYChart.Series<>();
    private XYChart.Series<Number, Number> trades = new XYChart.Series<>();
    private XYChart.Series<Number, Number> lastTrade = new XYChart.Series<>();
    private XYChart.Series<Number, Number> avgOrder = new XYChart.Series<>();
    private OrderbookWsClient orderbookWsClient;
    // treshold above which calculate order into average for display
    private final BigDecimal tresholdAmt = new BigDecimal(0.0036);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Initialize in start method
        scatterChart = new ScatterChart<>(xAxis, yAxis);

        primaryStage.setTitle("Orderbook with trades");

        xAxis.setTickUnit(500d);
        xAxis.setTickLabelFont(new Font(24.0d));
        xAxis.setAutoRanging(false);

        yAxis.setTickUnit(0.1d);
        yAxis.setTickLabelFont(new Font(24.0d));
        yAxis.setAutoRanging(false);

        orderbookWsClient = new OrderbookWsClient(this);
        orderbookWsClient.subscribeToOrderbookMonitoring();

        xAxis.setLabel("$ Price");
        yAxis.setLabel("Amount");
        yAxis.autosize();

        scatterChart.setTitle("Trade Orders");
        scatterChart.setPrefHeight(2200d);
        scatterChart.setPrefWidth(1200d);

        scatterChart.autosize();
        askOrders.setName("Asks");
        trades.setName("Trades");
        lastTrade.setName("LastTrade");
        bidOrders.setName("Bids");
        avgOrder.setName("AvgOrder");

        {
            @SuppressWarnings("unchecked")
            var unused = scatterChart.getData().addAll(askOrders, trades, lastTrade, bidOrders, avgOrder);
        }

        Label title = new Label("Orderbook & trades");
        VBox root = new VBox(10, title, scatterChart);
        root.setSpacing(10);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);

        //Set view in window
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/chart.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * method called by the OrderbookWsClient upon receiving refreshed orderbook data
     * @param ob
     */
    public void displayOrderbookData(OrderBook ob) {
        BigDecimal largestOrderAmount = new BigDecimal(0);

        for(Order o : ob.getPayload().getAsks()) {
            // determine max amount of order in set (for sizing the graph)
            if (o.getA().compareTo(largestOrderAmount) > 0)
                largestOrderAmount = o.getA();
        }
        for(Order o : ob.getPayload().getBids()) {
            // determine max amount of order in set (for sizing the graph)
            if (o.getA().compareTo(largestOrderAmount) > 0)
                largestOrderAmount = o.getA();
        }

        // set chart params according to the range of the data
        xAxis.setLowerBound(ob.getPayload().getBids()[49].getP().doubleValue());
        xAxis.setUpperBound(ob.getPayload().getAsks()[49].getP().doubleValue());
        yAxis.setLowerBound(-0.1d);
        yAxis.setUpperBound(largestOrderAmount.doubleValue() + 0.02);

        updateLineChart(ob);
    }

    private void updateLineChart(OrderBook ob) {
        avgOrder.getData().clear();
        BigDecimal avgAskAmount = new BigDecimal(0);
        BigDecimal avgAskPrice = new BigDecimal(0);
        BigDecimal avgBidAmount = new BigDecimal(0);
        BigDecimal avgBidPrice = new BigDecimal(0);

        askOrders.getData().clear();
        int cnt = 0;
        for (Order order : ob.getPayload().getAsks()) {
            // only calculate into average if value of order is above treshold
            avgAskAmount = avgAskAmount.add(order.getA());
            avgAskPrice = avgAskPrice.add(order.getP());
            cnt++;
            if (order.getA().compareTo(tresholdAmt) > 0) {
                askOrders.getData().add(new XYChart.Data<Number, Number>(order.getP(), order.getA()));
            }
        }
        if (cnt > 0) {
            avgAskAmount = avgAskAmount.divide(new BigDecimal(cnt), 4, RoundingMode.HALF_UP);
            avgAskPrice = avgAskPrice.divide(new BigDecimal(cnt), 4, RoundingMode.HALF_UP);
        }
        avgOrder.getData().add(new XYChart.Data<Number, Number>(avgAskPrice, avgAskAmount));

        bidOrders.getData().clear();
        cnt = 0;
        for (Order order : ob.getPayload().getBids()) {
            // only calculate into average if value of order is above treshold
            avgBidAmount = avgBidAmount.add(order.getA());
            avgBidPrice = avgBidPrice.add(order.getP());
            cnt++;
            if (order.getA().compareTo(tresholdAmt) > 0) {
                bidOrders.getData().add(new XYChart.Data<Number, Number>(order.getP(), order.getA()));
            }
        }
        if (cnt > 0) {
            avgBidAmount = avgBidAmount.divide(new BigDecimal(cnt), 4, RoundingMode.HALF_UP);
            avgBidPrice = avgBidPrice.divide(new BigDecimal(cnt), 4, RoundingMode.HALF_UP);
        }
        avgOrder.getData().add(new XYChart.Data<Number, Number>(avgBidPrice, avgBidAmount));

        trades.getData().clear();
        // start with second element as first is the last trade
        for (int i=1; i<ob.getTrades().length; i++) {
            Object trade = ob.getTrades()[i];
            if (trade instanceof LinkedHashMap<?,?> map) {
                trades.getData().add(new XYChart.Data<Number, Number>(
                    ((Number)map.get("price")),
                    ((Number)map.get("amount"))));
            }
        }

        // last trade to be displayed separately
        lastTrade.getData().clear();
        if (ob.getTrades().length > 0 && ob.getTrades()[0] instanceof LinkedHashMap<?,?> map) {
            Double price = (Double)map.get("price");
            Double amount = (Double)map.get("amount");
            XYChart.Data<Number, Number> xyChartData = new XYChart.Data<>(price, amount);
            lastTrade.getData().add(xyChartData);
            Tooltip tooltip = new Tooltip("" + amount + " @ $" + price);
            tooltip.setStyle("-fx-font-size: 36px;");
            Tooltip.install(xyChartData.getNode(), tooltip);
        }
    }
} 