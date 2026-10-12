package de.lazytv.pro.player;
import de.lazytv.pro.catalog.*;import java.util.*;
/** Uses existing resume keys; provider and stable content IDs, never playback URLs. */
public final class ContinueWatching {
 private ContinueWatching(){}
 public interface Positions {PlaybackResumeStore.Entry get(String key);}
 public static List<StreamItem> movies(String provider,List<StreamItem> items,Positions positions){List<StreamItem> result=new ArrayList<>();Map<String,PlaybackResumeStore.Entry> saved=new HashMap<>();for(StreamItem item:items){if(item.type!=CatalogType.MOVIES)continue;String key=PlaybackContentKey.of(provider,item);PlaybackResumeStore.Entry entry=positions.get(key);if(entry!=null){result.add(item);saved.put(key,entry);}}Collections.sort(result,(a,b)->Long.compare(saved.get(PlaybackContentKey.of(provider,b)).timestamp,saved.get(PlaybackContentKey.of(provider,a)).timestamp));return result;}
}
