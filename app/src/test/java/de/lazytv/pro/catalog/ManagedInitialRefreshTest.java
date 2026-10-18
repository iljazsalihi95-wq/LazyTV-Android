package de.lazytv.pro.catalog;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ManagedInitialRefreshTest {
 @Test public void restoresBuild764NumberedTokenRefreshWithoutChangingLazyOrPremium(){
  for(String server:new String[]{"SERVER_05","SERVER_06","SERVER_09","SERVER_20"})assertTrue(ManagedBackendCatalogSource.refreshBeforeStart(server,"https://fixture.invalid/play?play_token=old"));
  for(String server:new String[]{"LAZI IPTV","Krystal","MASTER","SERVER_10"})assertFalse(ManagedBackendCatalogSource.refreshBeforeStart(server,"https://fixture.invalid/play?play_token=old"));
  assertFalse(ManagedBackendCatalogSource.refreshBeforeStart("SERVER_09","https://fixture.invalid/live.ts"));
 }
 @Test public void sameTitleCannotReplaceTheSelectedId(){
  StreamItem selected=item("SERVER_09|17","17"),wrong=item("18","18"),right=item("17","17");
  Catalog c=new Catalog();c.items.add(wrong);c.items.add(right);
  assertSame(right,ManagedBackendCatalogSource.matchNumberedSelection(c,selected));
  c.items.remove(right);assertNull(ManagedBackendCatalogSource.matchNumberedSelection(c,selected));
 }
 @Test public void sameIdInDifferentMediaTypeIsNotSelected(){
  Catalog c=new Catalog();c.items.add(new StreamItem("17","Same title","cat","","https://fixture.invalid/movie.mp4","",CatalogType.MOVIES));
  assertNull(ManagedBackendCatalogSource.matchNumberedSelection(c,item("SERVER_09|17","17")));
 }
 private static StreamItem item(String id,String source){Map<String,String> m=new HashMap<>();m.put("managed_source_id",source);return new StreamItem(id,"Same title","cat","","https://fixture.invalid/play?token=fixture","",CatalogType.LIVE,Collections.emptyList(),m);}
}
