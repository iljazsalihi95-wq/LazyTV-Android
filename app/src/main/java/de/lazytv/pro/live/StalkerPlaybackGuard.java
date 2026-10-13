package de.lazytv.pro.live;
/** A Stalker selection invalidates older work and permits a small bounded playback-link recovery budget. */
final class StalkerPlaybackGuard {
 private static final int MAX_RETRIES=2;
 private long generation;
 private int retries;
 synchronized long begin() { retries=0;return ++generation; }
 synchronized boolean current(long expected) { return expected==generation; }
 synchronized boolean retry(long expected) {
  if(!current(expected)||retries>=MAX_RETRIES)return false;
  retries++;return true;
 }
}
