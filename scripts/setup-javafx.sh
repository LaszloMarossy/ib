#!/bin/bash

# This script downloads and sets up JavaFX SDK for macOS

# Define the JavaFX version and download URL
JAVAFX_VERSION="21.0.2"
JAVAFX_URL="https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_osx-x64_bin-sdk.zip"
DOWNLOAD_DIR="/Users/laszlo/Downloads"
INSTALL_DIR="/Users/laszlo/dev"

echo "Setting up JavaFX SDK ${JAVAFX_VERSION}..."

# Create directories if they don't exist
mkdir -p "$DOWNLOAD_DIR"
mkdir -p "$INSTALL_DIR"

# Download JavaFX SDK if not already downloaded
if [ ! -f "${DOWNLOAD_DIR}/javafx-sdk-${JAVAFX_VERSION}.zip" ]; then
  echo "Downloading JavaFX SDK ${JAVAFX_VERSION}..."
  curl -L "$JAVAFX_URL" -o "${DOWNLOAD_DIR}/javafx-sdk-${JAVAFX_VERSION}.zip"
else
  echo "JavaFX SDK ${JAVAFX_VERSION} already downloaded."
fi

# Extract JavaFX SDK if not already extracted
if [ ! -d "${INSTALL_DIR}/javafx-sdk-${JAVAFX_VERSION}" ]; then
  echo "Extracting JavaFX SDK ${JAVAFX_VERSION}..."
  unzip -q "${DOWNLOAD_DIR}/javafx-sdk-${JAVAFX_VERSION}.zip" -d "$INSTALL_DIR"
else
  echo "JavaFX SDK ${JAVAFX_VERSION} already extracted."
fi

# Create a symbolic link for easier access
if [ ! -L "${INSTALL_DIR}/javafx-sdk" ]; then
  echo "Creating symbolic link..."
  ln -sf "${INSTALL_DIR}/javafx-sdk-${JAVAFX_VERSION}" "${INSTALL_DIR}/javafx-sdk"
else
  echo "Symbolic link already exists."
fi

# Update the run-performance.sh script with the correct JavaFX path
echo "Updating run-performance.sh with the correct JavaFX path..."
sed -i '' "s|JAVAFX_PATH=\"/Users/laszlo/dev/javafx-sdk\"|JAVAFX_PATH=\"${INSTALL_DIR}/javafx-sdk\"|g" run-performance.sh

echo "JavaFX SDK ${JAVAFX_VERSION} has been set up successfully!"
echo "You can now run the performance application with:"
echo "  ./run-performance.sh" 