package de.lazytv.pro.player;
import de.lazytv.pro.catalog.*;import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class ContinueWatchingTest {
 private StreamItem item(String id,CatalogType type){return new StreamItem(id,id,"cat","","https://host?token=fresh","",type);}
 @Test public void includesOnlySavedMoviesForCurrentProviderAndSortsRecentFirst(){StreamItem old=item("old",CatalogType.MOVIES),recent=item("recent",CatalogType.MOVIES),unwatched=item("unwatched",CatalogType.MOVIES),live=item("live",CatalogType.LIVE);Map<String,PlaybackResumeStore.Entry> saved=new HashMap<>();saved.put(PlaybackContentKey.of("p",old),new PlaybackResumeStore.Entry(40000,600000,100));saved.put(PlaybackContentKey.of("p",recent),new PlaybackResumeStore.Entry(90000,600000,200));saved.put(PlaybackContentKey.of("p",live),new PlaybackResumeStore.Entry(50000,600000,300));List<StreamItem> catalog=Arrays.asList(old,unwatched,live,recent);assertEquals(Arrays.asList(recent,old),ContinueWatching.movies("p",catalog,saved::get));assertTrue(ContinueWatching.movies("other",catalog,saved::get).isEmpty());}
}
