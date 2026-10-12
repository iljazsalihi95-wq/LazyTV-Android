package de.lazytv.pro.catalog;

import org.junit.*;
import static org.junit.Assert.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import de.lazytv.pro.playlist.*;

public class PortalSessionTest {
    private HttpServer server;
    private String base;
    private final AtomicInteger handshakes=new AtomicInteger(),links=new AtomicInteger();
    private boolean rejectOnce,blocked;
    @Before public void start()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/portal.php",e->reply(e,200,"<html>landing page</html>"));
        server.createContext("/server/load.php",e->{
            String q=e.getRequestURI().getQuery();
            if(q.contains("action=handshake")){int n=handshakes.incrementAndGet();e.getResponseHeaders().add("Set-Cookie","session=local"+n+"; Path=/");reply(e,200,"{\"js\":{\"token\":\"test"+n+"\"}}");return;}
            String cookie=e.getRequestHeaders().getFirst("Cookie");
            if(cookie==null||!cookie.contains("session=local"+handshakes.get())||!cookie.contains("mac=")){reply(e,403,"{}");return;}
            if(q.contains("action=get_main_info")){reply(e,200,blocked?"{\"js\":{\"status\":\"expired\"}}":"{\"js\":{\"status\":\"active\"}}");return;}
            if(q.contains("action=get_profile")){reply(e,200,"{\"js\":{\"id\":1}}");return;}
            if(q.contains("action=create_link")){links.incrementAndGet();if(rejectOnce){rejectOnce=false;reply(e,403,"{}");}else reply(e,200,"{\"js\":{\"cmd\":\"ffmpeg "+base+"/media.mp4\"}}");return;}
            if(q.contains("get_categories")||q.contains("get_genres")){reply(e,200,"{\"js\":[{\"id\":\"1\",\"title\":\"Provider category\"}]}");return;}
            reply(e,200,"{\"js\":{\"total_items\":1,\"data\":[{\"id\":\"9\",\"name\":\"Provider title\",\"category_id\":\"1\",\"tv_genre_id\":\"1\",\"cmd\":\"ffmpeg /provider/ref\",\"description\":\"Provider plot\"}]}}");
        });server.start();
    }
    @After public void stop(){server.stop(0);}
    private Playlist playlist(){return new Playlist(UUID.randomUUID().toString(),"Local fixture",PlaylistType.STALKER_PORTAL,base+"/c/","","","00:11:22:33:44:55",1);}
    private static void reply(HttpExchange e,int code,String body)throws java.io.IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(code,b.length);try(java.io.OutputStream out=e.getResponseBody()){out.write(b);}e.close();}
    @Test public void alternateEndpointAndCookiesReuseAcrossAllCatalogs()throws Exception{
        StalkerCatalogSource s=new StalkerCatalogSource();Playlist p=playlist();
        for(CatalogType t:new CatalogType[]{CatalogType.LIVE,CatalogType.MOVIES,CatalogType.SERIES})assertFalse(s.loadType(p,t).isEmpty());
        assertEquals(1,handshakes.get());assertEquals(0,links.get());
        Movie movie=new Movie("9","Movie","1","","ffmpeg /provider/ref","");
        assertEquals(base+"/media.mp4",s.resolve(p,movie).url);assertEquals(1,handshakes.get());
    }
    @Test public void refusedTokenRefreshesOnce()throws Exception{
        StalkerCatalogSource s=new StalkerCatalogSource();Playlist p=playlist();s.loadType(p,CatalogType.MOVIES);rejectOnce=true;
        StalkerCatalogSource.LinkResult r=s.resolve(p,new Movie("9","Movie","1","","ffmpeg /provider/ref",""));
        assertEquals(2,handshakes.get());assertEquals(2,links.get());assertTrue(r.headers.get("Cookie").contains("session=local2"));
    }
    @Test public void expiredAccountCannotBecomeSuccessfulCatalog()throws Exception{
        blocked=true;try{new StalkerCatalogSource().loadType(playlist(),CatalogType.LIVE);fail("expired account accepted");}catch(CatalogException e){assertTrue(e.getMessage().contains("expired"));}
    }
    @Test public void conflictRetriesOnceAndPaymentNeverRetries()throws Exception{
        AtomicInteger conflicts=new AtomicInteger(),payment=new AtomicInteger();
        server.createContext("/conflict",e->{conflicts.incrementAndGet();reply(e,409,"{}");});
        server.createContext("/payment",e->{payment.incrementAndGet();reply(e,402,"{\"password\":\"do-not-show\"}");});
        try{HttpClient.get(base+"/conflict",null);fail();}catch(ProviderHttpException e){assertEquals(409,e.status);}assertEquals(2,conflicts.get());
        try{HttpClient.get(base+"/payment",null);fail();}catch(ProviderHttpException e){assertEquals(402,e.status);assertFalse(e.getMessage().contains("do-not-show"));}assertEquals(1,payment.get());
    }
    @Test public void movieListRetainsProviderPlot()throws Exception{
        Catalog c=new StalkerCatalogSource().loadType(playlist(),CatalogType.MOVIES);assertEquals("Provider plot",c.items.get(0).metadata.get("plot"));
    }
}
