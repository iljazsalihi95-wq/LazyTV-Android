package de.lazytv.pro.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class StalkerPlaybackGuardTest {
 @Test public void oldSelectionAndRetryCannotAffectNewChannel() {
  StalkerPlaybackGuard guard=new StalkerPlaybackGuard();long old=guard.begin();
  assertTrue(guard.current(old));assertTrue(guard.retry(old));long next=guard.begin();
  assertFalse(guard.current(old));assertFalse(guard.retry(old));assertTrue(guard.current(next));
 }
 @Test public void playbackRetryStopsAtOneUntilNewSelection() {
  StalkerPlaybackGuard guard=new StalkerPlaybackGuard();long selected=guard.begin();
  assertTrue(guard.retry(selected));for(int n=0;n<20;n++)assertFalse(guard.retry(selected));
  assertTrue(guard.retry(guard.begin()));
 }
}
