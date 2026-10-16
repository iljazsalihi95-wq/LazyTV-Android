package de.lazytv.pro.live;

import java.util.concurrent.ExecutorService;

/**
 * Executor factory local to the Live TV package.
 *
 * LiveTvActivity historically used one serial worker for both catalog/network
 * loading and channel URL resolution. A slow catalog request could therefore
 * hold every later channel selection behind it. The live worker is deliberately
 * bounded to two threads: one slow network task can no longer block the next
 * independent live operation, while playbackGeneration in LiveTvActivity still
 * prevents stale channel resolutions from being applied to the player.
 *
 * Fixed pools used for EPG and artwork retain their requested sizes.
 */
final class Executors {
    private Executors() {}

    static ExecutorService newSingleThreadExecutor() {
        return java.util.concurrent.Executors.newFixedThreadPool(2);
    }

    static ExecutorService newFixedThreadPool(int size) {
        return java.util.concurrent.Executors.newFixedThreadPool(size);
    }
}
