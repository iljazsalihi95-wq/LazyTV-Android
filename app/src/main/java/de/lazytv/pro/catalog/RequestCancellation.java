package de.lazytv.pro.catalog;

import java.net.HttpURLConnection;
import java.util.concurrent.ConcurrentHashMap;

/** Cancels sockets owned by a stopped screen, without touching another provider's requests. */
public final class RequestCancellation {
  private static final ConcurrentHashMap<Thread, HttpURLConnection> active = new ConcurrentHashMap<>();
  public static void register(HttpURLConnection c) throws java.io.InterruptedIOException {
    if (Thread.currentThread().isInterrupted()) { c.disconnect(); throw new java.io.InterruptedIOException(); }
    active.put(Thread.currentThread(), c);
    if (Thread.currentThread().isInterrupted()) { unregister(c); c.disconnect(); throw new java.io.InterruptedIOException(); }
  }
  public static void unregister(HttpURLConnection c) { active.remove(Thread.currentThread(), c); }
  public static void cancel(Thread thread) {
    HttpURLConnection c = active.remove(thread);
    if (c != null) c.disconnect();
  }
  private RequestCancellation() {}
}
