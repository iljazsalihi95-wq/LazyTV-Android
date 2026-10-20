package de.lazytv.pro.catalog;
import java.net.*;import java.util.*;
/** Native Stalker addresses. Never used to normalize Xtream or managed Server 10. */
public final class StalkerPortalAddress {
 private StalkerPortalAddress(){}
 public static String normalize(String value)throws CatalogException {
  String raw=value==null?"":value.trim();if(!raw.matches("(?i)^https?://.*"))raw="http://"+raw;
  try {URL url=new URL(raw);if(url.getHost().isEmpty())throw new Exception();
   String path=url.getPath().replaceAll("/+$","");String origin=url.getProtocol()+"://"+url.getAuthority();
   if(path.endsWith("/portal.php")||path.endsWith("/server/load.php")||path.endsWith("/load.php"))return origin+path;
   // MAG portals are often shared as the browser landing page (/c/, /c/index.html
   // or /index.html). These are UI paths, not API endpoints. Strip the landing page
   // before selecting the native Stalker endpoint instead of producing the invalid
   // .../index.html/portal.php path.
   if(path.endsWith("/c/index.html"))path=path.substring(0,path.length()-"/c/index.html".length());
   else if(path.endsWith("/index.html"))path=path.substring(0,path.length()-"/index.html".length());
   else if(path.endsWith("/c"))path=path.substring(0,path.length()-2);
   return origin+path+(path.endsWith("/stalker_portal")?"/server/load.php":"/portal.php");
  }catch(Exception error){throw new CatalogException("Stalker portal URL e pavlefshme");}
 }
 static List<String> endpoints(String value)throws CatalogException {
  String normalized=normalize(value);try {URL url=new URL(normalized);String origin=url.getProtocol()+"://"+url.getAuthority(),path=url.getPath();
   String base;
   if(path.endsWith("/server/load.php"))base=path.substring(0,path.length()-"/server/load.php".length());
   else if(path.endsWith("/portal.php"))base=path.substring(0,path.length()-"/portal.php".length());
   else if(path.endsWith("/load.php"))base=path.substring(0,path.length()-"/load.php".length());
   else base=path;
   LinkedHashSet<String> out=new LinkedHashSet<>();out.add(normalized);
   // Known MAG/Stalker endpoint layouts. Order is deterministic and the first working
   // endpoint is retained by the session client, so later channel changes do not probe again.
   for(String suffix:new String[]{"/portal.php","/server/load.php","/load.php","/c/portal.php","/c/server/load.php","/c/load.php"})out.add(origin+base+suffix);
   for(String suffix:new String[]{"/portal.php","/server/load.php","/load.php","/c/portal.php","/c/server/load.php","/c/load.php"})out.add(origin+suffix);
   out.add(origin+"/stalker_portal/server/load.php");out.add(origin+"/stalker_portal/portal.php");out.add(origin+"/stalker_portal/load.php");
   out.add(origin+"/stalker_portal/c/server/load.php");out.add(origin+"/stalker_portal/c/portal.php");
   return new ArrayList<>(out);
  }catch(Exception error){throw new CatalogException("Stalker endpoint i pavlefshëm");}
 }
}
