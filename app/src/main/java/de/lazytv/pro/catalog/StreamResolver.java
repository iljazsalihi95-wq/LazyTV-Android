package de.lazytv.pro.catalog;
import java.util.*;import android.content.Context;import de.lazytv.pro.playlist.*;import org.json.*;
public class StreamResolver {
 private static final String TV_USER_AGENT="Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
 private final StalkerCatalogSource stalker; private final Context context;
 public StreamResolver(StalkerCatalogSource s){stalker=s;context=null;}
 public StreamResolver(StalkerCatalogSource s,Context c){stalker=s;context=c==null?null:c.getApplicationContext();}
 public ResolvedStream resolve(Playlist p,StreamItem i)throws CatalogException{
  String url=i.streamUrl;Map<String,String> headers=new LinkedHashMap<>(),meta=new LinkedHashMap<>();
  if(p.getType()==PlaylistType.M3U_URL||p.getType()==PlaylistType.M3U_FILE){if(i instanceof M3uStreamItem)headers.putAll(((M3uStreamItem)i).requestHeaders);}
  if(p.getType()==PlaylistType.STALKER_PORTAL){StalkerCatalogSource.LinkResult x=stalker.resolve(p,i);url=x.url;headers.putAll(x.headers);}
  if(p.getType()==PlaylistType.XTREAM_CODES&&i.type==CatalogType.LIVE){
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
 private Map<String,String> loadXtreamEpgFallback(Playlist p,String streamId)throws Exception{
  Map<String,String> out=new LinkedHashMap<>();if(streamId==null||streamId.trim().isEmpty())return out;
  String base=xtreamBase(p),auth="username="+HttpClient.enc(p.getUsername())+"&password="+HttpClient.enc(p.getPassword());
  String u=base+"/player_api.php?"+auth+"&action=get_simple_data_table&stream_id="+HttpClient.enc(streamId);
  JSONObject root=new JSONObject(HttpClient.get(u,null));JSONArray a=root.optJSONArray("epg_listings");
  if(a==null){JSONObject js=root.optJSONObject("js");if(js!=null)a=js.optJSONArray("epg_listings");}
  if(a==null)a=root.optJSONArray("data");if(a==null||a.length()==0)return out;
  int written=0;for(int n=0;n<a.length()&&written<6;n++){JSONObject e=a.optJSONObject(n);if(e==null)continue;String title=decodeEpg(e.optString("title",e.optString("name",""))).trim();if(title.isEmpty())continue;String start=epgTime(e,"start","start_timestamp"),end=epgTime(e,"end","stop_timestamp");String label=((start.isEmpty()?"":start+(end.isEmpty()?"":" - "+end)+"  ")+title).trim();if(written==0)out.put("epg_now",label);else if(written==1)out.put("epg_next",label);else out.put("epg_"+written,label);String desc=decodeEpg(e.optString("description","")).trim();if(!desc.isEmpty())out.put(written==0?"epg_now_description":written==1?"epg_next_description":"epg_"+written+"_description",desc);written++;}
  return out;
 }
 private String xtreamBase(Playlist p){String x=p.getUrl()==null?"":p.getUrl().trim().replace("&amp;","&");if(!x.matches("(?i)^https?://.*"))x="http://"+x;try{java.net.URI u=new java.net.URI(x);String scheme=u.getScheme()==null?"http":u.getScheme(),host=u.getHost();if(host==null||host.trim().isEmpty())throw new Exception();int port=u.getPort();String path=u.getPath()==null?"":u.getPath().replaceAll("/+$","");path=path.replaceFirst("(?i)/(player_api\\.php|get\\.php)$","").replaceAll("/+$","");return scheme+"://"+host+(port>0?":"+port:"")+path;}catch(Exception e){int q=x.indexOf('?');if(q>0)x=x.substring(0,q);return x.replaceAll("(?i)/(player_api\\.php|get\\.php)/?$","").replaceAll("/+$","");}}
 private String epgTime(JSONObject e,String textKey,String timestampKey){String ts=e.optString(timestampKey,"").trim();if(!ts.isEmpty())try{long v=Long.parseLong(ts);if(v>999999999999L)v/=1000L;return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new java.util.Date(v*1000L));}catch(Exception ignored){}String raw=e.optString(textKey,"").trim();if(raw.isEmpty())return"";try{java.text.SimpleDateFormat in=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);java.util.Date d=in.parse(raw);if(d!=null)return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(d);}catch(Exception ignored){}return raw.matches("\\d{2}:\\d{2}(:\\d{2})?")?raw.substring(0,5):raw;}
 private String decodeEpg(String s){if(s==null)return"";try{byte[] b=android.util.Base64.decode(s,android.util.Base64.DEFAULT);String x=new String(b,java.nio.charset.StandardCharsets.UTF_8).trim();if(!x.isEmpty()&&x.chars().filter(ch->ch>=32||ch=='\n'||ch=='\r'||ch=='\t').count()>x.length()*0.8)return x;}catch(Exception ignored){}return s;}
 private static void putIfHeaderMissing(Map<String,String> headers,String name,String value){for(String key:headers.keySet())if(name.equalsIgnoreCase(key))return;headers.put(name,value);}
}
