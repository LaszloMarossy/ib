package com.ibbe.entity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Represents a trade transaction in the ib system.
 * Implements Comparable to allow sorting based on trade IDs.
 * Uses the Builder pattern for flexible object construction.
 */
public class Trade implements Comparable<Trade> {
  private OrderBookPayload obp;

//  protected String book;
  protected String createdAt;
  protected BigDecimal amount;
  protected String makerSide;
  protected BigDecimal price;
  protected Long tid;
  protected Tick tick;
  // current status relative to previous tick values
  protected String nthStatus;

  /**
   * trade object used within the ib system
   */
  protected Trade() {

  }

  /**
   * Standard constructor for basic trade creation
   */
  public Trade(String createdAt,
               BigDecimal amount,
               String makerSide,
               BigDecimal price,
               Long tid) {
    this.createdAt = createdAt != null ? createdAt : "";
    this.amount = amount != null ? amount : BigDecimal.ZERO;
    this.makerSide = makerSide != null ? makerSide : "buy"; // Default to buy if null
    this.price = price != null ? price : BigDecimal.ZERO;
    this.tid = tid;
  }

  /**
   * Deep copy constructor to create independent trade instance
   */
  public Trade(Trade trade) {
    if (trade == null) {
      // Create a default trade if null is passed
      this.createdAt = "";
      this.amount = BigDecimal.ZERO;
      this.makerSide = "buy";
      this.price = BigDecimal.ZERO;
      this.tid = null;
      this.tick = null;
      this.nthStatus = null;
      return;
    }
    
    // Copy values with null checks
    this.createdAt = trade.createdAt != null ? new String(trade.createdAt) : "";
    this.amount = trade.amount != null ? new BigDecimal(trade.amount.toString()) : BigDecimal.ZERO;
    this.makerSide = trade.makerSide != null ? new String(trade.makerSide) : "buy";
    this.price = trade.price != null ? new BigDecimal(trade.price.toString()) : BigDecimal.ZERO;
    this.tid = trade.tid;
    this.tick = trade.tick != null ? Tick.valueOf(trade.tick.name()) : null;
    this.nthStatus = trade.nthStatus != null ? new String(trade.nthStatus) : null;
    this.obp = trade.obp;
  }

//  public String getBook() {
//    return book;
//  }

  public String getCreatedAt() {
    return createdAt;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getMakerSide() {
    return makerSide;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public Long getTid() {
    return tid;
  }

  public void setTid(Long tid) {
    this.tid = tid;
  }

  public Tick getTick() {
    return tick;
  }

  public void setTick(Tick tick) {
    this.tick = tick;
  }

  public String getNthStatus() {
    return nthStatus;
  }

  public void setNthStatus(String nthStatus) {
    this.nthStatus = nthStatus;
  }

  public void setObp(OrderBookPayload obp) {
    this.obp = obp;
  }

  public OrderBookPayload getObp() {
    return this.obp;
  }

  /**
   * Builder pattern constructor - creates a new Trade from builder values
   */
  private Trade(Builder builder) {
    this.createdAt = builder.createdAt;
    this.amount = builder.amount;
    this.makerSide = builder.makerSide;
    this.price = builder.price;
    this.tid = builder.tid;
    this.tick = builder.tick;
    this.nthStatus = builder.nthStatus;
    this.obp = builder.obp;
  }

  /**
   * Entry point for the builder pattern
   * @return new Builder instance to construct a Trade
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for constructing Trade objects in a flexible, readable manner.
   * Supports fluent interface pattern with method chaining.
   */
  public static class Builder {
    private String createdAt;
    private BigDecimal amount;
    private String makerSide;
    private BigDecimal price;
    private Long tid;
    private Tick tick;
    private String nthStatus;
    private OrderBookPayload obp;

    public Builder createdAt(String createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder makerSide(String makerSide) {
      this.makerSide = makerSide;
      return this;
    }

    public Builder price(BigDecimal price) {
      this.price = price;
      return this;
    }

    public Builder tid(Long tid) {
      this.tid = tid;
      return this;
    }

    public Builder tick(Tick tick) {
      this.tick = tick;
      return this;
    }

    public Builder nthStatus(String nthStatus) {
      this.nthStatus = nthStatus;
      return this;
    }

    public Builder obp(OrderBookPayload obp) {
      this.obp = obp;
      return this;
    }

    /**
     * Constructs the final Trade object
     * @return new Trade instance with all builder-set values
     */
    public Trade build() {
      return new Trade(this);
    }
  }

  /**
   * Compares trades based on their trade IDs to allow insertion of pretend-trades
   * @param t Trade to compare this to
   * @return negative if this trade is earlier, positive if later, 0 if same ID
   */
  @Override
  public int compareTo(Trade t) {
    return Comparator.comparing(Trade::getTid).compare(t, this);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Trade)) return false;
    if (obj == null)
      return false;
    Trade other = (Trade) obj;
    if (tid.compareTo(other.tid) != 0)
      return false;
    return true;
  }

  @Override
  public int hashCode() {
      return Math.toIntExact(tid);
  }

  /**
   * Utility method to create a string of trade IDs for logging/debugging
   */
  public static String printTrades(String prefix, Trade[] trades) {
    StringBuilder tradeIds = new StringBuilder(prefix);
    Arrays.stream(trades)
        .forEach(trade -> tradeIds.append(" " + trade.getTid()));
    return tradeIds.toString();
  }
}
