package de.lazytv.pro.catalog;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class DirectPlaybackRequestTest {
 @Test public void preservesSignedQueryAndProviderHeaders(){
  String signed="https://provider.invalid/live?play_token=a%2Bb%3D&stream=9";
  Map<String,String> h=new LinkedHashMap<>();h.put("user-agent","Provider Agent");h.put("Cookie","session=fixture");
  StreamItem item=new M3uStreamItem("9","Fixture","c","",signed+"|Referer=https%3A%2F%2Fprovider.invalid%2F&Origin=https%3A%2F%2Fprovider.invalid","",CatalogType.LIVE,h,Collections.emptyMap());
  ResolvedStream r=DirectPlaybackRequest.resolve(item,item.streamUrl,"LIVE");assertEquals(signed,r.url);assertEquals("Provider Agent",r.headers.get("user-agent"));assertFalse(r.headers.containsKey("User-Agent"));assertEquals("session=fixture",r.headers.get("Cookie"));assertEquals("https://provider.invalid/",r.headers.get("Referer"));assertEquals("https://provider.invalid",r.headers.get("Origin"));
 }
 @Test public void refusesHeaderInjection(){StreamItem i=new StreamItem("x","x","","","https://provider.invalid/x|Cookie=a%0D%0AX-Evil%3Ayes","",CatalogType.LIVE);ResolvedStream r=DirectPlaybackRequest.resolve(i,i.streamUrl,"LIVE");assertFalse(r.headers.containsKey("Cookie"));assertTrue(r.headers.containsKey("User-Agent"));}
}
