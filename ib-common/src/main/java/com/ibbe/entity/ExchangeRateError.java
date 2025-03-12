package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "code",
    "info"
})
public class ExchangeRateError {

  @JsonProperty("code")
  private Integer code;
  @JsonProperty("info")
  private String info;

  /**
   * No args constructor for use in serialization
   */
  public ExchangeRateError() {
  }

  /**
   * @param code
   * @param info
   */
  public ExchangeRateError(Integer code, String info) {
    super();
    this.code = code;
    this.info = info;
  }

  @JsonProperty("code")
  public Integer getCode() {
    return code;
  }

  @JsonProperty("code")
  public void setCode(Integer code) {
    this.code = code;
  }

  @JsonProperty("info")
  public String getInfo() {
    return info;
  }

  @JsonProperty("info")
  public void setInfo(String info) {
    this.info = info;
  }

}