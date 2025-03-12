package com.ibbe.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class AppWebSocketConfig implements WebSocketMessageBrokerConfigurer {

//            registration.setMessageSizeLimit(200000); // default : 64 * 1024
//            registration.setSendTimeLimit(20 * 10000); // default : 10 * 10000
//            registration.setSendBufferSizeLimit(3* 512 * 1024); // default : 512 * 1024

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        // Increase message size limit to 1MB (default is 64KB)
        registry.setMessageSizeLimit(1024 * 1024);
        // Increase time limit for sending a message to 30 seconds (default is 10 seconds)
        registry.setSendTimeLimit(30 * 1000);
        // Increase buffer size limit to 3MB (default is 512KB)
        registry.setSendBufferSizeLimit(3 * 1024 * 1024);
        
        WebSocketMessageBrokerConfigurer.super.configureWebSocketTransport(registry);
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/websocket").setAllowedOrigins("*");
    }
}