package de.lazytv.pro.catalog;
import java.util.*;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={25,28,30},application=android.app.Application.class)
public class ManagedLiveEpgTest {
 @Test public void managedEntryWithNoPersonalPlaylistUsesProviderEpg()throws Exception{
  MockWebServer server=new MockWebServer();
  java.util.concurrent.atomic.AtomicReference<String> epgQuery=new java.util.concurrent.atomic.AtomicReference<>();
  server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){String q=request.getPath();if(q==null)q="";
   String body=q.contains("action=handshake")?"{\"js\":{\"token\":\"fixture\"}}":q.contains("get_epg_info")?"{\"js\":[{\"title\":\"Programi real\"}]}":"{\"js\":{}}";
   if(q.contains("get_epg_info"))epgQuery.set(q);
   return new MockResponse().setBody(body);
  }});server.start();
  try{
   Map<String,String> meta=new HashMap<>();meta.put("portal_url","http://127.0.0.1:"+server.getPort());meta.put("mac","00:11:22:33:44:55");meta.put("stalker_original_cmd","ffmpeg http://localhost/ch/42_");
   StreamItem item=new StreamItem("SERVER_10|42","Fixture","1","","http://fixture.invalid/live.ts","",CatalogType.LIVE,Collections.emptyList(),meta);
   EpgSchedule schedule=new CatalogEngine().loadLiveSchedule(null,item);
   assertNotNull(schedule.current);assertEquals("Programi real",schedule.current.title);assertTrue(epgQuery.get().contains("ch_id=42"));
  }finally{server.shutdown();}
 }
 @Test public void suppliedNowNextDoesNotDisappearForManagedSource()throws Exception{
  StreamItem item=new StreamItem("x","x","1","","http://fixture.invalid/live.ts","",CatalogType.LIVE,Collections.emptyList(),Map.of("epg_now","Lajmet","epg_next","Sport"));
  EpgSchedule s=new CatalogEngine().loadLiveSchedule(null,item);assertEquals("Lajmet",s.current.title);assertEquals("Sport",s.next.title);
 }
 @Test public void gzipXmltvIsParsedWithoutContentEncodingHeader()throws Exception{
  String xml="<tv><programme channel='one' start='20261024090000 +0000' stop='20261024100000 +0000'><title>Programi</title></programme></tv>";
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();try(var gzip=new java.util.zip.GZIPOutputStream(bytes)){gzip.write(xml.getBytes(StandardCharsets.UTF_8));}
  Map<String,EpgSchedule> guide=M3uXmltv.parse(new java.io.ByteArrayInputStream(bytes.toByteArray()),java.time.Instant.parse("2026-10-24T09:30:00Z").toEpochMilli());assertEquals("Programi",guide.get("one").current.title);
 }
}
