package de.lazytv.pro.catalog;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class ManagedServerRegistryTest {
 @Test public void keepsOwnerApiNamesForChannelRequests()throws Exception{
  List<String> ids=new ArrayList<>();Map<String,String> labels=new LinkedHashMap<>();
  ManagedBackendCatalogSource.appendApiServers("{\"ok\":true,\"servers\":[{\"id\":\"krystal\",\"name\":\"Krystal\"},{\"id\":\"lazi_iptv\",\"name\":\"LAZI IPTV\"},{\"name\":\"MASTER\"},{\"name\":\"SARAY DARK\"}]}",ids,labels);
  assertEquals(Arrays.asList("Krystal","LAZI IPTV","MASTER","SARAY DARK"),ids);assertEquals("LAZI IPTV",labels.get("LAZI IPTV"));
 }
 @Test public void disabledAndDuplicateEntriesDoNotCreateDeadMenuRows()throws Exception{
  List<String> ids=new ArrayList<>();Map<String,String> labels=new HashMap<>();ManagedBackendCatalogSource.appendApiServers("{\"ok\":true,\"servers\":[\"MASTER\",\"MASTER\",{\"name\":\"Disabled\",\"active\":false}]}",ids,labels);assertEquals(Collections.singletonList("MASTER"),ids);
 }
}
