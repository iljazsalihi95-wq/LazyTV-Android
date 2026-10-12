package de.lazytv.pro.live;
/** A Stalker selection invalidates older work and permits one playback-link retry. */
final class StalkerPlaybackGuard {
 private long generation;
 private int retries;
 synchronized long begin() { retries=0;return ++generation; }
 synchronized boolean current(long expected) { return expected==generation; }
 synchronized boolean retry(long expected) {
  if(!current(expected)||retries>=1)return false;
  retries++;return true;
 }
}
