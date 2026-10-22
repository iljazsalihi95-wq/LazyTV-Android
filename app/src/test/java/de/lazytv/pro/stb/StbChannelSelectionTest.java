package de.lazytv.pro.stb;
import org.junit.Test;import org.json.*;import java.util.*;import static org.junit.Assert.*;
public class StbChannelSelectionTest {
 @Test public void wrapsAndUsesStableId()throws Exception{List<StbProtocol.Channel> rows=Arrays.asList(new StbProtocol.Channel(new JSONObject().put("id","a")),new StbProtocol.Channel(new JSONObject().put("id","b")));assertEquals(0,StbChannelSelection.adjacent(rows,"b",1));assertEquals(1,StbChannelSelection.adjacent(rows,"a",-1));assertEquals(-1,StbChannelSelection.adjacent(Collections.emptyList(),"a",1));assertEquals(0,StbChannelSelection.adjacent(rows,"unknown",1));}
 @Test public void filteredNavigationNeverSelectsOutsideCategory()throws Exception{
  StbProtocol.Channel a=new StbProtocol.Channel(new JSONObject().put("id","a"));
  StbProtocol.Channel c=new StbProtocol.Channel(new JSONObject().put("id","c"));
  List<StbProtocol.Channel> filtered=Arrays.asList(a,c);
  assertEquals(1,StbChannelSelection.adjacent(filtered,"a",1));
  assertEquals(0,StbChannelSelection.adjacent(filtered,"c",1));
  assertEquals(1,StbChannelSelection.adjacent(filtered,"a",-1));
  assertEquals(0,StbChannelSelection.adjacent(filtered,"outside",1));
  assertEquals(1,StbChannelSelection.adjacent(filtered,"outside",-1));
 }
 @Test public void emptyFilterDoesNotSelectAndSingleChannelWraps()throws Exception{
  assertEquals(-1,StbChannelSelection.adjacent(Collections.emptyList(),"active",-1));
  List<StbProtocol.Channel> one=Collections.singletonList(new StbProtocol.Channel(new JSONObject().put("id","only")));
  assertEquals(0,StbChannelSelection.adjacent(one,"only",1));
  assertEquals(0,StbChannelSelection.adjacent(one,"only",-1));
 }
}
