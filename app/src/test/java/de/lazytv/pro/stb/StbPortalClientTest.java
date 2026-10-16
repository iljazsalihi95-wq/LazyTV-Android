package de.lazytv.pro.stb;
import org.junit.*;import static org.junit.Assert.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class StbPortalClientTest {
 private HttpServer server;private String base;private AtomicInteger handshakes=new AtomicInteger(),links=new AtomicInteger();private boolean refuse;
 @Before public void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+server.getAddress().getPort();server.createContext("/portal.php",e->{String q=e.getRequestURI().getQuery();if(q.contains("action=handshake")){handshakes.incrementAndGet();e.getResponseHeaders().add("Set-Cookie","session=fixture; Path=/");reply(e,200,"{\"js\":{\"token\":\"fixture\"}}");return;}if(!"Bearer fixture".equals(e.getRequestHeaders().getFirst("Authorization"))||!e.getRequestHeaders().getFirst("Cookie").contains("session=fixture")){reply(e,403,"{}");return;}if(q.contains("action=create_link")){int count=links.incrementAndGet();if(refuse||count==1){reply(e,403,"{}");return;}e.getResponseHeaders().add("Set-Cookie","play=fresh; Path=/");reply(e,200,"{\"js\":{\"cmd\":\"ffmpeg "+base+"/media?fresh="+count+"\"}}");return;}if(q.contains("action=get_genres")){reply(e,200,"{\"js\":[{\"id\":\"1\",\"title\":\"DE SPORT\"}]}");return;}reply(e,200,"{\"js\":{\"id\":1}}");});server.start();}
 @After public void stop(){server.stop(0);}
 private StbProtocol.Channel channel()throws Exception{return new StbProtocol.Channel(new org.json.JSONObject().put("id","7").put("cmd","ffmpeg http://localhost/ch/7"));}
 @Test public void freshLinksAndOneAuthRetry()throws Exception{try(StbPortalClient c=new StbPortalClient(base,"00:11:22:33:44:55")){assertEquals(1,c.genres().size());String a=c.resolve(channel()),b=c.resolve(channel());assertNotEquals(a,b);assertEquals(2,handshakes.get());assertEquals(3,links.get());assertTrue(c.playbackHeaders(b).get("Cookie").contains("play=fresh"));assertFalse(c.playbackHeaders("https://other.test/live").containsKey("Authorization"));}}
 @Test public void authRetryStops()throws Exception{refuse=true;try(StbPortalClient c=new StbPortalClient(base,"00:11:22:33:44:55")){try{c.resolve(channel());fail();}catch(IOException expected){}assertEquals(2,links.get());assertEquals(2,handshakes.get());}}
 private static void reply(HttpExchange e,int status,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);e.close();}
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
