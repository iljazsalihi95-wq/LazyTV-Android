package de.lazytv.pro.catalog;
import org.json.*;import java.net.*;import java.util.*;
import de.lazytv.pro.playlist.Playlist;
/** Native provider client. Managed Server 10 keeps its existing StalkerCatalogSource. */
public class StalkerNativePortalClient extends StalkerCatalogSource {
 private final CookieManager linkCookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
 private String selectedEndpoint="";
 @Override public synchronized void clear(){super.clear();linkCookies.getCookieStore().removeAll();selectedEndpoint="";}
 @Override public synchronized LinkResult resolve(Playlist playlist,StreamItem item)throws CatalogException {
  if(item.type!=CatalogType.LIVE)return super.resolve(playlist,item);
  String cmd=item.meta("stalker_original_cmd");if(cmd.isEmpty())cmd=item.streamUrl;
  if(cmd==null||cmd.trim().isEmpty())throw new CatalogException("Stalker CMD mungon");
  String query="type=itv&action=create_link&cmd="+HttpClient.enc(cmd)+"&series=0&forced_storage=undefined&disable_ad=0&download=0&JsHttpRequest=1-xml";
  for(int attempt=0;attempt<2;attempt++) {
   Map<String,String> headers=new LinkedHashMap<>(sessionHeaders(playlist));
   try {
    JSONObject result=payload(requestLink(playlist,query,headers));int status=refusal(result);
    if(status!=0)throw new ProviderHttpException(status);
    String command=StalkerLiveParser.first(result,"cmd","url");
    // JSON escaping is decoded by JSONObject; handle providers which additionally HTML-escape URLs.
    command=command.replace("\\/","/").replace("&amp;","&");
    String endpoint=selectedEndpoint.isEmpty()?StalkerPortalAddress.normalize(playlist.getUrl()):selectedEndpoint;
    String url=StalkerLinkParser.url(command,endpoint);if(url.isEmpty())throw new CatalogException("Portali nuk ktheu URL për kanalin");
    headers.put("User-Agent","Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG254 stbapp");
    try{headers.put("Cookie",HttpClient.cookieHeader(linkCookies,new URL(endpoint),headers.get("Cookie")));}catch(Exception ignored){}
    JSONObject supplied=result.optJSONObject("headers");if(supplied==null)supplied=result.optJSONObject("http_headers");
    if(supplied!=null){Iterator<String> keys=supplied.keys();while(keys.hasNext()){String key=keys.next(),value=supplied.optString(key);if(key.matches("[A-Za-z0-9-]+")&&!value.contains("\r")&&!value.contains("\n"))headers.put(key,value);}}
    return new LinkResult(url,headers);
   }catch(ProviderHttpException error){if(attempt==0&&(error.status==401||error.status==403)){clear();continue;}throw error;}
  }
  throw new CatalogException("Stalker session-i u refuzua pas rifreskimit");
 }
 protected String requestLink(Playlist playlist,String query,Map<String,String> headers)throws CatalogException {
  LinkedHashSet<String> endpoints=new LinkedHashSet<>();if(!selectedEndpoint.isEmpty())endpoints.add(selectedEndpoint);endpoints.addAll(StalkerPortalAddress.endpoints(playlist.getUrl()));
  CatalogException last=null;
  for(String endpoint:endpoints)try {
   String body=HttpClient.get(endpoint+"?"+query,headers,linkCookies);payload(body);selectedEndpoint=endpoint;return body;
  }catch(ProviderHttpException error){if(error.status!=404&&error.status!=405)throw error;last=error;}
   catch(CatalogException invalid){last=invalid;}
  throw last==null?new CatalogException("Stalker create_link nuk u përgjigj"):last;
 }
 private JSONObject payload(String body)throws CatalogException {
  try{JSONObject root=new JSONObject(body);JSONObject js=root.optJSONObject("js");if(js!=null)return js;JSONObject data=root.optJSONObject("data");return data==null?root:data;}
  catch(Exception invalid){throw new CatalogException("Stalker create_link përgjigje e pavlefshme");}
 }
 static int refusal(JSONObject result) {
  for(String key:new String[]{"status","status_code","code","error"}){String value=result.optString(key);if("401".equals(value))return 401;if("403".equals(value))return 403;}
  String text=(result.optString("error")+" "+result.optString("message")+" "+result.optString("msg")).toLowerCase(Locale.ROOT);
  return text.contains("expired")||text.contains("invalid token")||text.contains("unauthorized")||text.contains("forbidden")||text.contains("authorization required")?401:0;
 }
}
