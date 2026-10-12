package de.lazytv.pro.catalog;
import org.junit.Test;
import static org.junit.Assert.*;
public class XtreamUrlBuilderTest {
 @Test public void httpPortAndTrailingSlash()throws Exception {
  assertEquals("http://provider.invalid:80",XtreamUrlBuilder.base("http://provider.invalid:80/"));
  assertEquals("http://provider.invalid:8080",XtreamUrlBuilder.base("provider.invalid:8080///"));
 }
 @Test public void httpsAndApiQuery()throws Exception {
  assertEquals("https://provider.invalid",XtreamUrlBuilder.base("https://provider.invalid/player_api.php?username=fixture&password=a%24b"));
  assertEquals("https://provider.invalid:443/sub",XtreamUrlBuilder.base("https://provider.invalid:443/sub/get.php?type=m3u&output=ts"));
 }
 @Test public void encodedPathAndIpv6()throws Exception {
  assertEquals("http://provider.invalid:8080/my%20portal",XtreamUrlBuilder.base("http://provider.invalid:8080/my portal/player_api.php"));
  assertEquals("http://[::1]:8080",XtreamUrlBuilder.base("http://[::1]:8080/player_api.php"));
 }
 @Test public void invalidAuthorityFailsBeforeDns()throws Exception {
  try{XtreamUrlBuilder.base("http://:80");fail();}catch(CatalogException expected){}
 }
 private de.lazytv.pro.playlist.Playlist playlist(){return new de.lazytv.pro.playlist.Playlist("fixture","Fixture",de.lazytv.pro.playlist.PlaylistType.XTREAM_CODES,"http://provider.invalid:80/player_api.php?ignored=yes","user name","p$&+ /","",1);}
 @Test public void allActionsUseOneApiAndPreserveCredentials()throws Exception {
  String[] actions={"get_live_categories","get_live_streams","get_vod_categories","get_vod_streams","get_vod_info","get_series_categories","get_series","get_series_info","get_short_epg","get_simple_data_table"};
  for(String action:actions){String url=XtreamUrlBuilder.api(playlist(),action,"stream_id","9");assertTrue(url.startsWith("http://provider.invalid:80/player_api.php?"));assertFalse(url.contains("player_api.php/player_api.php"));assertFalse(url.contains("ignored="));assertTrue(url.contains("password=p%24%26%2B+%2F"));assertTrue(url.contains("action="+action));}
 }
 @Test public void streamPathAndM3uFallbackEncodeReservedCharacters()throws Exception {
  assertEquals("http://provider.invalid:80/live/user%20name/p%24%26%2B%20%2F/9.ts",XtreamUrlBuilder.stream(XtreamUrlBuilder.base(playlist().getUrl()),playlist(),"live","9","ts"));
  assertTrue(XtreamUrlBuilder.m3u(playlist()).startsWith("http://provider.invalid:80/get.php?"));assertTrue(XtreamUrlBuilder.m3u(playlist()).endsWith("&type=m3u&output=ts"));
 }
}
