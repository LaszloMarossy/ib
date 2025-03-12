package com.ibbe.entity;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.beans.factory.annotation.Value;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "USDMXN"
})
public class ExchangeRateQuote {

  @JsonProperty("USDMXN")
  private Double uSDMXN;

  /**
   * No args constructor for use in serialization w default value of 17.0
   */
  public ExchangeRateQuote() {
//    this.uSDMXN = Double.parseDouble(POLLER_DEFAULT_XCHRATE);
  }

  /**
   * @param uSDMXN
   */
  public ExchangeRateQuote(Double uSDMXN) {
    super();
    this.uSDMXN = uSDMXN;
  }

  @JsonProperty("USDMXN")
  public Double getUSDMXN() {
    return uSDMXN;
  }

  @JsonProperty("USDMXN")
  public void setUSDMXN(Double uSDMXN) {
    this.uSDMXN = uSDMXN;
  }

}