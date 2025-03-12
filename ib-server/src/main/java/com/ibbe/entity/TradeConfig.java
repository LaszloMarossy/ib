package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibbe.util.RandomString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeConfig {
  private String id;
  @Value("${trade.up_m}")
  private String ups;
  @Value("${trade.down_n}")
  private String downs;

  public TradeConfig() {
    id = RandomString.getRandomString();
  }

  public TradeConfig(String upsIn, String downsIn) {
    id = RandomString.getRandomString();
    if (upsIn != null)
      this.ups = upsIn;
    if (downsIn != null)
      this.downs = downsIn;
  }

  public TradeConfig(@JsonProperty("id") String id,
                     @JsonProperty("ups") String ups,
                     @JsonProperty("downs") String downs) {
    this.id = id;
    this.ups = ups;
    this.downs = downs;
  }

  public String getId() {
    return id;
  }

  public String getUps() {
    return ups;
  }

  public String getDowns() {
    return downs;
  }

  @Override
  public String toString() {
    return "TradeConfig{" +
        "id='" + id + '\'' +
        ", ups='" + ups + '\'' +
        ", downs='" + downs + '\'' +
        '}';
  }
}
