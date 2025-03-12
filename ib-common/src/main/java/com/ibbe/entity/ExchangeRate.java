package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "success",
    "error",
    "terms",
    "privacy",
    "timestamp",
    "source",
    "quotes"
})
public class ExchangeRate {

  @JsonProperty("success")
  private Boolean success;
  @JsonProperty("error")
  private ExchangeRateError error;
  @JsonProperty("terms")
  private String terms;
  @JsonProperty("privacy")
  private String privacy;
  @JsonProperty("timestamp")
  private Integer timestamp;
  @JsonProperty("source")
  private String source;
  @JsonProperty("quotes")
  private ExchangeRateQuote quotes;

  /**
   * No args constructor for use in serialization
   */
  public ExchangeRate() {
  }

  /**
   * @param timestamp
   * @param quotes
   * @param source
   * @param error
   * @param terms
   * @param privacy
   * @param success
   */
  public ExchangeRate(Boolean success, ExchangeRateError error, String terms, String privacy, Integer timestamp, String source, ExchangeRateQuote quotes) {
    super();
    this.success = success;
    this.error = error;
    this.terms = terms;
    this.privacy = privacy;
    this.timestamp = timestamp;
    this.source = source;
    this.quotes = quotes;
  }

  @JsonProperty("success")
  public Boolean getSuccess() {
    return success;
  }

  @JsonProperty("success")
  public void setSuccess(Boolean success) {
    this.success = success;
  }

  @JsonProperty("error")
  public ExchangeRateError getError() {
    return error;
  }

  @JsonProperty("error")
  public void setError(ExchangeRateError error) {
    this.error = error;
  }

  @JsonProperty("terms")
  public String getTerms() {
    return terms;
  }

  @JsonProperty("terms")
  public void setTerms(String terms) {
    this.terms = terms;
  }

  @JsonProperty("privacy")
  public String getPrivacy() {
    return privacy;
  }

  @JsonProperty("privacy")
  public void setPrivacy(String privacy) {
    this.privacy = privacy;
  }

  @JsonProperty("timestamp")
  public Integer getTimestamp() {
    return timestamp;
  }

  @JsonProperty("timestamp")
  public void setTimestamp(Integer timestamp) {
    this.timestamp = timestamp;
  }

  @JsonProperty("source")
  public String getSource() {
    return source;
  }

  @JsonProperty("source")
  public void setSource(String source) {
    this.source = source;
  }

  @JsonProperty("quotes")
  public ExchangeRateQuote getQuotes() {
    if (this.quotes == null) {
      this.quotes = new ExchangeRateQuote();
    }
    return quotes;
  }

  @JsonProperty("quotes")
  public void setQuotes(ExchangeRateQuote quotes) {
    this.quotes = quotes;
  }

}