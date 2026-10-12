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
  String url=XtreamUrlBuilder.api(p,"get_vod_info","vod_id",vodId.trim());
  JSONObject root=obj(HttpClient.get(url,null));
  JSONObject info=root.optJSONObject("info");
  JSONObject movie=root.optJSONObject("movie_data");
  if(info!=null){
   put(out,"plot",first(info,"plot","description","overview","storyline"));
   put(out,"rating",first(info,"rating","rating_5based","imdb_rating"));
   put(out,"year",year(first(info,"year","releasedate","release_date","releaseDate")));
   put(out,"genre",textValue(info,"genre","genres"));
   put(out,"duration",first(info,"duration"));
   put(out,"duration_secs",first(info,"duration_secs","duration_seconds"));
   put(out,"director",textValue(info,"director","directors"));
   put(out,"cast",textValue(info,"cast","actors"));
   put(out,"country",textValue(info,"country","countries"));
   put(out,"backdrop",firstBackdrop(info));
   put(out,"poster",first(info,"movie_image","cover_big","cover","stream_icon"));
  }
  if(movie!=null){
   putIfMissing(out,"plot",first(movie,"plot","description","overview","storyline"));
   putIfMissing(out,"rating",first(movie,"rating","rating_5based","imdb_rating"));
   putIfMissing(out,"year",year(first(movie,"year","releasedate","release_date","releaseDate")));
   putIfMissing(out,"genre",textValue(movie,"genre","genres"));
   putIfMissing(out,"duration",first(movie,"duration"));
   putIfMissing(out,"duration_secs",first(movie,"duration_secs","duration_seconds"));
   putIfMissing(out,"director",textValue(movie,"director","directors"));
   putIfMissing(out,"cast",textValue(movie,"cast","actors"));
   putIfMissing(out,"country",textValue(movie,"country","countries"));
   putIfMissing(out,"backdrop",firstBackdrop(movie));
   putIfMissing(out,"poster",first(movie,"stream_icon","movie_image","cover_big","cover"));
   put(out,"container_extension",first(movie,"container_extension"));
  }
  return out;
 }
 private JSONObject obj(String s)throws CatalogException{try{return new JSONObject(s);}catch(Exception e){throw new CatalogException("Përgjigje Xtream VOD e pavlefshme",e);}}
 private String first(JSONObject o,String...keys){for(String k:keys){Object raw=o.opt(k);if(raw==null||raw==JSONObject.NULL)continue;String v=String.valueOf(raw).trim();if(!v.isEmpty()&&!"null".equalsIgnoreCase(v))return v;}return "";}
 private String textValue(JSONObject o,String...keys){for(String k:keys){Object raw=o.opt(k);if(raw==null||raw==JSONObject.NULL)continue;if(raw instanceof JSONArray){JSONArray a=(JSONArray)raw;StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){Object entry=a.opt(i);String v="";if(entry instanceof JSONObject)v=first((JSONObject)entry,"name","title");else if(entry!=null)v=String.valueOf(entry).trim();if(v.isEmpty())continue;if(b.length()>0)b.append(", ");b.append(v);}if(b.length()>0)return b.toString();}else{String v=String.valueOf(raw).trim();if(!v.isEmpty()&&!"null".equalsIgnoreCase(v))return v;}}return "";}
 private String firstBackdrop(JSONObject o){Object raw=o.opt("backdrop_path");if(raw instanceof JSONArray){JSONArray a=(JSONArray)raw;if(a.length()>0){String v=a.optString(0,"").trim();if(!v.isEmpty())return v;}}return first(o,"backdrop_path","backdrop","backdrop_url");}
 private String year(String value){if(value==null)return "";String v=value.trim();java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:19|20)\\d{2}").matcher(v);return m.find()?m.group():v;}
 private void put(Map<String,String>m,String k,String v){if(v!=null&&!v.trim().isEmpty())m.put(k,v.trim());}
 private void putIfMissing(Map<String,String>m,String k,String v){if(!m.containsKey(k))put(m,k,v);}
}
