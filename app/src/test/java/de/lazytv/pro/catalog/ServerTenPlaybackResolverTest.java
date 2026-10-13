package de.lazytv.pro.catalog;
import org.junit.Test;import static org.junit.Assert.*;
public class ServerTenPlaybackResolverTest {
 @Test public void tokenUrlBecomesOriginalReferenceWithoutOldToken() {
  ServerTenPlaybackResolver.Reference ref=ServerTenPlaybackResolver.reference("https://provider.invalid:8443/play/live.php?mac=00%3A11%3A22%3A33%3A44%3A55&stream=91&play_token=expired");
  assertNotNull(ref);assertEquals("https://provider.invalid:8443",ref.portal);assertEquals("00:11:22:33:44:55",ref.mac);assertEquals("ffmpeg http://localhost/ch/91_",ref.command);assertFalse(ref.command.contains("expired"));
 }
 @Test public void ordinaryM3uUrlDoesNotInventPortalIdentity() {
  assertNull(ServerTenPlaybackResolver.reference("https://provider.invalid/channel.m3u8"));
  assertNull(ServerTenPlaybackResolver.reference("https://provider.invalid/play?mac=invalid&stream=91&play_token=old"));
 }
 @Test public void referenceRetainsChannelAndSheetUrl() {
  String url="http://provider.invalid/play?mac=00:11:22:33:44:55&stream=8&play_token=old";
  StreamItem item=new StreamItem("stable-id","Fixture","category","",url,"epg",CatalogType.LIVE);
  assertTrue(ServerTenPlaybackResolver.canRefresh(item));assertEquals(url,item.streamUrl);assertEquals("stable-id",item.id);
 }
 @Test public void encodedQueryOrderAndExtraSignedFieldsStillUseStablePortalIdentity() {
  String url="https://provider.invalid:9443/play/live.php?play_token=expired%2Btoken&signature=a%2Fb%3D%3D&stream=73&mac=AA%3Abb%3ACC%3Add%3AEE%3Aff&expires=1";
  ServerTenPlaybackResolver.Reference ref=ServerTenPlaybackResolver.reference(url);
  assertNotNull(ref);assertEquals("https://provider.invalid:9443",ref.portal);assertEquals("AA:bb:CC:dd:EE:ff",ref.mac);assertEquals("ffmpeg http://localhost/ch/73_",ref.command);
  assertFalse(ref.command.contains("expired"));assertFalse(ref.command.contains("signature"));assertFalse(ref.command.contains("expires"));
 }
 @Test public void staleSheetTokenNeverBecomesTheRefreshCommand() {
  String stale="http://provider.invalid/play?stream=9&play_token=old-token&mac=00%3A11%3A22%3A33%3A44%3A55";
  StreamItem item=new StreamItem("sheet-id","Premium","cat","",stale,"",CatalogType.LIVE);
  ServerTenPlaybackResolver.Reference ref=ServerTenPlaybackResolver.reference(item.streamUrl);
  assertNotNull(ref);assertEquals("ffmpeg http://localhost/ch/9_",ref.command);assertEquals(stale,item.streamUrl);
 }
}
