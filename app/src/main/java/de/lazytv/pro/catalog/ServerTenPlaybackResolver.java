package de.lazytv.pro.catalog;
import java.net.*;import java.util.*;
import de.lazytv.pro.playlist.*;
/** Fresh Server 10 portal links; sheet rows remain immutable references. */
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
 /**
  * A signed playback URL intentionally identifies only host/MAC/stream. Some managed servers
  * are installed below a portal prefix though, so allow the catalog row to carry a stable
  * portal identity hint separately from the expiring playback URL. Never copy play_token,
  * signature or expiry fields into this identity.
  */
 static Reference reference(StreamItem item){
  if(item==null)return null;
  // Method B rows are already resolved by the owner LazyTV API. They must be handed to the
  // player byte-for-byte and must never be converted back into MAG/create_link requests.
  if("true".equalsIgnoreCase(item.meta("managed_direct")))return null;
  Reference signed=reference(item.streamUrl);
  String hinted=item.meta("server10_portal");if(hinted.isEmpty())hinted=item.meta("portal_url");
  String hintedMac=item.meta("server10_mac");if(hintedMac.isEmpty())hintedMac=item.meta("mac");
  String command=item.meta("stalker_original_cmd");
  boolean validHintedMac=hintedMac.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
  // A Server 10 row may deliberately contain only stable MAG identity metadata while its
  // STREAM_URL_CMD is expired or no longer parseable. Do not fall back to that stale URL:
  // rebuild create_link from portal + MAC + original command when all three are available.
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
 public static boolean canRefresh(StreamItem item){return reference(item)!=null;}
 public static ResolvedStream resolve(StreamItem item)throws CatalogException {
  Reference ref=reference(item);
  if(ref==null)return new ResolvedStream(item.streamUrl,item.type.name(),item.name,item.logo,item.tvgId,null,item.metadata);
  Playlist portal=new Playlist("server10-runtime","Server 10",PlaylistType.STALKER_PORTAL,ref.portal,"","",ref.mac,0);
  StreamItem original=new StreamItem(item.id,item.name,item.categoryId,item.logo,ref.command,item.tvgId,CatalogType.LIVE);
  StalkerCatalogSource source=session(ref.portal+"\n"+ref.mac);
  StalkerCatalogSource.LinkResult fresh;
  // Server 10 must never fall back to the expired Sheet playback token. Keep one cached
  // authenticated MAG session and let the proven Stalker flow perform exactly one auth refresh.
  synchronized(source){fresh=source.resolve(portal,original);}
  Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.put("server10_fresh_link","true");metadata.put("stalker_session","true");
  return new ResolvedStream(fresh.url,item.type.name(),item.name,item.logo,item.tvgId,fresh.headers,metadata);
 }
}
