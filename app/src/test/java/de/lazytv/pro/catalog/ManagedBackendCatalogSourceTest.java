package de.lazytv.pro.catalog;
import org.junit.Test;import static org.junit.Assert.*;
public class ManagedBackendCatalogSourceTest {
 @Test public void originalApiMetadataAndStableIdsSurviveTokenRotation()throws Exception {String template="{\"ok\":true,\"channels\":[{\"id\":\"stable\",\"name\":\"Fixture\",\"country\":\"AL\",\"category\":\"News\",\"tvgId\":\"fixture.epg\",\"url\":\"http://fixture.invalid/play?play_token=%s\"}]}";StreamItem first=ManagedBackendCatalogSource.parse("Fixture server",String.format(template,"old")).items.get(0),second=ManagedBackendCatalogSource.parse("Fixture server",String.format(template,"fresh")).items.get(0);assertEquals(first.id,second.id);assertNotEquals(first.streamUrl,second.streamUrl);assertEquals("fixture.epg",second.tvgId);assertEquals("Fixture server",second.meta("managed_server"));}
 @Test public void signedUrlsNeedRefreshAndOrdinaryStreamsDoNot(){assertTrue(ManagedBackendCatalogSource.expiring("http://fixture.invalid/play?play_token=expired"));assertFalse(ManagedBackendCatalogSource.expiring("http://fixture.invalid/live/7.ts"));}
 @Test public void inactiveRowsAndBackendRefusalsAreNotPlayback()throws Exception {try{ManagedBackendCatalogSource.parse("Fixture","{\"ok\":false,\"error\":\"private-data\"}");fail();}catch(CatalogException error){assertFalse(error.getMessage().contains("private-data"));}try{ManagedBackendCatalogSource.parse("Fixture","{\"ok\":true,\"channels\":[{\"active\":false,\"name\":\"Off\",\"url\":\"http://fixture.invalid\"}]}");fail();}catch(CatalogException empty){assertTrue(empty.getMessage().contains("aktive"));}}
 @Test public void signedManagedReferenceUsesFreshPortalResolution()throws Exception {
  String url="http://fixture.invalid/play?mac=00:11:22:33:44:55&stream=17&play_token=fixture";
  String json=new org.json.JSONObject().put("ok",true).put("channels",new org.json.JSONArray().put(new org.json.JSONObject().put("id","17").put("name","Fixture").put("url",url))).toString();
  StreamItem item=ManagedBackendCatalogSource.parse("LAZI IPTV",json).items.get(0);
  assertEquals("true",item.meta("managed_direct"));assertTrue(ServerTenPlaybackResolver.canRefresh(item));
  ServerTenPlaybackResolver.Reference ref=ServerTenPlaybackResolver.reference(item);
  assertNotNull(ref);assertEquals("http://fixture.invalid",ref.portal);assertEquals("00:11:22:33:44:55",ref.mac);assertEquals("ffmpeg http://localhost/ch/17_",ref.command);
 }
 @Test public void ordinaryManagedDirectUrlStaysDirect()throws Exception {
  String url="http://fixture.invalid/live/17.ts";
  String json=new org.json.JSONObject().put("ok",true).put("channels",new org.json.JSONArray().put(new org.json.JSONObject().put("id","17").put("name","Fixture").put("url",url))).toString();
  StreamItem item=ManagedBackendCatalogSource.parse("LAZI IPTV",json).items.get(0);
  assertEquals("true",item.meta("managed_direct"));assertFalse(ServerTenPlaybackResolver.canRefresh(item));
  assertEquals(url,ServerTenPlaybackResolver.resolve(item).url);
 }
 @Test public void explicitMagIdentityStillUsesItsOwnResolver()throws Exception {
  String json=new org.json.JSONObject().put("ok",true).put("channels",new org.json.JSONArray().put(new org.json.JSONObject().put("name","Fixture").put("url","http://fixture.invalid/play?play_token=old").put("portal_url","http://portal.invalid/stalker_portal").put("mac","00:11:22:33:44:55").put("original_cmd","ffmpeg http://localhost/ch/17_"))).toString();
  StreamItem item=ManagedBackendCatalogSource.parse("Fixture",json).items.get(0);
  assertTrue(ServerTenPlaybackResolver.canRefresh(item));assertEquals("",item.meta("managed_direct"));
 }
}
