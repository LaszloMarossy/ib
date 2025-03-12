package com.ibbe.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@EnableAsync
@Component
public class WebSocketConfig implements WebSocketConfigurer {

  Logger LOGGER = LoggerFactory.getLogger(WebSocketConfig.class);

  // Removed BitsoOrderbookMonitorEndpoint as it's been deprecated in favor of OrderbookPublisherService
  
  @Autowired
  TradingMonitorEndpoint tradingMonitorEndpoint;
  
  @Autowired
  PerformanceAnalysisEndpoint performanceAnalysisEndpoint;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
      // Removed BitsoOrderbookMonitorEndpoint registration as it's been deprecated in favor of OrderbookPublisherService
      registry.addHandler(tradingMonitorEndpoint, "/tradingconfigmonitor").setAllowedOrigins("*");
      registry.addHandler(performanceAnalysisEndpoint, "/performanceanalysis").setAllowedOrigins("*");
      LOGGER.info("websocket handlers registered");
  }

  @Bean
  public ServletServerContainerFactoryBean createWebSocketContainer() {
      ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
      container.setMaxTextMessageBufferSize(1024 * 1024); // 1MB
      container.setMaxBinaryMessageBufferSize(1024 * 1024); // 1MB
      container.setMaxSessionIdleTimeout(15 * 60 * 1000L); // 15 minutes
      return container;
  }
}