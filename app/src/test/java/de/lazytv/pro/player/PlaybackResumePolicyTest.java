package de.lazytv.pro.player;
import org.junit.Test;import static org.junit.Assert.*;
public class PlaybackResumePolicyTest {
 @Test public void actualPositionAndCompletionWindow(){assertFalse(PlaybackResumePolicy.eligible(9999,600000,0));assertTrue(PlaybackResumePolicy.eligible(10000,600000,0));assertFalse(PlaybackResumePolicy.eligible(510000,600000,0));assertFalse(PlaybackResumePolicy.eligible(700000,600000,0));}
 @Test public void unknownDurationAndExpiry(){assertTrue(PlaybackResumePolicy.eligible(20000,0,0));assertTrue(PlaybackResumePolicy.eligible(20000,0,30L*24*60*60*1000));assertFalse(PlaybackResumePolicy.eligible(20000,0,30L*24*60*60*1000+1));}
}
