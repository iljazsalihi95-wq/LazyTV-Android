package de.lazytv.pro.catalog;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PlaybackRequestSummaryTest {
 @Test public void reportsRequestKindAndStatusWithoutSensitivePayload(){
  Map<String,String> h=new LinkedHashMap<>();h.put("Cookie","private-cookie");h.put("authorization","private-auth");
  Map<String,String> meta=new HashMap<>();meta.put("stalker_session","true");
  ResolvedStream stream=new ResolvedStream("http://fixture.invalid/private-path?play_token=private-token&mac=private-mac","ts","title","","",h,meta);
  String s=PlaybackRequestSummary.describe(stream,458);
  assertTrue(s.contains("MAG/create_link"));assertTrue(s.contains("HTTP 458"));assertTrue(s.contains("http://fixture.invalid"));assertTrue(s.contains("Cookie"));assertTrue(s.contains("Authorization"));assertFalse(s.contains("private-"));assertFalse(s.contains("play_token="));
 }
 @Test public void directRequestStaysDistinctFromMag(){
  ResolvedStream stream=new ResolvedStream("https://fixture.invalid/live.ts","ts","title","","",null,null);
  String s=PlaybackRequestSummary.describe(stream,404);assertTrue(s.contains("DIRECT"));assertFalse(s.contains("create_link"));assertFalse(s.contains("live.ts"));
 }
}
