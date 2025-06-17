package com.ibbe.util;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class ParallelDequeTest {

    // ANSI escape codes for colors
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";

    public static void main(String[] args) {
        // --- Setup: Create two corresponding deques ---
        Deque<BigDecimal> tradePrices = new ArrayDeque<>();
        Deque<BigDecimal> tradeAmounts = new ArrayDeque<>();

        System.out.println(ANSI_CYAN + "Populating two deques with corresponding data using addFirst()..." + ANSI_RESET);
        
        // Let's add 5 corresponding trades. Newest are added first.
        tradePrices.addFirst(new BigDecimal("105.50")); // Newest
        tradeAmounts.addFirst(new BigDecimal("0.5"));

        tradePrices.addFirst(new BigDecimal("104.20"));
        tradeAmounts.addFirst(new BigDecimal("1.2"));

        tradePrices.addFirst(new BigDecimal("103.80"));
        tradeAmounts.addFirst(new BigDecimal("0.8"));

        tradePrices.addFirst(new BigDecimal("102.90"));
        tradeAmounts.addFirst(new BigDecimal("2.0"));

        tradePrices.addFirst(new BigDecimal("101.10")); // Oldest
        tradeAmounts.addFirst(new BigDecimal("1.5"));
        
        System.out.println("Prices Deque (Newest at Head): " + tradePrices);
        System.out.println("Amounts Deque (Newest at Head): " + tradeAmounts);
        
        System.out.println("\n----------------------------------------------------------\n");

        // --- Scenario 1: Iterating from NEWEST to OLDEST ---
        System.out.println(ANSI_YELLOW + "1. Iterating in parallel from NEWEST to OLDEST (using standard .iterator())" + ANSI_RESET);
        
        Iterator<BigDecimal> priceIter = tradePrices.iterator();
        Iterator<BigDecimal> amountIter = tradeAmounts.iterator();

        while (priceIter.hasNext() && amountIter.hasNext()) {
            BigDecimal price = priceIter.next();
            BigDecimal amount = amountIter.next();
            System.out.println("  - " + ANSI_GREEN + "Got Price: " + price + ANSI_RESET + ", " + ANSI_RED + "Amount: " + amount + ANSI_RESET);
        }

        System.out.println("\n----------------------------------------------------------\n");

        // --- Scenario 2: Iterating from OLDEST to NEWEST ---
        System.out.println(ANSI_YELLOW + "2. Iterating in parallel from OLDEST to NEWEST (using .descendingIterator())" + ANSI_RESET);

        Iterator<BigDecimal> priceDescIter = tradePrices.descendingIterator();
        Iterator<BigDecimal> amountDescIter = tradeAmounts.descendingIterator();

        while (priceDescIter.hasNext() && amountDescIter.hasNext()) {
            BigDecimal price = priceDescIter.next();
            BigDecimal amount = amountDescIter.next();
            System.out.println("  - " + ANSI_GREEN + "Got Price: " + price + ANSI_RESET + ", " + ANSI_RED + "Amount: " + amount + ANSI_RESET);
        }
    }
} 