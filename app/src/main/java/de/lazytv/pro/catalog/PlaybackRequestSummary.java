package de.lazytv.pro.catalog;

import java.net.URI;
import java.util.Map;

/** Request-level diagnostics. Deliberately excludes URL paths, queries and header values. */
public final class PlaybackRequestSummary {
 private PlaybackRequestSummary(){}
 public static String describe(ResolvedStream stream,int status){
  String mode=stream!=null&&("true".equals(stream.metadata.get("stalker_session"))||"true".equals(stream.metadata.get("server10_fresh_link")))?"MAG/create_link":"DIRECT";
  String destination="unknown";
  if(stream!=null)try{URI uri=new URI(stream.url);String scheme=uri.getScheme(),host=uri.getHost();if(("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))&&host!=null&&host.matches("[A-Za-z0-9.:-]{1,253}"))destination=scheme.toLowerCase(java.util.Locale.ROOT)+"://"+host;}catch(Exception ignored){}
  StringBuilder out=new StringBuilder("PLAYBACK • ").append(mode).append(" • GET ").append(destination);
  if(status>0)out.append(" • HTTP ").append(status);
  if(stream!=null){StringBuilder names=new StringBuilder();for(String key:new String[]{"User-Agent","Referer","Origin","Cookie","Authorization","X-User-Agent"})if(has(stream.headers,key)){if(names.length()>0)names.append(", ");names.append(key);}out.append("\nHeaders: ").append(names.length()==0?"none":names);}
  return out.toString();
 }
 private static boolean has(Map<String,String> headers,String key){for(String k:headers.keySet())if(key.equalsIgnoreCase(k))return true;return false;}
}
