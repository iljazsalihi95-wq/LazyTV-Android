package de.lazytv.pro.catalog;
import android.content.Context;
import de.lazytv.pro.playlist.*;
/** Ephemeral managed player context. Never changes saved personal providers or activation. */
public final class ManagedProviderContext {
 private ManagedProviderContext(){}
 public static boolean owns(StreamItem item){return item!=null&&!item.meta("managed_server").isEmpty();}
 public static Playlist playlist(StreamItem item){if(!owns(item))return null;String name=item.meta("managed_server");return new Playlist("MANAGED_BACKEND|"+name,name,PlaylistType.M3U_URL,"","","","",0);}
 public static ResolvedStream resolve(Context context,StreamItem item,boolean refresh)throws CatalogException {if(!owns(item))throw new CatalogException("Provider managed i pavlefshëm");return new ManagedBackendCatalogSource(context).resolve(item.meta("managed_server"),item,refresh);}
}
