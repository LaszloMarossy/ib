package com.ibbe.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;



/**
 * called by localhost server startup to register available WSocket endpoints available to call
 */
@Configuration
@EnableWebSocket
public class IbbeWsEndpointConfig implements WebSocketConfigurer {

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    System.out.println("&*&*&*&*&*&*&*&*&*& WEBSOCKET BACKEND STARTUP *&*&*&*&*&*&*&*&*&*&*&*&*&");
//    registry.addHandler(new BitsoOrderbookMonitorEndpoint(), "/websocket/orderbookmonitor")
//        .addInterceptors(new HttpSessionHandshakeInterceptor());
    registry.addHandler(new TradingMonitorEndpoint(), "/websocket/tradingconfigmonitor")
        .addInterceptors(new HttpSessionHandshakeInterceptor());

  }

}
