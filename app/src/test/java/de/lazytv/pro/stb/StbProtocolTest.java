package de.lazytv.pro.stb;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class StbProtocolTest {
 @Test public void normalizationKeepsSchemePortAndContext(){assertEquals("http://example.test:8080/stalker_portal/portal.php",StbProtocol.endpoints("example.test:8080/stalker_portal/c/").get(0));assertEquals("https://example.test/portal.php",StbProtocol.endpoints("https://example.test/").get(0));}
 @Test public void explicitEndpointFirstAndDiscoveryBounded(){List<String> e=StbProtocol.endpoints("https://example.test/custom/server/load.php?ignored=true");assertEquals("https://example.test/custom/server/load.php",e.get(0));assertTrue(e.size()<=6);assertEquals(e.size(),new HashSet<>(e).size());}
 @Test public void handshakeAndToken()throws Exception{assertEquals("fixture-token",StbProtocol.token(StbProtocol.payload("{\"js\":{\"token\":\"fixture-token\"}}")));assertEquals("",StbProtocol.token(StbProtocol.payload("{\"js\":false}")));}
 @Test public void categoriesKeepOriginalIdentity()throws Exception{StbProtocol.Genre g=StbProtocol.genres(StbProtocol.payload("{\"js\":[{\"id\":\"42\",\"title\":\"DE SPORTS\",\"country_code\":\"DE\"}]}")).get(0);assertEquals("42",g.id);assertEquals("DE SPORTS",g.title);assertEquals("DE",g.country);}
 @Test public void channelsKeepOriginalCommandAndFullName()throws Exception{StbProtocol.Channel c=StbProtocol.channels(StbProtocol.payload("{\"js\":{\"data\":[{\"id\":\"7\",\"name\":\"TOP CHANNEL HD\",\"tv_genre_id\":\"42\",\"cmd\":\"ffmpeg http://localhost/ch/7\",\"logo\":\"logo.png\",\"xmltv_id\":\"top\"}]}}")).get(0);assertEquals("ffmpeg http://localhost/ch/7",c.cmd);assertEquals("TOP CHANNEL HD",c.name);assertEquals("42",c.genreId);assertEquals("top",c.epgId);}
 @Test public void freshLinksAreNeverCached()throws Exception{String old=StbProtocol.link(StbProtocol.payload("{\"js\":{\"cmd\":\"ffmpeg https://example.test/live?token=old\"}}"),"https://example.test/portal.php");String fresh=StbProtocol.link(StbProtocol.payload("{\"js\":{\"cmd\":\"ffmpeg https://example.test/live?token=new\"}}"),"https://example.test/portal.php");assertNotEquals(old,fresh);assertTrue(fresh.endsWith("token=new"));}
 @Test public void relativeLinkResolvesAgainstPortal()throws Exception{assertEquals("https://example.test/stream/7",StbProtocol.link("/stream/7","https://example.test/portal.php"));}
 @Test(expected=IllegalArgumentException.class)public void invalidPortalRejected(){StbProtocol.endpoints("file:///etc/passwd");}
 @Test(expected=IllegalArgumentException.class)public void invalidLinkRejected(){StbProtocol.link("javascript:bad","https://example.test/portal.php");}
}
