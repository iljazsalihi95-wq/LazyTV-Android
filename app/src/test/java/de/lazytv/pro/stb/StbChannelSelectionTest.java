package de.lazytv.pro.stb;
import org.junit.Test;import org.json.*;import java.util.*;import static org.junit.Assert.*;
public class StbChannelSelectionTest {
 @Test public void wrapsAndUsesStableId()throws Exception{List<StbProtocol.Channel> rows=Arrays.asList(new StbProtocol.Channel(new JSONObject().put("id","a")),new StbProtocol.Channel(new JSONObject().put("id","b")));assertEquals(0,StbChannelSelection.adjacent(rows,"b",1));assertEquals(1,StbChannelSelection.adjacent(rows,"a",-1));assertEquals(-1,StbChannelSelection.adjacent(Collections.emptyList(),"a",1));assertEquals(0,StbChannelSelection.adjacent(rows,"unknown",1));}
}
