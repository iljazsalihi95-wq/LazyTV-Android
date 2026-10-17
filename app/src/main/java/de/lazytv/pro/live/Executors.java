package de.lazytv.pro.live;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Executor factory local to the Live TV package.
 *
 * LiveTvActivity uses this serial worker for catalog/network loading and channel
 * URL resolution. Keeping exactly one running task prevents two provider
 * resolves from mutating the same provider/session concurrently. The single
 * bounded pending slot is latest-request-first: rapid channel changes discard
 * the older pending resolve in favour of the newest selection, while
 * playbackGeneration in LiveTvActivity prevents an already-running stale
 * resolve from being applied to the persistent playback session.
 *
 * Fixed pools used for EPG and artwork retain their requested sizes.
 */
final class Executors {
    private Executors() {}

    static ExecutorService newSingleThreadExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(1),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.prestartAllCoreThreads();
        return executor;
    }

    static ExecutorService newFixedThreadPool(int size) {
        return java.util.concurrent.Executors.newFixedThreadPool(size);
    }
}
