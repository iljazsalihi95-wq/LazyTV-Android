package de.lazytv.pro.catalog;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ProviderPlaybackPolicyTest {
    private ResolvedStream stream(Map<String,String> headers){return new ResolvedStream("http://local/stream","LIVE","Channel","","",headers,Collections.emptyMap());}
    private Map<String,String> defaults(){Map<String,String> h=new LinkedHashMap<>();h.put("User-Agent","Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");h.put("Referer","http://provider/");h.put("Cookie","session=fixture");return h;}
    @Test public void nestedHttpStatusRemainsVisible(){assertEquals(402,ProviderPlaybackPolicy.status(new RuntimeException(new ProviderHttpException(402))));assertTrue(ProviderPlaybackPolicy.message(409).contains("409"));}
    @Test public void paymentDoesNotChangeClientOrStream(){ResolvedStream original=stream(defaults());assertSame(original,ProviderPlaybackPolicy.retryHeaders(original,null,402,1));}
    @Test public void alternateDefaultHeadersAreBoundedAndPreserveProviderHeaders(){ResolvedStream original=stream(defaults());ResolvedStream retry=ProviderPlaybackPolicy.retryHeaders(original,null,409,1);assertEquals(original.url,retry.url);assertEquals("IPTVSmartersPro",retry.headers.get("User-Agent"));assertEquals(original.headers.get("Cookie"),retry.headers.get("Cookie"));assertEquals(original.headers.get("Referer"),retry.headers.get("Referer"));assertSame(original,ProviderPlaybackPolicy.retryHeaders(original,null,409,2));}
    @Test public void portalAndExplicitM3uHeadersRemainIntact(){Map<String,String> h=defaults();h.put("X-User-Agent","Model: MAG254; Link: Ethernet");ResolvedStream portal=stream(h);assertSame(portal,ProviderPlaybackPolicy.retryHeaders(portal,null,403,1));Map<String,String> explicit=new HashMap<>();explicit.put("user-agent","Provider agent");M3uStreamItem item=new M3uStreamItem("1","Channel","1","","http://local/stream","",CatalogType.LIVE,explicit);ResolvedStream original=stream(defaults());assertSame(original,ProviderPlaybackPolicy.retryHeaders(original,item,403,1));}
}
