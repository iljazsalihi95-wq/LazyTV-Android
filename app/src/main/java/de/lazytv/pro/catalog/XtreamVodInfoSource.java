package de.lazytv.pro.catalog;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import de.lazytv.pro.playlist.Playlist;

/** Loads authoritative Xtream VOD details from player_api get_vod_info.
 * Kept separate from Live playback so movie metadata cannot disturb the Live session. */
public final class XtreamVodInfoSource {
 public Map<String,String> load(Playlist p,String vodId)throws CatalogException{
  Map<String,String> out=new LinkedHashMap<>();
  if(p==null||vodId==null||vodId.trim().isEmpty())return out;
  String url=base(p)+"/player_api.php?"+auth(p)+"&action=get_vod_info&vod_id="+HttpClient.enc(vodId.trim());
  JSONObject root=obj(HttpClient.get(url,null));
  JSONObject info=root.optJSONObject("info");
  JSONObject movie=root.optJSONObject("movie_data");
  if(info!=null){
   put(out,"plot",first(info,"plot","description","overview"));
   put(out,"rating",first(info,"rating","rating_5based","imdb_rating"));
   put(out,"year",first(info,"year","releasedate","release_date"));
   put(out,"genre",first(info,"genre","genres"));
   put(out,"duration",first(info,"duration","duration_secs"));
   put(out,"director",first(info,"director"));
   put(out,"cast",first(info,"cast","actors"));
   put(out,"backdrop",firstBackdrop(info));
   put(out,"poster",first(info,"movie_image","cover_big","cover","stream_icon"));
  }
  if(movie!=null){
   putIfMissing(out,"year",first(movie,"year","releasedate","release_date"));
   putIfMissing(out,"genre",first(movie,"genre"));
   putIfMissing(out,"duration",first(movie,"duration","duration_secs"));
   putIfMissing(out,"poster",first(movie,"stream_icon","movie_image","cover"));
   put(out,"container_extension",first(movie,"container_extension"));
  }
  return out;
 }
 private String base(Playlist p)throws CatalogException{
  String x=p.getUrl()==null?"":p.getUrl().trim().replace("&amp;","&");
  if(x.isEmpty())throw new CatalogException("Xtream URL mungon");
  if(!x.matches("(?i)^https?://.*"))x="http://"+x;
  try{
   java.net.URI u=new java.net.URI(x);String scheme=u.getScheme()==null?"http":u.getScheme();String host=u.getHost();
   if(host==null||host.trim().isEmpty())throw new Exception("host");int port=u.getPort();String path=u.getPath()==null?"":u.getPath().replaceAll("/+$","");
   path=path.replaceFirst("(?i)/(player_api\\.php|get\\.php)$","").replaceAll("/+$","");
   return scheme+"://"+host+(port>0?":"+port:"")+path;
  }catch(Exception e){throw new CatalogException("Xtream URL e pavlefshme",e);}
 }
 private String auth(Playlist p){return "username="+HttpClient.enc(p.getUsername())+"&password="+HttpClient.enc(p.getPassword());}
 private JSONObject obj(String s)throws CatalogException{try{return new JSONObject(s);}catch(Exception e){throw new CatalogException("Përgjigje Xtream VOD e pavlefshme",e);}}
 private String first(JSONObject o,String...keys){for(String k:keys){String v=o.optString(k,"").trim();if(!v.isEmpty()&&!"null".equalsIgnoreCase(v))return v;}return "";}
 private String firstBackdrop(JSONObject o){JSONArray a=o.optJSONArray("backdrop_path");if(a!=null&&a.length()>0)return a.optString(0,"").trim();return first(o,"backdrop_path","backdrop");}
 private void put(Map<String,String>m,String k,String v){if(v!=null&&!v.trim().isEmpty())m.put(k,v.trim());}
 private void putIfMissing(Map<String,String>m,String k,String v){if(!m.containsKey(k))put(m,k,v);}
}
