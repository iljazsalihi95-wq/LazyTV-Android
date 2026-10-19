package de.lazytv.pro.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeCallbackFenceTest {
    @Test public void rapidSwitchingRejectsEarlierEngines() {
        NativeCallbackFence fence = new NativeCallbackFence();
        Object a = new Object(), b = new Object(), c = new Object();
        long first = fence.begin(a), second = fence.begin(b), third = fence.begin(c);
        assertFalse(fence.accepts(first, a));
        assertFalse(fence.accepts(second, b));
        assertFalse(fence.accepts(third, b));
        assertTrue(fence.accepts(third, c));
    }

    @Test public void reusedEngineCannotDeliverPreviousPlaybackEvents() {
        NativeCallbackFence fence = new NativeCallbackFence();
        Object engine = new Object();
        long first = fence.begin(engine);
        long retry = fence.begin(engine);
        assertFalse(fence.accepts(first, engine));
        assertTrue(fence.accepts(retry, engine));
    }

    @Test public void stopRejectsQueuedCallbacksBeforeAnotherPlaybackStarts() {
        NativeCallbackFence fence = new NativeCallbackFence();
        Object engine = new Object();
        long ticket = fence.begin(engine);
        fence.invalidate();
        assertFalse(fence.accepts(ticket, engine));
        assertFalse(fence.accepts(ticket, null));
        long next = fence.begin(engine);
        assertFalse(fence.accepts(ticket, engine));
        assertTrue(fence.accepts(next, engine));
    }
}
