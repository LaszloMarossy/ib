package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibbe.util.PropertiesUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/**
 * represents trading info needed by the FX UI
 * TODO can be separated into diff areas and scheduled/polled separately
 */
public class FxTradesDisplayData {

  // trade balance values
  private BigDecimal currencyBalance;
  private BigDecimal coinBalance;
  private BigDecimal latestPrice;
  private BigDecimal startingAccountValue;
  private BigDecimal profit;
  private BigDecimal accountValue;

  // recent trades from websocket
  private ArrayList<Trade> recentTrades;

  // constructor for ObjectMapper
  public FxTradesDisplayData(
      @JsonProperty("currencyBalance") BigDecimal currencyBalance,
      @JsonProperty("coinBalance") BigDecimal coinBalance,
      @JsonProperty("latestPrice") BigDecimal latestPrice,
      @JsonProperty("recentTrades") ArrayList<Trade> recentTrades) {
    this.currencyBalance = currencyBalance;
    this.coinBalance = coinBalance;
    this.latestPrice = latestPrice;
    this.recentTrades = recentTrades;
    // derives from coin an currency balance..
    this.startingAccountValue = calculateAccountValue();
    this.accountValue = startingAccountValue;
    this.profit = calculateProfit();
  }

  public ArrayList<Trade> getRecentTrades() {
    return recentTrades;
  }

  public void addRecentTradeWs(Trade recentTrade) {
    recentTrades.addLast(recentTrade);
    if (recentTrades.size() > Integer.parseInt(PropertiesUtil.getProperty("displaydata.numberoftrades"))) {
      recentTrades.removeFirst();
    }
  }

  public BigDecimal getCurrencyBalance() {
    return currencyBalance;
  }

  public void setCurrencyBalance(BigDecimal currencyBalance) {
    this.currencyBalance = currencyBalance;
    this.profit = calculateProfit();
  }

  public BigDecimal getCoinBalance() {
    return coinBalance;
  }

  public void setCoinBalance(BigDecimal coinBalance) {
    this.coinBalance = coinBalance;
    this.profit = calculateProfit();
  }

  public BigDecimal getLatestPrice() {
    return latestPrice;
  }

  public void setLatestPrice(BigDecimal latestPrice) {
    this.latestPrice = latestPrice;
    this.profit = calculateProfit();
  }

  public BigDecimal calculateAccountValue() {
    accountValue = coinBalance.multiply(latestPrice).add(currencyBalance).setScale(2, RoundingMode.DOWN);
    return accountValue;
  }

  public BigDecimal calculateProfit() {
    return calculateAccountValue().subtract(startingAccountValue).setScale(2, RoundingMode.DOWN);
  }

  public BigDecimal getStartingAccountValue() {
    return startingAccountValue;
  }

  public BigDecimal getProfit() {
    return profit;
  }

  public void setProfit(BigDecimal profit) {
    this.profit = profit;
  }

  public BigDecimal getAccountValue() {
    return accountValue;
  }

}
