package de.lazytv.pro.recording;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import de.lazytv.pro.catalog.ResolvedStream;
import okhttp3.mockwebserver.*;
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
  MockWebServer server=new MockWebServer();
  byte[] packet=new byte[188];packet[0]=0x47;packet[3]=0x10;
  server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location","/content.ts"));
  server.enqueue(new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(packet)));server.start();
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  RecordingController recorder=new RecordingController(activity,t->{});
  Path target=Files.createTempFile("lazytv-recording-",".ts");
  try{
   ResolvedStream stream=new ResolvedStream("http://127.0.0.1:"+server.getPort()+"/start.ts","ts","Fixture","","",Map.of("Cookie","fixture=one"),null);
   recorder.toggle(stream);
   assertTrue(recorder.result(RecordingController.REQUEST,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(target.toFile()))));
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(Files.size(target)<188&&System.nanoTime()<deadline){Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();}
   assertArrayEquals(packet,Files.readAllBytes(target));server.takeRequest(1,TimeUnit.SECONDS);assertEquals("fixture=one",server.takeRequest(1,TimeUnit.SECONDS).getHeader("Cookie"));
  }finally{recorder.close();activity.finish();server.shutdown();Files.deleteIfExists(target);}
 }
}
