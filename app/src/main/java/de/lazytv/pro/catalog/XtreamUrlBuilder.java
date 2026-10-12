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
}
