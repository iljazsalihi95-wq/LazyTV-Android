package de.lazytv.pro.catalog;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.*;
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
    private String lastLinkQuery;
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
            if(q.contains("action=create_link")){lastLinkQuery=q;links.incrementAndGet();if(rejectOnce){rejectOnce=false;reply(e,403,"{}");}else reply(e,200,"{\"js\":{\"cmd\":\"ffmpeg "+base+"/media.mp4\"}}");return;}
            if(q.contains("get_categories")||q.contains("get_genres")){reply(e,200,"{\"js\":[{\"id\":\"1\",\"title\":\"Provider category\"}]}");return;}
            if(q.contains("movie_id=series-fixture")){reply(e,200,"{\"js\":{\"data\":[{\"id\":\"season-2\",\"season\":2,\"name\":\"Season 2\",\"cmd\":\"ffmpeg /provider/season\",\"series\":[1,3]}]}}");return;}
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
    @Test public void numericPortalEpisodesUseRealEpisodeNumberAndDoNotAutoplay()throws Exception{
        StalkerCatalogSource source=new StalkerCatalogSource();Playlist p=playlist();
        SeriesDetails details=source.loadSeries(p,new Series("series-fixture","Provider series","1","","",""));
        assertEquals(2,details.seasons.get(0).number);assertEquals(2,details.episodes.get(2).size());assertEquals(0,links.get());
        Episode selected=details.episodes.get(2).get(1);assertEquals(3,selected.episodeNumber);
        source.resolve(p,selected);assertTrue(lastLinkQuery.contains("series=3"));assertTrue(lastLinkQuery.contains("cmd=ffmpeg"));
    }
    @Test public void screenTeardownAndLinkRefreshKeepValidProviderToken()throws Exception{
        Playlist p=playlist();Movie movie=new Movie("9","Movie","1","","ffmpeg /provider/ref","");
        CatalogEngine first=new CatalogEngine();first.resolveStream(p,movie);first.close();
        CatalogEngine next=new CatalogEngine();next.resolveStream(p,movie);next.refreshStream(p,movie);assertEquals(1,handshakes.get());
    }
    @Test public void metadataArraysContainOnlyProviderFacts()throws Exception{
        Map<String,String> m=ProviderMetadata.read(new org.json.JSONObject("{\"cast\":[{\"name\":\"Actor One\"},\"Actor Two\"],\"backdrop_path\":[\"https://example.com/a.jpg\",\"https://example.com/b.jpg\"]}"));
        assertEquals("Actor One, Actor Two",m.get("cast"));assertEquals("https://example.com/a.jpg",m.get("backdrop"));assertFalse(m.containsKey("year"));
    }
    // ServerSocket is available in both the Android compile API and the local JVM.
    private interface Handler { void handle(HttpExchange exchange)throws IOException; }
    private static final class Headers extends TreeMap<String,List<String>> {
        Headers(){super(String.CASE_INSENSITIVE_ORDER);}
        void add(String key,String value){computeIfAbsent(key,k->new ArrayList<>()).add(value);}
        String getFirst(String key){List<String> values=get(key);return values==null||values.isEmpty()?null:values.get(0);}
    }
    private static final class HttpExchange {
        private final Socket socket;
        private final URI uri;
        private final Headers request=new Headers(),response=new Headers();
        private final ByteArrayOutputStream body=new ByteArrayOutputStream();
        private int status=200;
        HttpExchange(Socket socket)throws IOException{
            this.socket=socket;BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
            String line=in.readLine();if(line==null)throw new EOFException();uri=URI.create(line.split(" ")[1]);
            while((line=in.readLine())!=null&&!line.isEmpty()){int colon=line.indexOf(':');if(colon>0)request.add(line.substring(0,colon),line.substring(colon+1).trim());}
        }
        URI getRequestURI(){return uri;}
        Headers getRequestHeaders(){return request;}
        Headers getResponseHeaders(){return response;}
        void sendResponseHeaders(int code,long length){status=code;}
        OutputStream getResponseBody(){return body;}
        void close()throws IOException{
            StringBuilder head=new StringBuilder("HTTP/1.1 ").append(status).append(" Fixture\r\nContent-Length: ").append(body.size()).append("\r\nConnection: close\r\n");
            for(Map.Entry<String,List<String>> e:response.entrySet())for(String value:e.getValue())head.append(e.getKey()).append(": ").append(value).append("\r\n");
            head.append("\r\n");OutputStream out=socket.getOutputStream();out.write(head.toString().getBytes(StandardCharsets.US_ASCII));body.writeTo(out);out.flush();socket.close();
        }
    }
    private static final class HttpServer {
        private final ServerSocket socket;
        private final Map<String,Handler> handlers=new ConcurrentHashMap<>();
        private final ExecutorService worker=Executors.newSingleThreadExecutor();
        private HttpServer(InetSocketAddress address)throws IOException{socket=new ServerSocket();socket.bind(address);}
        static HttpServer create(InetSocketAddress address,int backlog)throws IOException{return new HttpServer(address);}
        InetSocketAddress getAddress(){return (InetSocketAddress)socket.getLocalSocketAddress();}
        void createContext(String path,Handler handler){handlers.put(path,handler);}
        void start(){worker.execute(()->{while(!socket.isClosed())try(Socket client=socket.accept()){
            HttpExchange e=new HttpExchange(client);Handler h=handlers.get(e.getRequestURI().getPath());
            if(h==null)reply(e,404,"{}");else h.handle(e);
        }catch(IOException ignored){}});}
        void stop(int delay){try{socket.close();}catch(IOException ignored){}worker.shutdownNow();}
    }

}
