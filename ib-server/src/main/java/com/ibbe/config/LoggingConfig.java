package com.ibbe.config;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.logging.LogManager;

/**
 * Configuration class for setting up logging bridges.
 * This class ensures that java.util.logging (JUL) logs are redirected to SLF4J.
 */
@Configuration
public class LoggingConfig {

    /**
     * Initializes the JUL to SLF4J bridge.
     * This method is called automatically by Spring after bean initialization.
     */
    @PostConstruct
    public void init() {
        // Remove existing JUL handlers
        LogManager.getLogManager().reset();
        
        // Install the SLF4J bridge handler
        SLF4JBridgeHandler.install();
    }
} 