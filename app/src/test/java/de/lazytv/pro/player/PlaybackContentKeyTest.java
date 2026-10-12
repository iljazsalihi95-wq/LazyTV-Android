package de.lazytv.pro.player;
import de.lazytv.pro.catalog.*;import java.util.Collections;import org.junit.Test;import static org.junit.Assert.*;
public class PlaybackContentKeyTest {
 private StreamItem movie(String url){return new StreamItem("42","Film","1","",url,"",CatalogType.MOVIES);}
 @Test public void signedUrlsDoNotChangeResumeKey(){assertEquals(PlaybackContentKey.of("provider",movie("https://host/movie?token=old")),PlaybackContentKey.of("provider",movie("https://host/movie?token=fresh")));}
 @Test public void providersAreIsolated(){assertNotEquals(PlaybackContentKey.of("one",movie("")),PlaybackContentKey.of("two",movie("")));}
 @Test public void episodesHaveIndependentProgress(){Episode one=new Episode("42","Episode","series","cat","","","mp4","",1,1,Collections.emptyMap());Episode two=new Episode("42","Episode","series","cat","","","mp4","",1,2,Collections.emptyMap());assertNotEquals(PlaybackContentKey.of("p",one),PlaybackContentKey.of("p",two));}
}
