package de.lazytv.pro.stb;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class StbEpgTest {
 private JSONObject program(String title,long start,long end)throws Exception{return new JSONObject().put("name",title).put("start_timestamp",start).put("stop_timestamp",end);}
 @Test public void nestedChannelScheduleAndProgress()throws Exception{
  JSONArray rows=new JSONArray().put(program("Current",1700000000L,1700003600L)).put(program("Next",1700003600L,1700007200L));
  Object payload=new JSONObject().put("data",new JSONObject().put("42",rows));
  List<StbEpg.Program> p=StbEpg.parse(payload,"42");
  assertEquals(2,p.size());assertEquals("Current",StbEpg.current(p,1700001800000L).title);
  assertEquals(50,p.get(0).progress(1700001800000L));assertEquals("Next",StbEpg.next(p,1700001800000L).title);
  assertTrue(StbEpg.parse(payload,"43").isEmpty());
 }
 @Test public void listingsAndMilliseconds()throws Exception{
  JSONObject row=program("Actual",1700000000000L,1700003600000L).put("channel_id","42");
  assertEquals(1,StbEpg.parse(new JSONObject().put("epg_listings",new JSONArray().put(row)),"42").size());
  assertTrue(StbEpg.parse(new JSONArray().put(row),"43").isEmpty());
 }
 @Test public void missingTimesNeverCreatePrograms()throws Exception{
  assertTrue(StbEpg.parse(new JSONArray().put(new JSONObject().put("name","Missing times")),"42").isEmpty());
  assertNull(StbEpg.current(Collections.emptyList(),1700000000000L));
 }
 @Test public void utcDatesAndExplicitDuration()throws Exception{
  JSONObject row=new JSONObject().put("title","UTC").put("start","2023-11-14 22:13:20").put("duration",3600);
  assertEquals(1700000000000L,StbEpg.parse(new JSONArray().put(row),"42").get(0).start);
 }
}
