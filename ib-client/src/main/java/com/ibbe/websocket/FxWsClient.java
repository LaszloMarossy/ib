package com.ibbe.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibbe.util.PropertiesUtil;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import org.springframework.lang.NonNull;


@Component
public class FxWsClient extends TextWebSocketHandler implements ApplicationListener<ApplicationReadyEvent> {
  protected WebSocketSession session;
  protected ObjectMapper objectMapper = new ObjectMapper();


  /**
   * called after boot startup
   * @param event
   */
  @Override
  public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
    //TODO startup logic here
  }

  /**
   * called at window creation time, before pushing button
   */
  protected void connectToWebSocket(String str) {
    try {
      WebSocketClient client = new StandardWebSocketClient();
      String wsUrl = PropertiesUtil.getProperty("server.ws.url");
      String wsRoot = PropertiesUtil.getProperty("server.ws.root");
      String url = wsUrl.concat(wsRoot).concat(str);
      System.out.println("Url: " + url);



      // Use the execute method instead of deprecated doHandshake
      this.session = client.execute(this, url).get();
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    }
  }


}
