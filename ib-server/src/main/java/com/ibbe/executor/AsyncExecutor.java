package com.ibbe.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;


/**
 * Base class for common code in async executors.
 */
public class AsyncExecutor {
    List<Integer> totalCounts = new ArrayList<>();
    List<String> futureResults = new ArrayList<>();
    static List<Future<String>> futures = new ArrayList<>();
    List<Integer> warnings = new ArrayList<>();
    static int defaultNumThreads = 5;


    public AsyncExecutor() {
        totalCounts = new ArrayList<>();
        futureResults = new ArrayList<>();
        warnings = new ArrayList<>();
    }

}
