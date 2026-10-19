package de.lazytv.pro.live;

/** Rejects callbacks from a stopped engine, including reuse of the same engine or URL. */
final class NativeCallbackFence {
    private long generation;
    private Object engine;

    synchronized long begin(Object engine) {
        this.engine = engine;
        return ++generation;
    }

    synchronized boolean accepts(long ticket, Object engine) {
        return this.engine != null && this.engine == engine && ticket == generation;
    }

    synchronized void invalidate() {
        engine = null;
        generation++;
    }
}
