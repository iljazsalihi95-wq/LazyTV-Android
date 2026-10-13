package de.lazytv.pro.catalog;
import org.junit.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import static org.junit.Assert.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.atomic.AtomicInteger;import de.lazytv.pro.playlist.*;
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
 private static void reply(HttpExchange exchange,int status,String body)throws java.io.IOException {byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);try(java.io.OutputStream out=exchange.getResponseBody()){out.write(bytes);}exchange.close();}
 @Test public void profileAndRotatingPlaybackCookiesStayInSameSession()throws Exception {Playlist playlist=new Playlist("fixture","Fixture",PlaylistType.STALKER_PORTAL,base+"/tenant/c/","","","00:11:22:33:44:55",0);StalkerNativePortalClient client=new StalkerNativePortalClient();StreamItem channel=new StreamItem("7","Channel","genre","","ffmpeg http://localhost/ch/7_","",CatalogType.LIVE);StalkerCatalogSource.LinkResult first=client.resolve(playlist,channel);assertTrue(first.headers.get("Cookie").contains("session=play1"));StalkerCatalogSource.LinkResult second=client.resolve(playlist,channel);assertTrue(second.url.contains("fresh2"));assertTrue(second.headers.get("Cookie").contains("session=play2"));assertEquals(1,handshakes.get());assertEquals(2,links.get());assertEquals("ffmpeg http://localhost/ch/7_",channel.streamUrl);}
 @Test public void bareHostAndPortAreNormalizedBeforeHandshake()throws Exception {Playlist playlist=new Playlist("fixture","Fixture",PlaylistType.STALKER_PORTAL,base.substring(7)+"/tenant/c/","","","00:11:22:33:44:55",0);assertFalse(new StalkerNativePortalClient().sessionHeaders(playlist).isEmpty());assertEquals(1,handshakes.get());}
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
