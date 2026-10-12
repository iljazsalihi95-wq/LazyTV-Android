package de.lazytv.pro.catalog;
import java.util.*;import android.content.Context;import de.lazytv.pro.playlist.*;import org.json.*;
public class StreamResolver {
 private static final String TV_USER_AGENT="Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
 private final StalkerCatalogSource stalker;private final Context context;private final boolean epgEnabled;
 public StreamResolver(StalkerCatalogSource source){this(source,null,true);}
 public StreamResolver(StalkerCatalogSource source,Context context){this(source,context,true);}
 public StreamResolver(StalkerCatalogSource source,Context context,boolean epgEnabled){this.stalker=source;this.context=context==null?null:context.getApplicationContext();this.epgEnabled=epgEnabled;}
 public ResolvedStream resolve(Playlist p,StreamItem i)throws CatalogException{
  String url=i.streamUrl;Map<String,String> headers=new LinkedHashMap<>(),meta=new LinkedHashMap<>();
  if(p.getType()==PlaylistType.M3U_URL||p.getType()==PlaylistType.M3U_FILE||p.getType()==PlaylistType.XTREAM_CODES&&i instanceof M3uHeaders){if(i instanceof M3uHeaders){headers.putAll(((M3uHeaders)i).requestHeaders());meta.putAll(i.metadata);}}
  if(p.getType()==PlaylistType.STALKER_PORTAL){
   meta.putAll(i.metadata);meta.put("stalker_session","true");
   // Resolve last: optional EPG network calls must not consume a short-lived playback URL.
   if(epgEnabled&&i.type==CatalogType.LIVE)try{mergeExtendedEpg(meta,loadStalkerEpg(p,i.id,stalker.sessionHeaders(p)));}catch(Exception ignored){}
   StalkerCatalogSource.LinkResult x=stalker.resolve(p,i);url=x.url;headers.putAll(x.headers);
  }
  if(epgEnabled&&p.getType()==PlaylistType.XTREAM_CODES&&i.type==CatalogType.LIVE&&!(i instanceof M3uStreamItem)){
   try{meta.putAll(new XtreamCatalogSource().loadLiveEpg(p,i.id));}catch(Exception ignored){}
   try{Map<String,String> extended=loadXtreamEpgFallback(p,i.id);mergeExtendedEpg(meta,extended);}catch(Exception ignored){}
  }
  if(url==null||url.trim().isEmpty()||url.contains("://")&&url.startsWith("xtream-series://"))throw new CatalogException("Stream URL mungon");
  putIfHeaderMissing(headers,"User-Agent",TV_USER_AGENT);putIfHeaderMissing(headers,"Accept","*/*");
  if(i instanceof Episode){Episode e=(Episode)i;meta.put("series_id",e.seriesId);meta.put("season",String.valueOf(e.seasonNumber));meta.put("episode",String.valueOf(e.episodeNumber));meta.put("duration",e.duration==null?"":e.duration);meta.put("container",e.extension==null?"":e.extension);meta.putAll(e.metadata);}
  return new ResolvedStream(url,i.type.name(),i.name,i.logo,i.tvgId,headers,meta);
 }
 private void mergeExtendedEpg(Map<String,String> meta,Map<String,String> extended){
  if(extended==null||extended.isEmpty())return;
  if(!meta.containsKey("epg_now")&&extended.containsKey("epg_now"))meta.put("epg_now",extended.get("epg_now"));
  StringBuilder upcoming=new StringBuilder();
  for(int n=1;n<6;n++){String key=n==1?"epg_next":"epg_"+n;String value=extended.get(key);if(value==null||value.trim().isEmpty())continue;if(upcoming.length()>0)upcoming.append("\n");upcoming.append(value.trim());}
  if(upcoming.length()>0)meta.put("epg_next",upcoming.toString());
  for(Map.Entry<String,String> e:extended.entrySet())if(!meta.containsKey(e.getKey()))meta.put(e.getKey(),e.getValue());
 }
 private Map<String,String> loadStalkerEpg(Playlist p,String channelId,Map<String,String> sessionHeaders)throws Exception{
  Map<String,String> out=new LinkedHashMap<>();if(channelId==null||channelId.trim().isEmpty())return out;
  String q="type=itv&action=get_epg_info&period=6&ch_id="+HttpClient.enc(channelId)+"&JsHttpRequest=1-xml";JSONArray listings=null;
  Exception last=null;for(String ep:stalkerEndpoints(p.getUrl()))try{String body=HttpClient.get(ep+"?"+q,sessionHeaders);listings=stalkerEpgArray(body,channelId);if(listings!=null&&listings.length()>0)break;}catch(Exception e){last=e;}
  if(listings==null||listings.length()==0){q="type=itv&action=get_short_epg&ch_id="+HttpClient.enc(channelId)+"&JsHttpRequest=1-xml";for(String ep:stalkerEndpoints(p.getUrl()))try{String body=HttpClient.get(ep+"?"+q,sessionHeaders);listings=stalkerEpgArray(body,channelId);if(listings!=null&&listings.length()>0)break;}catch(Exception e){last=e;}}
  if((listings==null||listings.length()==0)&&last!=null)throw last;if(listings==null)return out;
  int written=0;for(int n=0;n<listings.length()&&written<6;n++){JSONObject e=listings.optJSONObject(n);if(e==null)continue;String title=decodeEpg(e.optString("name",e.optString("title",e.optString("program_name","")))).trim();if(title.isEmpty())continue;String start=stalkerEpgTime(e,true),end=stalkerEpgTime(e,false);String label=((start.isEmpty()?"":start+(end.isEmpty()?"":" - "+end)+"  ")+title).trim();if(written==0)out.put("epg_now",label);else if(written==1)out.put("epg_next",label);else out.put("epg_"+written,label);String desc=decodeEpg(e.optString("descr",e.optString("description",""))).trim();if(!desc.isEmpty())out.put(written==0?"epg_now_description":written==1?"epg_next_description":"epg_"+written+"_description",desc);written++;}
  return out;
 }
 private JSONArray stalkerEpgArray(String body,String channelId)throws Exception{JSONObject root=new JSONObject(body);JSONArray a=epgArrayFrom(root.opt("js"),channelId,0);if(a!=null)return a;return epgArrayFrom(root,channelId,0);}
 private JSONArray epgArrayFrom(Object node,String channelId,int depth){if(node==null||node==JSONObject.NULL||depth>4)return null;if(node instanceof JSONArray){JSONArray a=(JSONArray)node;if(a.length()==0)return a;Object first=a.opt(0);if(first instanceof JSONObject){JSONObject f=(JSONObject)first;if(f.has("name")||f.has("title")||f.has("program_name")||f.has("start")||f.has("start_timestamp"))return a;}for(int n=0;n<a.length();n++){JSONArray nested=epgArrayFrom(a.opt(n),channelId,depth+1);if(nested!=null&&nested.length()>0)return nested;}return null;}if(!(node instanceof JSONObject))return null;JSONObject o=(JSONObject)node;if(channelId!=null&&!channelId.isEmpty()){JSONArray direct=o.optJSONArray(channelId);if(direct!=null)return direct;JSONObject channel=o.optJSONObject(channelId);if(channel!=null){JSONArray nested=epgArrayFrom(channel,channelId,depth+1);if(nested!=null)return nested;}}for(String k:new String[]{"data","epg","items","results","programs","listings"}){JSONArray direct=o.optJSONArray(k);if(direct!=null){JSONArray nested=epgArrayFrom(direct,channelId,depth+1);if(nested!=null)return nested;}JSONObject child=o.optJSONObject(k);if(child!=null){JSONArray nested=epgArrayFrom(child,channelId,depth+1);if(nested!=null)return nested;}}Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();Object child=o.opt(k);if(child instanceof JSONArray||child instanceof JSONObject){JSONArray nested=epgArrayFrom(child,channelId,depth+1);if(nested!=null&&nested.length()>0)return nested;}}return null;}
 private String stalkerEpgTime(JSONObject e,boolean start){String[] ts=start?new String[]{"start_timestamp","start_unixtime","start"}:new String[]{"stop_timestamp","end_timestamp","stop","end"};for(String k:ts){Object raw=e.opt(k);if(raw==null)continue;String s=String.valueOf(raw).trim();if(s.isEmpty()||"null".equalsIgnoreCase(s))continue;try{long v=Long.parseLong(s);if(v>999999999999L)v/=1000L;if(v>1000000000L)return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new java.util.Date(v*1000L));}catch(Exception ignored){}if(s.matches("\\d{4}-\\d{2}-\\d{2}.*"))try{java.text.SimpleDateFormat in=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);java.util.Date d=in.parse(s);if(d!=null)return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(d);}catch(Exception ignored){}if(s.matches("\\d{2}:\\d{2}(:\\d{2})?"))return s.substring(0,5);}return"";}
 private List<String> stalkerEndpoints(String portal){LinkedHashSet<String>x=new LinkedHashSet<>();String u=portal==null?"":portal.trim().replace("&amp;","&");int q=u.indexOf('?');if(q>0)u=u.substring(0,q);u=u.replaceAll("/+$","");try{java.net.URL z=new java.net.URL(u);String origin=z.getProtocol()+"://"+z.getAuthority(),path=z.getPath()==null?"":z.getPath();String base=origin;if(path.startsWith("/stalker_portal"))base=origin+"/stalker_portal";else for(String marker:new String[]{"/portal.php","/server/load.php","/c/portal.php","/c/server/load.php","/c"}){int cut=path.indexOf(marker);if(cut>0){base=origin+path.substring(0,cut);break;}}x.add(base+"/portal.php");x.add(base+"/server/load.php");x.add(base+"/c/portal.php");x.add(base+"/c/server/load.php");x.add(origin+"/stalker_portal/server/load.php");x.add(origin+"/stalker_portal/portal.php");}catch(Exception ignored){}return new ArrayList<>(x);}
 private Map<String,String> loadXtreamEpgFallback(Playlist p,String streamId)throws Exception{
  Map<String,String> out=new LinkedHashMap<>();if(streamId==null||streamId.trim().isEmpty())return out;
  String u=XtreamUrlBuilder.api(p,"get_simple_data_table","stream_id",streamId);
  JSONObject root=new JSONObject(HttpClient.get(u,null));JSONArray a=root.optJSONArray("epg_listings");
  if(a==null){JSONObject js=root.optJSONObject("js");if(js!=null)a=js.optJSONArray("epg_listings");}
  if(a==null)a=root.optJSONArray("data");if(a==null||a.length()==0)return out;
  int written=0;for(int n=0;n<a.length()&&written<6;n++){JSONObject e=a.optJSONObject(n);if(e==null)continue;String title=decodeEpg(e.optString("title",e.optString("name",""))).trim();if(title.isEmpty())continue;String start=epgTime(e,"start","start_timestamp"),end=epgTime(e,"end","stop_timestamp");String label=((start.isEmpty()?"":start+(end.isEmpty()?"":" - "+end)+"  ")+title).trim();if(written==0)out.put("epg_now",label);else if(written==1)out.put("epg_next",label);else out.put("epg_"+written,label);String desc=decodeEpg(e.optString("description","")).trim();if(!desc.isEmpty())out.put(written==0?"epg_now_description":written==1?"epg_next_description":"epg_"+written+"_description",desc);written++;}
  return out;
 }
 private String epgTime(JSONObject e,String textKey,String timestampKey){String ts=e.optString(timestampKey,"").trim();if(!ts.isEmpty())try{long v=Long.parseLong(ts);if(v>999999999999L)v/=1000L;return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new java.util.Date(v*1000L));}catch(Exception ignored){}String raw=e.optString(textKey,"").trim();if(raw.isEmpty())return"";try{java.text.SimpleDateFormat in=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);java.util.Date d=in.parse(raw);if(d!=null)return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(d);}catch(Exception ignored){}return raw.matches("\\d{2}:\\d{2}(:\\d{2})?")?raw.substring(0,5):raw;}
 private String decodeEpg(String s){if(s==null)return"";try{byte[] b=android.util.Base64.decode(s,android.util.Base64.DEFAULT);String x=new String(b,java.nio.charset.StandardCharsets.UTF_8).trim();if(!x.isEmpty()&&x.chars().filter(ch->ch>=32||ch=='\n'||ch=='\r'||ch=='\t').count()>x.length()*0.8)return x;}catch(Exception ignored){}return s;}
 private static void putIfHeaderMissing(Map<String,String> headers,String name,String value){for(String key:headers.keySet())if(name.equalsIgnoreCase(key))return;headers.put(name,value);}
}
