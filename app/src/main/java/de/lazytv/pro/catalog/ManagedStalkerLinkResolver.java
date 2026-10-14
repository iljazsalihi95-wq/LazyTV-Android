package de.lazytv.pro.catalog;
import java.util.*;
import de.lazytv.pro.playlist.*;
/** Separate sessions per managed server, even when two servers share an upstream portal. */
final class ManagedStalkerLinkResolver {
 private static final Map<String,StalkerCatalogSource> sessions=new LinkedHashMap<>();
 static boolean canRefresh(StreamItem item){return item!=null&&(item.type==CatalogType.LIVE||!item.meta("stalker_original_cmd").isEmpty())&&ServerTenPlaybackResolver.canRefresh(item);}
 static ResolvedStream resolve(StreamItem item)throws CatalogException {
  ServerTenPlaybackResolver.Reference ref=ServerTenPlaybackResolver.reference(item);
  if(ref==null)throw new CatalogException("Mungon identiteti i portalit për fresh create_link");
  String server=item.meta("managed_server_id");if(server.isEmpty())server=item.meta("managed_server");
  String key=server+"\n"+ref.portal+"\n"+ref.mac;StalkerCatalogSource source;
  synchronized(sessions){source=sessions.get(key);if(source==null){source=new StalkerCatalogSource();sessions.put(key,source);if(sessions.size()>24)sessions.remove(sessions.keySet().iterator().next());}}
  Playlist portal=new Playlist(server,server,PlaylistType.STALKER_PORTAL,ref.portal,"","",ref.mac,0);
  StreamItem original=new StreamItem(item.id,item.name,item.categoryId,item.logo,ref.command,item.tvgId,item.type);
  StalkerCatalogSource.LinkResult fresh;synchronized(source){fresh=source.resolve(portal,original);}
  Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.put("stalker_session","true");
  return new ResolvedStream(fresh.url,item.type.name(),item.name,item.logo,item.tvgId,fresh.headers,metadata);
 }
}
