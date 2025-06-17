package com.ibbe.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class DequeIteratorTest {


    public static void main(String[] args) {
        Deque<String> dataStream = new ArrayDeque<>();

        dataStream.addFirst("Trade #1");
        dataStream.addFirst("Trade #2");
        dataStream.addFirst("Trade #3");
        dataStream.addFirst("Trade #4");

        System.out.println("\n----------------------------------------------------------\n");

        for (String element : dataStream) {
            System.out.println("  - " + element);
        }

        System.out.println("\n----------------------------------------------------------\n");

        Iterator<String> descendingIterator = dataStream.descendingIterator();
//        while (descendingIterator.hasNext()) {
//            System.out.println("  - " + descendingIterator.next());
//        }

        for (int i = 0 ; i < dataStream.size() ; i++) {
            System.out.println("  - " + descendingIterator.next());
        }

    }
} 