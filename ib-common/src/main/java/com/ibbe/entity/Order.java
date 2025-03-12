package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class Order {
  String book;
  BigDecimal p;
  BigDecimal a;
  String oid;

  public Order(@JsonProperty("book") String book,
               @JsonProperty("price") BigDecimal price,
               @JsonProperty("amount") BigDecimal amount,
               @JsonProperty("oid") String oid) {
    this.book = book;
    this.p = price;
    this.a = amount;
    this.oid = oid;
  }

  public BigDecimal getP() {
    return p;
  }

  public BigDecimal getA() {
    return a;
  }

}
