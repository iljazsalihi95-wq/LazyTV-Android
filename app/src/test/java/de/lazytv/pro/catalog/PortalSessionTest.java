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
    private boolean rejectOnce,rejectAlways,rejectBody,blocked,expiredToken,rotateLinks,rejectNumericOnce,rejectNumericAlways,profileNumericRefused;
    private String lastLinkQuery;
    @Before public void start()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/portal.php",e->reply(e,200,"<html>landing page</html>"));
        server.createContext("/server/load.php",e->{
            String q=e.getRequestURI().getQuery();
            if(q.contains("action=handshake")){int n=handshakes.incrementAndGet();e.getResponseHeaders().add("Set-Cookie","session=local"+n+"; Path=/");reply(e,200,"{\"js\":{\"token\":\"test"+n+"\""+(expiredToken?",\"expires_at\":1":"")+"}}");return;}
            String cookie=e.getRequestHeaders().getFirst("Cookie");
            if(cookie==null||!cookie.contains("session=local"+handshakes.get())||!cookie.contains("mac=")){reply(e,403,"{}");return;}
            if(q.contains("action=get_main_info")){reply(e,200,blocked?"{\"js\":{\"status\":\"expired\"}}":"{\"js\":{\"status\":\"active\"}}");return;}
            if(q.contains("action=get_profile")){reply(e,200,profileNumericRefused?"{\"js\":{\"status\":401}}":"{\"js\":{\"id\":1}}");return;}
            if(q.contains("action=create_link")){lastLinkQuery=q;links.incrementAndGet();if(rejectNumericOnce||rejectNumericAlways){rejectNumericOnce=false;reply(e,200,"{\"js\":{\"status\":401}}");return;}if(rejectOnce||rejectAlways||rejectBody){rejectOnce=false;if(rejectBody)reply(e,200,"{\"js\":{\"error\":\"invalid token\"}}");else reply(e,403,"{}");}else reply(e,200,"{\"js\":{\"cmd\":\"ffmpeg "+base+"/media.mp4"+(rotateLinks?"?play_token=fixture"+links.get():"")+"\"}}");return;}
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
    @Test public void forbiddenHttpRetryIsBoundedAndRespectsExplicitUserAgent()throws Exception{
        AtomicInteger count=new AtomicInteger();server.createContext("/forbidden",e->{count.incrementAndGet();reply(e,403,"{}");});
        try{HttpClient.get(base+"/forbidden",null);fail();}catch(ProviderHttpException e){assertEquals(403,e.status);}assertEquals(2,count.get());
        count.set(0);Map<String,String> h=new HashMap<>();h.put("User-Agent","Provider agent");
        try{HttpClient.get(base+"/forbidden",h);fail();}catch(ProviderHttpException e){assertEquals(403,e.status);}assertEquals(1,count.get());
    }
    @Test public void opaqueTokenHasNoGuessedTwentyMinuteExpiry()throws Exception{
        StalkerCatalogSource source=new StalkerCatalogSource();Playlist p=playlist();source.loadType(p,CatalogType.LIVE);
        java.lang.reflect.Field clock=StalkerCatalogSource.class.getDeclaredField("sessionAt");clock.setAccessible(true);clock.setLong(source,1);
        source.loadType(p,CatalogType.MOVIES);assertEquals(1,handshakes.get());
    }
    @Test public void explicitProviderExpiryTriggersRefresh()throws Exception{
        expiredToken=true;StalkerCatalogSource source=new StalkerCatalogSource();Playlist p=playlist();source.loadType(p,CatalogType.LIVE);
        expiredToken=false;source.loadType(p,CatalogType.MOVIES);assertEquals(2,handshakes.get());
    }
    @Test public void liveSelectionAlwaysUsesOriginalCmdAndFreshLink()throws Exception {
        rotateLinks=true;Playlist p=playlist();StalkerCatalogSource source=new StalkerCatalogSource();
        StreamItem channel=source.load(p).items.get(0);String original=channel.streamUrl;
        String first=source.resolve(p,channel).url,second=source.resolve(p,channel).url;
        assertNotEquals(first,second);assertEquals(original,channel.streamUrl);
        assertTrue(URLDecoder.decode(lastLinkQuery,"UTF-8").contains("cmd=ffmpeg /provider/ref"));assertEquals(1,handshakes.get());assertEquals(2,links.get());
    }
    @Test public void repeatedAuthRefusalStopsAfterOneSessionRefresh()throws Exception {
        Playlist p=playlist();StalkerCatalogSource source=new StalkerCatalogSource();source.load(p);rejectAlways=true;
        try{source.resolve(p,new Channel("9","Channel","1","","original cmd",""));fail();}
        catch(ProviderHttpException expected){assertEquals(403,expected.status);}
        assertEquals(2,handshakes.get());assertEquals(2,links.get());
    }
    @Test public void repeatedBodyTokenExpiryAlsoStopsAfterOneRefresh()throws Exception {
        Playlist p=playlist();StalkerCatalogSource source=new StalkerCatalogSource();source.load(p);rejectBody=true;
        try{source.resolve(p,new Channel("9","Channel","1","","original cmd",""));fail();}
        catch(CatalogException expected){assertTrue(expected.getMessage().contains("unauthorized"));}
        assertEquals(2,handshakes.get());assertEquals(2,links.get());
    }
    @Test public void signedAndRelativeCreateLinksArePreserved()throws Exception {
        assertEquals("https://provider.invalid/play?a=1%2F2&token=x+y",StalkerLinkParser.url("ffmpeg \"https://provider.invalid/play?a=1%2F2&token=x+y\"",base+"/server/load.php"));
        assertEquals(base+"/media.ts?x=1",StalkerLinkParser.url("auto /media.ts?x=1",base+"/server/load.php"));
    }
    @Test public void stalkerTransportHeadersAreRemovedButEncodedPipeIsPreserved()throws Exception {
        assertEquals("https://provider.invalid/live.ts?token=a%7Cb",StalkerLinkParser.url("ffmpeg https://provider.invalid/live.ts?token=a%7Cb|User-Agent=MAG254&Referer=https://portal.invalid/",base+"/server/load.php"));
        assertEquals(base+"/media.ts?x=1",StalkerLinkParser.url("ffrt /media.ts?x=1|User-Agent=MAG254",base+"/server/load.php"));
    }
    @Test public void premiumNumericTokenExpiryRefreshesThroughServerTenResolver()throws Exception {
        rotateLinks=true;rejectNumericOnce=true;
        StreamItem row=new StreamItem("sheet-stable-id","Premium","cat","",base+"/play/live.php?mac=00%3A11%3A22%3A33%3A44%3A55&stream=9&play_token=expired","",CatalogType.LIVE);
        ResolvedStream fresh=ServerTenPlaybackResolver.resolve(row);
        assertEquals(2,handshakes.get());assertEquals(2,links.get());assertTrue(fresh.url.contains("play_token=fixture2"));assertTrue(fresh.headers.get("Authorization").endsWith("test2"));assertTrue(fresh.headers.get("Cookie").contains("session=local2"));assertEquals("true",fresh.metadata.get("server10_fresh_link"));assertTrue(row.streamUrl.contains("play_token=expired"));
    }
    @Test public void premiumNumericRefusalStopsWithoutReturningOldSheetToken()throws Exception {
        rejectNumericAlways=true;StreamItem row=new StreamItem("stable","Premium","cat","",base+"/play/live.php?mac=00:11:22:33:44:55&stream=9&play_token=expired","",CatalogType.LIVE);
        try{ServerTenPlaybackResolver.resolve(row);fail("Old sheet token must not be returned on refusal");}catch(CatalogException expected){assertTrue(expected.getMessage().contains("unauthorized"));}
        assertEquals(2,handshakes.get());assertEquals(2,links.get());
    }
    @Test public void refusedProfileNeverStartsCreateLink()throws Exception {profileNumericRefused=true;try{new StalkerCatalogSource().resolve(playlist(),new Channel("9","Fixture","1","","original cmd",""));fail("Refused profile accepted");}catch(CatalogException expected){assertTrue(expected.getMessage().contains("unauthorized"));}assertEquals(1,handshakes.get());assertEquals(0,links.get());}
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
        private final Map<String,Handler> contexts=new ConcurrentHashMap<>();
        private volatile boolean running;
        private Thread accept;
        private HttpServer(ServerSocket socket){this.socket=socket;}
        static HttpServer create(InetSocketAddress bind,int backlog)throws IOException{ServerSocket socket=new ServerSocket();socket.bind(bind,backlog<=0?50:backlog);return new HttpServer(socket);}
        InetSocketAddress getAddress(){return (InetSocketAddress)socket.getLocalSocketAddress();}
        void createContext(String path,Handler handler){contexts.put(path,handler);}
        void start(){running=true;accept=new Thread(()->{while(running)try{Socket client=socket.accept();new Thread(()->{try{HttpExchange e=new HttpExchange(client);Handler h=contexts.get(e.getRequestURI().getPath());if(h==null)reply(e,404,"{}");else h.handle(e);}catch(Exception ignored){try{client.close();}catch(IOException ignored2){}}}).start();}catch(IOException e){if(running)throw new RuntimeException(e);}},"fixture-http");accept.setDaemon(true);accept.start();}
        void stop(int delay){running=false;try{socket.close();}catch(IOException ignored){}if(accept!=null)try{accept.join(1000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
    }
}
