package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibbe.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/**
 * represents trading info needed by the FX UI
 * TODO can be separated into diff areas and scheduled/polled separately
 */
public class FxTradesDisplayData {

  private static final Logger log = LoggerFactory.getLogger(FxTradesDisplayData.class);
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
    this.currencyBalance = currencyBalance != null ? currencyBalance : BigDecimal.ZERO;
    this.coinBalance = coinBalance != null ? coinBalance : BigDecimal.ZERO;
    this.latestPrice = latestPrice != null ? latestPrice : BigDecimal.ZERO;
    this.recentTrades = recentTrades != null ? recentTrades : new ArrayList<>();
    // derives from coin an currency balance..
//    this.startingAccountValue = calculateAccountValue();
//    this.accountValue = startingAccountValue;
//    this.profit = new BigDecimal(0);
  }

  public ArrayList<Trade> getRecentTrades() {
    return recentTrades;
  }

  public void addRecentTradeWs(Trade recentTrade) {
    if (recentTrade == null) {
      return;
    }
    
    if (recentTrades == null) {
      recentTrades = new ArrayList<>();
    }
    
    recentTrades.add(recentTrade);
    if (recentTrades.size() > Integer.parseInt(PropertiesUtil.getProperty("displaydata.numberoftrades"))) {
      recentTrades.remove(0);
    }
  }

  public BigDecimal getCurrencyBalance() {
    return currencyBalance;
  }

  public void setCurrencyBalance(BigDecimal currencyBalance) {
    this.currencyBalance = currencyBalance;
//    this.profit = calculateProfit();
  }

  public BigDecimal getCoinBalance() {
    return coinBalance;
  }

  public void setCoinBalance(BigDecimal coinBalance) {
    this.coinBalance = coinBalance;
//    this.profit = calculateProfit();
  }

  public BigDecimal getLatestPrice() {
    return latestPrice;
  }

  public void setLatestPrice(BigDecimal latestPrice) {
    this.latestPrice = latestPrice;
    // Don't recalculate profit when only the price changes
    // Profit should only change when balances change (i.e., when trades occur)
    // this.profit = calculateProfit();
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

  public void setAccountValue(BigDecimal accountValue) {
    this.accountValue = accountValue;
  }



}
