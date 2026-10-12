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
}
