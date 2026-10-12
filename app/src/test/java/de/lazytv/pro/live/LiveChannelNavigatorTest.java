package de.lazytv.pro.live;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;import de.lazytv.pro.catalog.*;
public class LiveChannelNavigatorTest {
 private List<StreamItem> channels(){return Arrays.asList(new Channel("a","A","1","","","") ,new Channel("b","B","1","","",""),new Channel("c","C","1","","",""));}
 @Test public void nextAndPreviousUseStableIdsAndWrap(){assertEquals("b",LiveChannelNavigator.adjacent(channels(),"a",1).id);assertEquals("c",LiveChannelNavigator.adjacent(channels(),"a",-1).id);assertEquals("a",LiveChannelNavigator.adjacent(channels(),"c",1).id);}
 @Test public void filteredOrEmptyListsCannotReuseStaleIndex(){assertEquals("a",LiveChannelNavigator.adjacent(channels(),"missing",1).id);assertEquals("c",LiveChannelNavigator.adjacent(channels(),"missing",-1).id);assertNull(LiveChannelNavigator.adjacent(Collections.emptyList(),"a",1));}
}
