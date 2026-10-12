package de.lazytv.pro.catalog;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class EpisodeMetadataTest {
 @Test public void inheritedMetadataAndEpisodeMetadataAgree(){Map<String,String> input=new HashMap<>();input.put("plot","Provider plot");Episode episode=new Episode("id","ep","series","cat","","url","mp4","40 min",2,3,input);input.put("plot","mutated");StreamItem item=episode;assertEquals("Provider plot",item.meta("plot"));assertEquals(item.metadata,episode.metadata);}
}
