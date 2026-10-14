package de.lazytv.pro.catalog;
import java.util.*;import java.net.*;
import de.lazytv.pro.playlist.*;
/** Separate sessions per managed server, even when two servers share an upstream portal. */
final class ManagedStalkerLinkResolver {
 private static final Map<String,StalkerCatalogSource> sessions=new LinkedHashMap<>();
 private static final Map<String,Catalog> movieCommands=new java.util.concurrent.ConcurrentHashMap<>();private static final Map<String,Long> movieAt=new java.util.concurrent.ConcurrentHashMap<>();
 static boolean needsMovieCommand(StreamItem item){return item!=null&&item.type==CatalogType.MOVIES&&item.meta("stalker_original_cmd").isEmpty()&&ServerTenPlaybackResolver.canRefresh(item);}
 static boolean canRefresh(StreamItem item){return item!=null&&(item.type==CatalogType.LIVE||item.type==CatalogType.MOVIES||!item.meta("stalker_original_cmd").isEmpty())&&ServerTenPlaybackResolver.canRefresh(item);}
 private static String key(StreamItem item,ServerTenPlaybackResolver.Reference ref){String server=item.meta("managed_server_id");if(server.isEmpty())server=item.meta("managed_server");return server+"\n"+ref.portal+"\n"+ref.mac;}
 private static StalkerCatalogSource session(String key){synchronized(sessions){StalkerCatalogSource source=sessions.get(key);if(source==null){source=new StalkerCatalogSource();sessions.put(key,source);if(sessions.size()>24){String oldest=sessions.keySet().iterator().next();sessions.remove(oldest);movieCommands.remove(oldest);movieAt.remove(oldest);}}return source;}}
 private static Playlist portal(StreamItem item,ServerTenPlaybackResolver.Reference ref){String server=item.meta("managed_server");return new Playlist(server,server,PlaylistType.STALKER_PORTAL,ref.portal,"","",ref.mac,0);}
 static StreamItem verifyMovie(StreamItem item)throws CatalogException {
  ServerTenPlaybackResolver.Reference ref=ServerTenPlaybackResolver.reference(item);if(ref==null)throw new CatalogException("Mungon identiteti i filmit Stalker");String key=key(item,ref);StalkerCatalogSource source=session(key);Catalog nativeMovies;
  synchronized(source){Long at=movieAt.get(key);nativeMovies=movieCommands.get(key);if(nativeMovies==null||at==null||System.currentTimeMillis()-at>300000){nativeMovies=source.loadType(portal(item,ref),CatalogType.MOVIES);movieCommands.put(key,nativeMovies);movieAt.put(key,System.currentTimeMillis());}}
  return verifiedMovie(item,nativeMovies,ref);
 }
 static StreamItem verifiedMovie(StreamItem item,Catalog nativeMovies,ServerTenPlaybackResolver.Reference ref)throws CatalogException {
  String id="";try{String query=new URL(item.streamUrl).getQuery();if(query!=null)for(String part:query.split("&"))if(part.startsWith("stream="))id=URLDecoder.decode(part.substring(7),"UTF-8");}catch(Exception ignored){}
  for(StreamItem movie:nativeMovies.items)if(movie.type==CatalogType.MOVIES&&movie.id.equals(id)&&movie.name.trim().equalsIgnoreCase(item.name.trim())&&!movie.streamUrl.isEmpty()&&!movie.streamUrl.contains("localhost/ch/")){
   Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.putAll(movie.metadata);metadata.put("managed_original_item_id",item.id);metadata.put("stalker_original_cmd",movie.streamUrl);metadata.put("portal_url",ref.portal);metadata.put("mac",ref.mac);
   return new StreamItem(item.id,item.name,item.categoryId,item.logo,movie.streamUrl,item.tvgId,CatalogType.MOVIES,Collections.emptyList(),metadata);
  }
  throw new CatalogException("Provider-i nuk konfirmoi një film VOD me CMD origjinale për këtë hyrje");
 }
 static ResolvedStream resolve(StreamItem item)throws CatalogException {
  if(needsMovieCommand(item))item=verifyMovie(item);
  ServerTenPlaybackResolver.Reference ref=ServerTenPlaybackResolver.reference(item);
  if(ref==null)throw new CatalogException("Mungon identiteti i portalit për fresh create_link");
  StalkerCatalogSource source=session(key(item,ref));Playlist portal=portal(item,ref);
  StreamItem original;if(item instanceof Episode){Episode ep=(Episode)item;original=new Episode(item.id,item.name,ep.seriesId,item.categoryId,item.logo,ref.command,ep.extension,ep.duration,ep.seasonNumber,ep.episodeNumber,item.metadata);}else original=new StreamItem(item.id,item.name,item.categoryId,item.logo,ref.command,item.tvgId,item.type);
  StalkerCatalogSource.LinkResult fresh;
  synchronized(source){
   try{fresh=source.resolve(portal,original);}
   catch(ProviderHttpException e){
    if(e.status!=456)throw e;
    // Some MAG/Stalker portals use HTTP 456 for a stale/invalid device session.
    // Clear token/cookies and perform exactly one fresh handshake + create_link retry.
    source.clear();
    fresh=source.resolve(portal,original);
   }
  }
  Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.put("stalker_session","true");
  return new ResolvedStream(fresh.url,item.type.name(),item.name,item.logo,item.tvgId,fresh.headers,metadata);
 }
}
