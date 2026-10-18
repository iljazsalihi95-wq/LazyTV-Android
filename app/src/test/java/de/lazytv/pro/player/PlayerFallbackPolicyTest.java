package de.lazytv.pro.player;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PlayerFallbackPolicyTest {
 @Test public void providerRefusalsNeverBecomeDecoderFallback(){for(int status:new int[]{401,403,404,429,458,500}){assertFalse(PlayerFallbackPolicy.decoderFailure(4003,status));assertFalse(PlayerFallbackPolicy.decoderFailure(2004,status));}}
 @Test public void networkFailuresDoNotSwitchDecoder(){assertFalse(PlayerFallbackPolicy.decoderFailure(2001,0));assertFalse(PlayerFallbackPolicy.decoderFailure(2002,0));assertTrue(PlayerFallbackPolicy.decoderFailure(3001,0));assertTrue(PlayerFallbackPolicy.decoderFailure(4003,0));}
 @Test public void requiredHeadersSelectFullHeaderEngine(){for(String key:new String[]{"Cookie","Authorization","Origin","X-User-Agent"}){Map<String,String> h=new HashMap<>();h.put(key,"fixture");assertFalse(PlayerFallbackPolicy.vlcSupportsHeaders(h));}}
 @Test public void implementedVlcOptionsAreCaseInsensitive(){Map<String,String> h=new HashMap<>();h.put("user-agent","fixture-agent");h.put("REFERER","https://fixture.invalid/");h.put("Accept","*/*");assertTrue(PlayerFallbackPolicy.vlcSupportsHeaders(h));assertEquals("fixture-agent",PlayerFallbackPolicy.header(h,"User-Agent"));h.put("Accept","application/custom");assertFalse(PlayerFallbackPolicy.vlcSupportsHeaders(h));}
}
