package de.lazytv.pro.live;
import org.junit.Test;import static org.junit.Assert.*;
public class LiveDisplayStateTest {
 @Test public void browserReturnsToSameFullscreenContext(){LiveDisplayState state=new LiveDisplayState();state.fullscreen(true);assertTrue(state.openBrowser());assertEquals(LiveDisplayState.Mode.BROWSER,state.mode());assertTrue(state.closeBrowser());assertEquals(LiveDisplayState.Mode.FULLSCREEN,state.mode());}
 @Test public void repeatedBrowserEventsDoNotStartNewTransitions(){LiveDisplayState state=new LiveDisplayState();assertFalse(state.openBrowser());state.fullscreen(true);assertTrue(state.openBrowser());assertFalse(state.openBrowser());assertTrue(state.closeBrowser());assertFalse(state.closeBrowser());}
 @Test public void normalBackCanReturnToPreview(){LiveDisplayState state=new LiveDisplayState();state.fullscreen(true);state.fullscreen(false);assertEquals(LiveDisplayState.Mode.PREVIEW,state.mode());}
 @Test public void controlsAndBrowserHaveIndependentBackContexts(){LiveDisplayState state=new LiveDisplayState();assertFalse(state.showControls());state.fullscreen(true);assertTrue(state.showControls());assertTrue(state.controls());assertTrue(state.openBrowser());assertFalse(state.controls());assertTrue(state.closeBrowser());assertFalse(state.controls());assertTrue(state.showControls());assertTrue(state.hideControls());assertFalse(state.hideControls());}
}
