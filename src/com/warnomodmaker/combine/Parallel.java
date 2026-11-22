package com.warnomodmaker.combine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Runs indexed work on a few threads, rethrowing the first failure as it was thrown. */
public final class Parallel {
    private static final AtomicInteger THREADS = new AtomicInteger();

    private Parallel() {
    }

    public static void forEach(int count, int parallelism, IntConsumer work) {
        map(count, parallelism, i -> {
            work.accept(i);
            return null;
        });
    }

    public static <T> List<T> map(int count, int parallelism, IntFunction<T> work) {
        List<T> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) results.add(null);
        if (count == 0) return results;
        int threads = Math.max(1, Math.min(parallelism, count));
        if (threads == 1) {
            for (int i = 0; i < count; i++) results.set(i, work.apply(i));
            return results;
        }
        // Deep database structures are walked recursively, so the workers get a large stack.
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread thread = new Thread(null, r, "combine-worker-" + THREADS.incrementAndGet(), 64L << 20);
            thread.setDaemon(true);
            return thread;
        });
        try {
            AtomicInteger next = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    for (int i; (i = next.getAndIncrement()) < count; ) results.set(i, work.apply(i));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    next.set(count); // stop the other workers
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    if (cause instanceof Error) throw (Error) cause;
                    throw new IllegalStateException(cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CancellationException();
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Half the processors, at least one. */
    public static int half() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    }
}
