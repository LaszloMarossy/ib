#!/bin/bash
# Script to run the performance window
# Usage: ./scripts/rp.sh

cd "/Users/laszlo/dev/code/ib/ib-client"

# Set the Java module path
JAVA_MODULE_PATH="--module-path /Users/laszlo/dev/code/ib/ib-client/target/classes:/Users/laszlo/.m2/repository/org/openjfx/javafx-controls/17.0.2/javafx-controls-17.0.2.jar:/Users/laszlo/.m2/repository/org/openjfx/javafx-graphics/17.0.2/javafx-graphics-17.0.2.jar:/Users/laszlo/.m2/repository/org/openjfx/javafx-base/17.0.2/javafx-base-17.0.2.jar"

# Run the performance window
java --module-path /Users/laszlo/dev/javafx-sdk-21.0.1/lib --add-modules javafx.controls,javafx.fxml -jar target/ibbe-performance-jar-with-dependencies.jar 