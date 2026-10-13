package de.lazytv.pro.catalog;
import org.junit.*;import static org.junit.Assert.*;import com.sun.net.httpserver.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.atomic.AtomicInteger;import de.lazytv.pro.playlist.*;
public class StalkerNativeSessionContinuityTest {
 private HttpServer server;private String base;private final AtomicInteger handshakes=new AtomicInteger(),links=new AtomicInteger();private String expectedCookie="handshake";
 @Before public void start()throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+server.getAddress().getPort();
 server.createContext("/tenant/portal.php",e->reply(e,200,"{\"js\":{\"data\":[]}}"));
 server.createContext("/tenant/server/load.php",e->{String query=e.getRequestURI().getQuery();
 if(query.contains("action=handshake")){handshakes.incrementAndGet();expectedCookie="handshake";e.getResponseHeaders().add("Set-Cookie","session=handshake; Path=/tenant/");reply(e,200,"{\"js\":{\"token\":\"fixture-token\"}}");return;}
 String cookie=e.getRequestHeaders().getFirst("Cookie");if(cookie==null||!cookie.contains("session="+expectedCookie)){reply(e,403,"{}");return;}
 if(query.contains("action=get_profile")){e.getResponseHeaders().add("Set-Cookie","session=profile; Path=/tenant/");expectedCookie="profile";reply(e,200,"{\"js\":{\"id\":1}}");return;}
 if(query.contains("action=create_link")){int count=links.incrementAndGet();e.getResponseHeaders().add("Set-Cookie","session=play"+count+"; Path=/tenant/");expectedCookie="play"+count;reply(e,200,"{\"js\":{\"cmd\":\"ffmpeg "+base+"/media.ts?play_token=fresh"+count+"\"}}");return;}
 reply(e,200,"{\"js\":{\"status\":\"active\"}}");});server.start();}
 @After public void stop(){server.stop(0);}
 private void reply(HttpExchange exchange,int status,String body)throws java.io.IOException {byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);try(java.io.OutputStream out=exchange.getResponseBody()){out.write(bytes);}exchange.close();}
 @Test public void profileAndRotatingPlaybackCookiesStayInSameSession()throws Exception {Playlist playlist=new Playlist("fixture","Fixture",PlaylistType.STALKER_PORTAL,base+"/tenant/c/","","","00:11:22:33:44:55",0);StalkerNativePortalClient client=new StalkerNativePortalClient();StreamItem channel=new StreamItem("7","Channel","genre","","ffmpeg http://localhost/ch/7_","",CatalogType.LIVE);StalkerCatalogSource.LinkResult first=client.resolve(playlist,channel);assertTrue(first.headers.get("Cookie").contains("session=play1"));StalkerCatalogSource.LinkResult second=client.resolve(playlist,channel);assertTrue(second.url.contains("fresh2"));assertTrue(second.headers.get("Cookie").contains("session=play2"));assertEquals(1,handshakes.get());assertEquals(2,links.get());assertEquals("ffmpeg http://localhost/ch/7_",channel.streamUrl);}
 @Test public void bareHostAndPortAreNormalizedBeforeHandshake()throws Exception {Playlist playlist=new Playlist("fixture","Fixture",PlaylistType.STALKER_PORTAL,base.substring(7)+"/tenant/c/","","","00:11:22:33:44:55",0);assertFalse(new StalkerNativePortalClient().sessionHeaders(playlist).isEmpty());assertEquals(1,handshakes.get());}
}
