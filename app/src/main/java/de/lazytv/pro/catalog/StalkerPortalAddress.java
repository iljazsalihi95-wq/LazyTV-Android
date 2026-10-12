package de.lazytv.pro.catalog;
import java.net.*;import java.util.*;
/** Native Stalker addresses. Never used to normalize Xtream or managed Server 10. */
public final class StalkerPortalAddress {
 private StalkerPortalAddress(){}
 public static String normalize(String value)throws CatalogException {
  String raw=value==null?"":value.trim();if(!raw.matches("(?i)^https?://.*"))raw="http://"+raw;
  try {URL url=new URL(raw);if(url.getHost().isEmpty())throw new Exception();
   String path=url.getPath().replaceAll("/+$","");String origin=url.getProtocol()+"://"+url.getAuthority();
   if(path.endsWith("/portal.php")||path.endsWith("/server/load.php"))return origin+path;
   if(path.endsWith("/c"))path=path.substring(0,path.length()-2);
   return origin+path+(path.endsWith("/stalker_portal")?"/server/load.php":"/portal.php");
  }catch(Exception error){throw new CatalogException("Stalker portal URL e pavlefshme");}
 }
 static List<String> endpoints(String value)throws CatalogException {
  String normalized=normalize(value);try {URL url=new URL(normalized);String origin=url.getProtocol()+"://"+url.getAuthority(),path=url.getPath();
   String base=path.endsWith("/server/load.php")?path.substring(0,path.length()-"/server/load.php".length()):path.substring(0,path.length()-"/portal.php".length());
   LinkedHashSet<String> out=new LinkedHashSet<>();out.add(normalized);
   for(String suffix:new String[]{"/portal.php","/server/load.php","/c/portal.php","/c/server/load.php"})out.add(origin+base+suffix);
   out.add(origin+"/stalker_portal/server/load.php");out.add(origin+"/stalker_portal/portal.php");return new ArrayList<>(out);
  }catch(Exception error){throw new CatalogException("Stalker endpoint i pavlefshëm");}
 }
}
