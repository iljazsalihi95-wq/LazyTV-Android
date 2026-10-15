package de.lazytv.pro.catalog;
import java.net.*;import java.util.*;
import de.lazytv.pro.playlist.*;
/** Server 10 playback. API-ready HTTP(S) links use the same direct path as LazyIPTV (method B). */
public final class ServerTenPlaybackResolver {
 private static final Map<String,StalkerCatalogSource> SESSIONS=new LinkedHashMap<String,StalkerCatalogSource>(8,0.75f,true);
 private ServerTenPlaybackResolver(){}
 static final class Reference {
  final String portal,mac,command;
  Reference(String portal,String mac,String command){this.portal=portal;this.mac=mac;this.command=command;}
 }
 static Reference reference(String url) {
  try {
   URL parsed=new URL(url);Map<String,String> query=new HashMap<>();
   if(parsed.getQuery()==null)return null;
   for(String pair:parsed.getQuery().split("&")){int eq=pair.indexOf('=');if(eq>0)query.put(URLDecoder.decode(pair.substring(0,eq),"UTF-8"),URLDecoder.decode(pair.substring(eq+1),"UTF-8"));}
   String mac=query.get("mac"),stream=query.get("stream");
   if(mac==null||!mac.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")||stream==null||!stream.matches("[0-9]+"))return null;
   String portal=parsed.getProtocol()+"://"+parsed.getAuthority();
   return new Reference(portal,mac,"ffmpeg http://localhost/ch/"+stream+"_");
  }catch(Exception ignored){return null;}
 }
 static Reference reference(StreamItem item){
  if(item==null)return null;
  Reference signed=reference(item.streamUrl);
  String hinted=item.meta("server10_portal");if(hinted.isEmpty())hinted=item.meta("portal_url");
  String hintedMac=item.meta("server10_mac");if(hintedMac.isEmpty())hintedMac=item.meta("mac");
  String command=item.meta("stalker_original_cmd");
  boolean validHintedMac=hintedMac.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
  if(signed==null){
   if(hinted.isEmpty()||!validHintedMac||command.isEmpty())return null;
   String portal=portalIdentity(hinted,"");if(portal.isEmpty())return null;
   return new Reference(portal,hintedMac,command);
  }
  String portal=portalIdentity(hinted,signed.portal);
  String mac=validHintedMac?hintedMac:signed.mac;
  if(command.isEmpty())command=signed.command;
  return new Reference(portal,mac,command);
 }
 private static String portalIdentity(String hint,String fallback){
  if(hint==null||hint.trim().isEmpty())return fallback;
  try{
   URL u=new URL(hint.trim());String path=u.getPath()==null?"":u.getPath();String lower=path.toLowerCase(Locale.US);
   for(String marker:new String[]{"/portal.php","/server/load.php","/c/portal.php","/c/server/load.php"}){int at=lower.indexOf(marker);if(at>=0){path=path.substring(0,at);break;}}
   while(path.endsWith("/")&&path.length()>1)path=path.substring(0,path.length()-1);
   return u.getProtocol()+"://"+u.getAuthority()+path;
  }catch(Exception ignored){return fallback;}
 }
 private static synchronized StalkerCatalogSource session(String key){
  StalkerCatalogSource source=SESSIONS.get(key);
  if(source==null){source=new StalkerCatalogSource();SESSIONS.put(key,source);if(SESSIONS.size()>8)SESSIONS.remove(SESSIONS.keySet().iterator().next());}
  return source;
 }
 public static boolean canRefresh(StreamItem item){return item!=null&&item.streamUrl!=null&&item.streamUrl.matches("(?i)^https?://.*")||reference(item)!=null;}
 public static ResolvedStream resolve(StreamItem item)throws CatalogException {
  // Method B: when the backend/API already returned a playable URL, hand it to the player
  // unchanged. Do not turn it back into portal+MAC+create_link on every build/session.
  if(item!=null&&item.streamUrl!=null&&item.streamUrl.matches("(?i)^https?://.*")){
   Map<String,String> headers=new LinkedHashMap<>();headers.put("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");headers.put("Accept","*/*");
   Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.put("server10_direct_api","true");
   return new ResolvedStream(item.streamUrl,item.type.name(),item.name,item.logo,item.tvgId,headers,metadata);
  }
  Reference ref=reference(item);
  if(ref==null)return new ResolvedStream(item.streamUrl,item.type.name(),item.name,item.logo,item.tvgId,null,item.metadata);
  Playlist portal=new Playlist("server10-runtime","Server 10",PlaylistType.STALKER_PORTAL,ref.portal,"","",ref.mac,0);
  StreamItem original=new StreamItem(item.id,item.name,item.categoryId,item.logo,ref.command,item.tvgId,CatalogType.LIVE);
  StalkerCatalogSource source=session(ref.portal+"\n"+ref.mac);
  StalkerCatalogSource.LinkResult fresh;
  synchronized(source){fresh=source.resolve(portal,original);}
  Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.put("server10_fresh_link","true");metadata.put("stalker_session","true");
  return new ResolvedStream(fresh.url,item.type.name(),item.name,item.logo,item.tvgId,fresh.headers,metadata);
 }
}
