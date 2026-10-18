package de.lazytv.pro.catalog;
import org.junit.Test;
import static org.junit.Assert.*;
public class SheetServerPlaybackPolicyTest {
 private static final String SIGNED="http://fixture.invalid/play?mac=00:11:22:33:44:55&stream=17&play_token=old";
 @Test public void legacyPremiumRetainsHistoricalFreshLinkPath(){
  assertFalse(SheetServerCatalogSource.useDirectPlayback("SERVER_10",SIGNED,false));
  assertNotNull(ServerTenPlaybackResolver.reference(SIGNED));
 }
 @Test public void anotherServerDoesNotInheritPremiumMagPolicy(){
  assertTrue(SheetServerCatalogSource.useDirectPlayback("SERVER_09",SIGNED,false));
 }
 @Test public void ordinaryPremiumUrlRemainsDirect(){
  assertTrue(SheetServerCatalogSource.useDirectPlayback("SERVER_10","https://fixture.invalid/channel.m3u8",false));
  assertTrue(SheetServerCatalogSource.useDirectPlayback("SERVER_10","https://fixture.invalid/play?play_token=fixture",false));
 }
 @Test public void explicitProviderIdentityStillResolves(){
  assertFalse(SheetServerCatalogSource.useDirectPlayback("SERVER_09",SIGNED,true));
 }
}
