package de.lazytv.pro.live;

import java.util.*;
import java.util.concurrent.*;

/** Bounded screen workers. Stopping the screen also disconnects its in-flight HTTP sockets. */
final class Executors {
  static ThreadPoolExecutor pool(int size, int capacity) {
    return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(capacity), new ThreadPoolExecutor.DiscardOldestPolicy()) {
      private final Set<Thread> running = ConcurrentHashMap.newKeySet();
      @Override protected void beforeExecute(Thread t, Runnable r) { running.add(t); super.beforeExecute(t, r); }
      @Override protected void afterExecute(Runnable r, Throwable t) { running.remove(Thread.currentThread()); super.afterExecute(r, t); }
      @Override public List<Runnable> shutdownNow() {
        List<Runnable> pending = super.shutdownNow();
        for (Thread thread : running) de.lazytv.pro.catalog.RequestCancellation.cancel(thread);
        return pending;
      }
    };
  }
  static ExecutorService newSingleThreadExecutor() { return pool(1, 1); }
  static ExecutorService newFixedThreadPool(int size) { return pool(size, 32); }
  private Executors() {}
}
