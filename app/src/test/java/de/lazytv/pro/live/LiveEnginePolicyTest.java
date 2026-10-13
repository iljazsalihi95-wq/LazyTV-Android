package de.lazytv.pro.live;
import org.junit.Test;import static org.junit.Assert.*;
public class LiveEnginePolicyTest {
 @Test public void codecFailuresTryNativeOnce(){assertTrue(LiveEnginePolicy.nativeFallback(4001,0,false));assertTrue(LiveEnginePolicy.nativeFallback(3001,0,false));assertFalse(LiveEnginePolicy.nativeFallback(4001,0,true));}
 @Test public void authNetworkAndDrmNeverBecomeDecoderFallback(){for(int status:new int[]{401,402,403,404,500})assertFalse(LiveEnginePolicy.nativeFallback(4001,status,false));assertFalse(LiveEnginePolicy.nativeFallback(2001,0,false));assertFalse(LiveEnginePolicy.nativeFallback(6001,0,false));}
}
