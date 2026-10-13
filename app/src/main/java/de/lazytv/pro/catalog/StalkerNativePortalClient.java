package de.lazytv.pro.catalog;
import org.json.*;import java.net.*;import java.util.*;
import de.lazytv.pro.playlist.Playlist;
/** Native provider client. Managed Server 10 keeps its existing StalkerCatalogSource. */
public class StalkerNativePortalClient extends StalkerCatalogSource {
 private String selectedEndpoint="";
 private Map<String,String> lastPortalHeaders=Collections.emptyMap();
 @Override public synchronized void clear(){super.clear();selectedEndpoint="";lastPortalHeaders=Collections.emptyMap();}
 @Override public synchronized Catalog load(Playlist playlist)throws CatalogException{return loadNativeLive(playlist);}
 @Override public synchronized Catalog loadType(Playlist playlist,CatalogType wanted)throws CatalogException{return wanted==CatalogType.LIVE?loadNativeLive(playlist):super.loadType(playlist,wanted);}
 private Catalog loadNativeLive(Playlist playlist)throws CatalogException {
  String genres;
  try{genres=catalogRequest(playlist,"type=itv&action=get_genres&JsHttpRequest=1-xml");}
  catch(ProviderHttpException error){if(error.status!=404&&error.status!=405)throw error;genres=catalogRequest(playlist,"type=itv&action=get_categories&JsHttpRequest=1-xml");}
  Catalog catalog=new Catalog();StalkerLiveParser parser=new StalkerLiveParser(catalog,StalkerPayload.rows(genres));
  try{catalogPages(playlist,parser,"get_all_channels","");}
  catch(ProviderHttpException error){if(error.status!=404&&error.status!=405)throw error;}
  Set<String> known=parser.genreIds(),used=parser.usedGenres();
  for(String id:known)if(!id.isEmpty()&&!"0".equals(id)&&!"*".equals(id)&&!used.contains(id))catalogPages(playlist,parser,"get_ordered_list",id);
  if(parser.size()==0)catalogPages(playlist,parser,"get_ordered_list","*");
  Set<String> categories=new HashSet<>();for(StreamItem item:catalog.items)categories.add(item.categoryId);
  catalog.categories.removeIf(category->!categories.contains(category.id));
  if(catalog.isEmpty())throw new CatalogException("Portali nuk ktheu kanale LIVE");return catalog;
 }
 private void catalogPages(Playlist playlist,StalkerLiveParser parser,String action,String genre)throws CatalogException {
  Set<String> pageIds=new HashSet<>();
  for(int page=1;page<=500;page++) {
   String query="type=itv&action="+action+"&p="+page+(genre.isEmpty()?"":"&genre="+HttpClient.enc(genre))+"&JsHttpRequest=1-xml";
   String body=catalogRequest(playlist,query);JSONArray rows=StalkerPayload.rows(body);if(rows.length()==0)break;int previous=pageIds.size();
   for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(row!=null)pageIds.add(StalkerLiveParser.first(row,"id","ch_id","channel_id"));}
   parser.add(rows,"*".equals(genre)?"":genre);int total=StalkerPayload.total(body);
   if(pageIds.size()==previous||total>0&&pageIds.size()>=total)break;
  }
 }
 private String catalogRequest(Playlist playlist,String query)throws CatalogException {
  for(int attempt=0;attempt<2;attempt++)try {
   String body=requestLink(playlist,query,sessionHeaders(playlist));Object root=new JSONTokener(body.trim()).nextValue();
   if(root instanceof JSONObject){int status=refusal(payload(body));if(status!=0)throw new ProviderHttpException(status);}return body;
  }catch(ProviderHttpException error){if(attempt==0&&(error.status==401||error.status==403)){clear();continue;}throw error;}
   catch(JSONException invalid){throw new CatalogException("Stalker catalog përgjigje e pavlefshme");}
  throw new CatalogException("Stalker catalog session-i u refuzua");
 }
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
    headers=new LinkedHashMap<>(sessionHeaders(playlist));
    headers.put("User-Agent","Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG254 stbapp");
    lastPortalHeaders=new LinkedHashMap<>(headers);
    JSONObject supplied=result.optJSONObject("headers");if(supplied==null)supplied=result.optJSONObject("http_headers");
    if(supplied!=null){Iterator<String> keys=supplied.keys();while(keys.hasNext()){String key=keys.next(),value=supplied.optString(key);if(key.matches("[A-Za-z0-9-]+")&&!value.contains("\r")&&!value.contains("\n"))headers.put(key,value);}}
    return new LinkResult(url,headers);
   }catch(ProviderHttpException error){if(attempt==0&&(error.status==401||error.status==403)){clear();continue;}throw error;}
  }
  throw new CatalogException("Stalker session-i u refuzua pas rifreskimit");
 }
 /** Snapshot only; EPG never holds the playback session monitor during network I/O. */
 public String loadEpg(Playlist playlist,String channelId)throws CatalogException {
  final Map<String,String> headers;final String endpoint;
  synchronized(this){headers=new LinkedHashMap<>(lastPortalHeaders);endpoint=selectedEndpoint;}
  if(headers.isEmpty()||endpoint.isEmpty())return "{\"js\":{\"data\":[]}}";
  String query="type=itv&action=get_epg_info&period=6&ch_id="+HttpClient.enc(channelId)+"&JsHttpRequest=1-xml";
  try{return HttpClient.get(endpoint+"?"+query,headers);}
  catch(ProviderHttpException unsupported){if(unsupported.status!=404&&unsupported.status!=405)throw unsupported;}
  return HttpClient.get(endpoint+"?type=itv&action=get_short_epg&ch_id="+HttpClient.enc(channelId)+"&JsHttpRequest=1-xml",headers);
 }
 protected String requestLink(Playlist playlist,String query,Map<String,String> headers)throws CatalogException {
  Playlist normalized=normalized(playlist);
  String body=sessionRequest(normalized,query);selectedEndpoint=sessionEndpoint(normalized);return body;
 }
 @Override public synchronized Map<String,String> sessionHeaders(Playlist playlist)throws CatalogException {return super.sessionHeaders(normalized(playlist));}
 private Playlist normalized(Playlist playlist)throws CatalogException {return new Playlist(playlist.getId(),playlist.getName(),playlist.getType(),StalkerPortalAddress.normalize(playlist.getUrl()),playlist.getUsername(),playlist.getPassword(),playlist.getMacAddress(),playlist.getUpdatedAt());}

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
