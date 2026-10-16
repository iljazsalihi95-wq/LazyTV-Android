package de.lazytv.pro.stb;
import org.junit.Test;import static org.junit.Assert.*;
public class StbNativeCallsTest {
 @Test public void releasedDecoderDoesNotCrashSurfaceCleanup(){assertFalse(StbNativeCalls.run(()->{throw new IllegalStateException("released");}));}
 @Test public void missingNativeLibraryDoesNotCrashCleanup(){assertFalse(StbNativeCalls.run(()->{throw new UnsatisfiedLinkError("fixture");}));}
 @Test public void successfulCallRuns(){boolean[] ran={false};assertTrue(StbNativeCalls.run(()->ran[0]=true));assertTrue(ran[0]);}
}
