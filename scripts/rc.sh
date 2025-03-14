#!/bin/bash
cd "/Users/laszlo/dev/code/ibtrader/ib-client"

# Run the ChartWindow application
echo "Running the ChartWindow application..."
java --module-path /Users/laszlo/dev/javafx-sdk-21.0.1/lib --add-modules javafx.controls,javafx.fxml -jar target/ibbe-chart-jar-with-dependencies.jar 