package de.lazytv.pro.live;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Executor factory local to the Live TV package.
 *
 * LiveTvActivity historically used one serial worker for both catalog/network
 * loading and channel URL resolution. The Live worker has two execution slots
 * and a tiny bounded pending queue. When a viewer changes channels rapidly,
 * older pending resolve work is discarded in favour of the newest selection;
 * playbackGeneration in LiveTvActivity still prevents any already-running stale
 * resolve from being applied to the persistent player/session.
 *
 * Fixed pools used for EPG and artwork retain their requested sizes.
 */
final class Executors {
    private Executors() {}

    static ExecutorService newSingleThreadExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(2),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.prestartAllCoreThreads();
        return executor;
    }

    static ExecutorService newFixedThreadPool(int size) {
        return java.util.concurrent.Executors.newFixedThreadPool(size);
    }
}
