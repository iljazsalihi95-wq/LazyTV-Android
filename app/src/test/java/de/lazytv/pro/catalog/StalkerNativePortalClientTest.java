package de.lazytv.pro.catalog;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;import org.json.*;import de.lazytv.pro.playlist.*;
public class StalkerNativePortalClientTest {
 private static class Fixture extends StalkerNativePortalClient {
  int calls,refreshes,headerReads;boolean refuseAlways,refuseFirst;String query;
  @Override public synchronized Map<String,String> sessionHeaders(Playlist p){headerReads++;return new LinkedHashMap<>();}
  @Override public synchronized void clear(){refreshes++;super.clear();}
  @Override protected String requestLink(Playlist p,String q,Map<String,String> h){calls++;query=q;
   if(refuseAlways||refuseFirst&&calls==1)return "{\"js\":{\"status\":401}}";
   return "{\"js\":{\"cmd\":\"ffmpeg https://provider.invalid/live?play_token=fresh"+calls+"&amp;x=1\"}}";
  }
 }
 private Playlist playlist(){return new Playlist("fixture","Native",PlaylistType.STALKER_PORTAL,"http://provider.invalid/c/","","","00:11:22:33:44:55",1);}
 private StreamItem channel(){return new StreamItem("9","Neutral","1","","ffmpeg http://localhost/ch/9_","",CatalogType.LIVE);}
 @Test public void numericRefusalRefreshesOnceAndKeepsOriginalCmd()throws Exception {
  Fixture f=new Fixture();f.refuseFirst=true;String url=f.resolve(playlist(),channel()).url;
  assertEquals(2,f.calls);assertEquals(1,f.refreshes);assertTrue(url.endsWith("fresh2&x=1"));
  assertTrue(java.net.URLDecoder.decode(f.query,"UTF-8").contains("cmd=ffmpeg http://localhost/ch/9_"));
 }
 @Test public void repeatedRefusalHasHardLimit()throws Exception {
  Fixture f=new Fixture();f.refuseAlways=true;try{f.resolve(playlist(),channel());fail();}catch(ProviderHttpException expected){assertEquals(401,expected.status);}
  assertEquals(2,f.calls);assertEquals(1,f.refreshes);
 }
 @Test public void selectionsNeverReuseResolvedUrl()throws Exception {
  Fixture f=new Fixture();StreamItem channel=channel();assertNotEquals(f.resolve(playlist(),channel).url,f.resolve(playlist(),channel).url);assertEquals("ffmpeg http://localhost/ch/9_",channel.streamUrl);
 }
 @Test public void portalNormalizationRetainsNestedPathAndPort()throws Exception {
  assertEquals("http://provider.invalid:8080/portal.php",StalkerPortalAddress.normalize("http://provider.invalid:8080/c/"));
  assertEquals("https://provider.invalid/stalker_portal/server/load.php",StalkerPortalAddress.normalize("https://provider.invalid/stalker_portal/c/"));
  assertEquals("http://provider.invalid/custom/portal.php",StalkerPortalAddress.normalize("http://provider.invalid/custom/portal.php?ignored=1"));
  assertTrue(StalkerPortalAddress.endpoints("http://provider.invalid/custom/c/").contains("http://provider.invalid/custom/server/load.php"));
 }
 @Test public void liveStreamResolutionDoesNotWaitForEpg()throws Exception {Fixture source=new Fixture();new StreamResolver(source,null,false).resolve(playlist(),channel());assertEquals(2,source.headerReads);assertEquals(1,source.calls);}
}
