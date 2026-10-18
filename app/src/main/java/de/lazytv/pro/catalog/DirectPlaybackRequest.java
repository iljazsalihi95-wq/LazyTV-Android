package de.lazytv.pro.catalog;

import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Provider supplied direct requests. Never derives a portal or modifies a signed query. */
public final class DirectPlaybackRequest {
 private DirectPlaybackRequest(){}
 public static ResolvedStream resolve(StreamItem item,String raw,String transport){
  String url=raw==null?"":raw.trim();
  if(url.regionMatches(true,0,"ffmpeg ",0,7))url=url.substring(7).trim();
  Map<String,String> headers=new LinkedHashMap<>();
  if(item instanceof M3uHeaders)for(Map.Entry<String,String> h:((M3uHeaders)item).requestHeaders().entrySet())put(headers,h.getKey(),h.getValue());
  for(String name:new String[]{"User-Agent","Referer","Origin","Cookie","Authorization","Accept","X-User-Agent"}){
   String value=item.meta(name);if(value.isEmpty())value=item.meta(name.toLowerCase(java.util.Locale.ROOT).replace('-','_'));if(!value.isEmpty())put(headers,name,value);
  }
  int pipe=url.indexOf('|');if(pipe>=0){String suffix=url.substring(pipe+1);url=url.substring(0,pipe);for(String pair:suffix.split("&")){int eq=pair.indexOf('=');if(eq<=0)continue;try{put(headers,URLDecoder.decode(pair.substring(0,eq),"UTF-8"),URLDecoder.decode(pair.substring(eq+1),"UTF-8"));}catch(Exception ignored){}}}
  if(!contains(headers,"User-Agent"))headers.put("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
  if(!contains(headers,"Accept"))headers.put("Accept","*/*");
  Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.put("managed_direct","true");
  return new ResolvedStream(url,transport,item.name,item.logo,item.tvgId,headers,metadata);
 }
 private static boolean contains(Map<String,String> h,String key){for(String k:h.keySet())if(k.equalsIgnoreCase(key))return true;return false;}
 private static void put(Map<String,String> h,String key,String value){if(key==null||value==null||!key.matches("[A-Za-z0-9-]+")||value.contains("\r")||value.contains("\n"))return;String old=null;for(String k:h.keySet())if(k.equalsIgnoreCase(key)){old=k;break;}if(old!=null)h.remove(old);h.put(key,value);}
}
