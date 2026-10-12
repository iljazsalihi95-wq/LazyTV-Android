package de.lazytv.pro.catalog;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;
import de.lazytv.pro.playlist.*;
public class XtreamCatalogRecoveryTest {
 @Test public void unsupportedVodAndSeriesCannotDiscardWorkingLive()throws Exception {check(false);}
 @Test public void api404FallsBackToDirectM3uWithoutRecursion()throws Exception {check(true);}
 private void check(boolean m3uOnly)throws Exception {
  try(ServerSocket server=new ServerSocket(0)) {
   ExecutorService worker=Executors.newSingleThreadExecutor();
   worker.execute(()->{while(!server.isClosed())try(Socket client=server.accept()){
    BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));String request=in.readLine(),line;
    while((line=in.readLine())!=null&&!line.isEmpty()){}
    int status=200;String body;
    if(m3uOnly&&request.contains("/player_api.php")){status=404;body="{}";}
    else if(request.contains("/get.php"))body="#EXTM3U\n#EXTINF:-1 group-title=\"AL News\",Fixture\nhttp://127.0.0.1/live/9.ts\n";
    else if(request.contains("action=get_live_categories"))body="[{\"category_id\":\"1\",\"category_name\":\"DE News\"}]";
    else if(request.contains("action=get_live_streams"))body="[{\"stream_id\":\"9\",\"name\":\"Fixture\",\"category_id\":\"1\"}]";
    else if(request.contains("action=")){status=404;body="{}";}
    else body="{\"user_info\":{\"auth\":1}}";
    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);OutputStream out=client.getOutputStream();
    out.write(("HTTP/1.1 "+status+" Fixture\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
   }catch(IOException ignored){}});
   try {
    Playlist p=new Playlist("test","Fixture",PlaylistType.XTREAM_CODES,"http://127.0.0.1:"+server.getLocalPort()+"/player_api.php?ignored=1","fixture","p$&+","",1);
    Catalog catalog=new XtreamCatalogSource().load(p);assertEquals(1,catalog.items.size());assertEquals(CatalogType.LIVE,catalog.items.get(0).type);if(m3uOnly)assertTrue(catalog.items.get(0) instanceof M3uStreamItem);
   }finally{server.close();worker.shutdownNow();}
  }
 }
}
