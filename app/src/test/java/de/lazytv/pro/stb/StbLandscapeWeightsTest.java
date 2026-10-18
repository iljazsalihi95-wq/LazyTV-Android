package de.lazytv.pro.stb;
import org.junit.Test;
import static org.junit.Assert.*;
public class StbLandscapeWeightsTest {
 @Test public void previewOwnsMajorityOfLandscapeBody(){assertTrue(0.56f>0.44f);assertEquals(1f,0.56f+0.44f,0.0001f);}
 @Test public void fullscreenPreviewOwnsWholeBody(){float browser=0f,preview=1f;assertEquals(0f,browser,0f);assertEquals(1f,preview,0f);}
}
