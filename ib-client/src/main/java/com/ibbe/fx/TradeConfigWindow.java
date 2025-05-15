package com.ibbe.fx;

import com.ibbe.util.PropertiesUtil;
import com.ibbe.util.RandomString;
import javafx.application.Application;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TradeConfigWindow extends Application {

  protected TextField upsField = new TextField();
  protected TextField downsField = new TextField();

  // Add checkboxes for trading criteria
  protected CheckBox avgBidVsAvgAskCheckBox = new CheckBox("Avg Bid vs Avg Ask");
  protected CheckBox shortVsLongMovAvgCheckBox = new CheckBox("Short vs Long Mov Avg");
  protected CheckBox sumAmtUpVsDownCheckBox = new CheckBox("Sum Amt Up vs Down");
  protected CheckBox tradePriceCloserToAskVsBuyCheckBox = new CheckBox("Price Closer To Ask vs Buy");
  
  // Add a field to track the current configuration ID - shared by all subclasses
  protected String currentConfigId = null;

  @Override
  public void start(Stage stage) throws Exception {
    // Set up input fields
    upsField.setText(PropertiesUtil.getProperty("trade.up_m"));
    downsField.setText(PropertiesUtil.getProperty("trade.down_n"));
    upsField.setPrefWidth(100);
    downsField.setPrefWidth(100);
  }
  
  /**
   * Generates a new configuration ID using the RandomString utility.
   * This can be used by subclasses when creating new configurations.
   * 
   * @return A new random configuration ID
   */
  protected String generateConfigId() {
    // Create a new random ID for this configuration
    return new RandomString().getRandomString();
  }
  
  /**
   * Updates the current configuration ID.
   * This should be called by subclasses when a new configuration is created.
   * 
   * @param configId The new configuration ID
   */
  protected void setCurrentConfigId(String configId) {
    this.currentConfigId = configId;
  }
  
  /**
   * Gets the current configuration ID.
   * 
   * @return The current configuration ID, or null if none is set
   */
  protected String getCurrentConfigId() {
    return currentConfigId;
  }
  
  /**
   * Ends a performance analysis for the current configuration ID.
   * This makes a call to the server to remove the configuration.
   * 
   * @param statusHandler A callback to handle status updates or error messages
   * @return true if the call was successful, false otherwise
   */
  protected boolean endCurrentConfiguration(StatusHandler statusHandler) {
    // Check if we have a configuration ID to end
    if (currentConfigId == null || currentConfigId.trim().isEmpty()) {
      if (statusHandler != null) {
        statusHandler.handleStatus("No configuration ID to remove", true);
      }
      return false;
    }
    
    try {
      // Update status
      if (statusHandler != null) {
        statusHandler.handleStatus("Removing configuration: " + currentConfigId + "...", false);
      }
      
      // Get the server URL from properties
      String serverUrl = PropertiesUtil.getProperty("server.rest.url");
      String removeEndpoint = serverUrl + "/removeconfiguration/" + currentConfigId;
      
      // Create an HTTP client
      HttpClient httpClient = HttpClient.newHttpClient();
      
      // Create the request
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(removeEndpoint))
          .GET()
          .build();
      
      // Send the request
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      
      // Check the response
      if (response.statusCode() != 200) {
        throw new Exception("Error removing configuration: " + response.body());
      }
      
      // Update status on success
      if (statusHandler != null) {
        statusHandler.handleStatus("Configuration removed successfully: " + currentConfigId, false);
      }
      
      // Clear the current configuration ID
      currentConfigId = null;
      
      return true;
    } catch (Exception e) {
      if (statusHandler != null) {
        statusHandler.handleStatus("Error removing configuration: " + e.getMessage(), true);
      }
      return false;
    }
  }
  
  /**
   * Interface for handling status updates.
   * This allows subclasses to handle status updates in their own way.
   */
  public interface StatusHandler {
    /**
     * Handles a status update.
     * 
     * @param message The status message
     * @param isError Whether the message represents an error
     */
    void handleStatus(String message, boolean isError);
  }
}
