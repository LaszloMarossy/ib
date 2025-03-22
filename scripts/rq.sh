#!/bin/bash
cd "/Users/laszlo/dev/code/ibtrader/ib-client"

# Run the QuickReplayWindow application
echo "Running the QuickReplayWindow application..."
java --module-path /Users/laszlo/dev/javafx-sdk-21.0.1/lib --add-modules javafx.controls,javafx.fxml -jar target/ibbe-quick-replay-jar-with-dependencies.jar 