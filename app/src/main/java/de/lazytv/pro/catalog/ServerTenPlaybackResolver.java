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
   return new Reference(parsed.getProtocol()+"://"+parsed.getAuthority(),mac,"ffmpeg http://localhost/ch/"+stream+"_");
  }catch(Exception ignored){return null;}
 }
 private static synchronized StalkerCatalogSource session(String key){
  StalkerCatalogSource source=SESSIONS.get(key);
  if(source==null){source=new StalkerCatalogSource();SESSIONS.put(key,source);if(SESSIONS.size()>8)SESSIONS.remove(SESSIONS.keySet().iterator().next());}
  return source;
 }
 public static boolean canRefresh(StreamItem item){return item!=null&&reference(item.streamUrl)!=null;}
 public static ResolvedStream resolve(StreamItem item)throws CatalogException {
  Reference ref=reference(item.streamUrl);
  if(ref==null)return new ResolvedStream(item.streamUrl,item.type.name(),item.name,item.logo,item.tvgId,null,item.metadata);
  Playlist portal=new Playlist("server10-runtime","Server 10",PlaylistType.STALKER_PORTAL,ref.portal,"","",ref.mac,0);
  StreamItem original=new StreamItem(item.id,item.name,item.categoryId,item.logo,ref.command,item.tvgId,CatalogType.LIVE);
  StalkerCatalogSource source=session(ref.portal+"\n"+ref.mac);
  StalkerCatalogSource.LinkResult fresh;
  // A cached portal session owns mutable token/cookie state. Initial channel selection and
  // playback recovery run on different workers, so never let two create_link refreshes mutate
  // the same Server 10 session concurrently. This keeps each retry tied to one fresh token.
  synchronized(source){fresh=source.resolve(portal,original);}
  Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.put("server10_fresh_link","true");
  return new ResolvedStream(fresh.url,item.type.name(),item.name,item.logo,item.tvgId,fresh.headers,metadata);
 }
}
