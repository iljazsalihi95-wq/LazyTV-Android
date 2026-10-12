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
}
