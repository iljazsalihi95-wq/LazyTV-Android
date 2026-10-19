package de.lazytv.pro.catalog;
import okhttp3.HttpUrl;
/** Xtream-only normalization; never alters stored provider credentials or URLs. */
public final class XtreamUrlBuilder {
 private XtreamUrlBuilder(){}
 public static String base(String value)throws CatalogException {
  String raw=value==null?"":value.trim().replace("&amp;","&");
  if(raw.isEmpty())throw new CatalogException("Xtream URL mungon");
  if(!raw.matches("(?i)^https?://.*"))raw="http://"+raw;
  HttpUrl parsed=HttpUrl.parse(raw);
  if(parsed==null)throw new CatalogException("Xtream URL e pavlefshme");
  String path=parsed.encodedPath().replaceAll("/+$","");
  path=path.replaceFirst("(?i)/(player_api\\.php|get\\.php)$","")
   .replaceFirst("(?i)/(c|stalker_portal)$","").replaceAll("/+$","");
  // Retain an explicitly supplied default port too.
  String authority=raw.substring(raw.indexOf("://")+3).split("[/?#]",2)[0];
  boolean explicitPort=authority.matches(".*:[0-9]+$");
  String host=parsed.host().contains(":")?"["+parsed.host()+"]":parsed.host();
  return parsed.scheme()+"://"+host+(explicitPort||parsed.port()!=HttpUrl.defaultPort(parsed.scheme())?":"+parsed.port():"")+path;
 }
 public static String api(de.lazytv.pro.playlist.Playlist playlist,String action,String...parameters)throws CatalogException {
  if(parameters.length%2!=0)throw new CatalogException("Xtream API parameters të pavlefshëm");
  StringBuilder url=new StringBuilder(base(playlist.getUrl())).append("/player_api.php?username=").append(HttpClient.enc(playlist.getUsername())).append("&password=").append(HttpClient.enc(playlist.getPassword()));
  if(action!=null&&!action.isEmpty())url.append("&action=").append(HttpClient.enc(action));
  for(int n=0;n<parameters.length;n+=2)url.append('&').append(HttpClient.enc(parameters[n])).append('=').append(HttpClient.enc(parameters[n+1]));
  return url.toString();
 }
 public static String m3u(de.lazytv.pro.playlist.Playlist playlist)throws CatalogException {
  return base(playlist.getUrl())+"/get.php?username="+HttpClient.enc(playlist.getUsername())+"&password="+HttpClient.enc(playlist.getPassword())+"&type=m3u&output=ts";
 }
 static String stream(String base,de.lazytv.pro.playlist.Playlist playlist,String kind,String id,String extension){
  String ext=extension==null?"":extension.trim();
  if(ext.isEmpty()||"null".equalsIgnoreCase(ext))ext="live".equalsIgnoreCase(kind)?"ts":"mp4";
  while(ext.startsWith("."))ext=ext.substring(1);
  if(ext.isEmpty())ext="live".equalsIgnoreCase(kind)?"ts":"mp4";
  return base+"/"+kind+"/"+segment(playlist.getUsername())+"/"+segment(playlist.getPassword())+"/"+segment(id)+"."+segment(ext);
 }
 private static String segment(String value){return HttpClient.enc(value).replace("+","%20");}
}
