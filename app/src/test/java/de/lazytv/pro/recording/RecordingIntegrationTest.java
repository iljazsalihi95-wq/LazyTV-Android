package de.lazytv.pro.recording;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import de.lazytv.pro.catalog.ResolvedStream;
import com.sun.net.httpserver.HttpServer;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={25,28,30},application=android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecordingIntegrationTest {
 @Test public void recordWritesRealTransportPacketsAfterRedirect()throws Exception{
  HttpServer server=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  byte[] packet=new byte[188];packet[0]=0x47;packet[3]=0x10;
  java.util.concurrent.atomic.AtomicReference<String> cookie=new java.util.concurrent.atomic.AtomicReference<>();
  server.createContext("/start.ts",x->{x.getResponseHeaders().add("Location","/content.ts");x.sendResponseHeaders(302,-1);x.close();});
  server.createContext("/content.ts",x->{cookie.set(x.getRequestHeaders().getFirst("Cookie"));x.getResponseHeaders().add("Content-Type","video/mp2t");x.sendResponseHeaders(200,packet.length);x.getResponseBody().write(packet);x.close();});server.start();
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  RecordingController recorder=new RecordingController(activity,t->{});
  Path target=Files.createTempFile("lazytv-recording-",".ts");
  try{
   ResolvedStream stream=new ResolvedStream("http://127.0.0.1:"+server.getAddress().getPort()+"/start.ts","ts","Fixture","","",Map.of("Cookie","fixture=one"),null);
   recorder.toggle(stream);
   assertTrue(recorder.result(RecordingController.REQUEST,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(target.toFile()))));
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(Files.size(target)<188&&System.nanoTime()<deadline){Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();}
   assertArrayEquals(packet,Files.readAllBytes(target));assertEquals("fixture=one",cookie.get());
  }finally{recorder.close();activity.finish();server.stop(0);Files.deleteIfExists(target);}
 }
}
