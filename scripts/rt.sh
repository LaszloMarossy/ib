#!/bin/bash
cd "/Users/laszlo/dev/code/ibtrader/ib-client"

# Run the TradingWindow application
echo "Running the TradingWindow application..."
java --module-path /Users/laszlo/dev/javafx-sdk-21.0.1/lib --add-modules javafx.controls,javafx.fxml -jar target/ibbe-trading-jar-with-dependencies.jar 