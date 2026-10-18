package de.lazytv.pro.stb;
import org.junit.Test;import static org.junit.Assert.*;
public class StbVideoBoundsTest {
 @Test public void widescreenFillsPreviewWidthWithoutCrop(){assertArrayEquals(new int[]{640,360},StbVideoBounds.fit(640,420,1920,1080,1,1));}
 @Test public void sameAspectExpandsToFullscreenAndBack(){assertArrayEquals(new int[]{1920,1080},StbVideoBounds.fit(1920,1080,1280,720,1,1));assertArrayEquals(new int[]{640,360},StbVideoBounds.fit(640,360,1280,720,1,1));}
 @Test public void fourByThreeKeepsPillarsAndAnamorphicPixelsAreRespected(){assertArrayEquals(new int[]{480,360},StbVideoBounds.fit(640,360,720,576,16,15));assertArrayEquals(new int[]{640,360},StbVideoBounds.fit(640,360,720,576,64,45));}
 @Test public void missingDimensionsUseFullFrameUntilDecoderReportsSize(){assertArrayEquals(new int[]{-1,-1},StbVideoBounds.fit(640,360,0,0,1,1));}
}
