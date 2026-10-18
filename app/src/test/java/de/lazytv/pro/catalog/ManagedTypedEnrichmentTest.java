package de.lazytv.pro.catalog;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ManagedTypedEnrichmentTest {
 @Test public void parsedSeriesAndEpisodeSurviveInfoEnrichment()throws Exception{
  String csv="ID,SOURCE_ID,SERVER_ID,COUNTRY_CODE,CATEGORY,NAME,STREAM_URL_CMD,STATUS,SERIES_ID,SEASON,EPISODE,EXTENSION,DURATION,EPISODE_TITLE\n"+
   "r,22,SERVER_09,AL,Drama,Series title,https://fixture.invalid/episode.mp4,ACTIVE,parent,2,3,mp4,00:42:00,Third episode\n";
  Catalog parsed=ManagedSheetCatalogSource.parse(new ManagedSheetCatalogSource.Server("SERVER_09","Fixture","fixture"),"SERIES",csv);
  Map<String,String> identity=new HashMap<>();identity.put("PORTAL_URL","https://fixture.invalid/portal.php");
  Catalog enriched=ManagedSheetCatalogSource.enrich(parsed,identity);Episode ep=null;Series series=null;
  for(StreamItem item:enriched.items){if(item instanceof Episode)ep=(Episode)item;else if(item instanceof Series)series=(Series)item;}
  assertNotNull(ep);assertNotNull(series);assertEquals(series.id,ep.seriesId);assertEquals(2,ep.seasonNumber);assertEquals(3,ep.episodeNumber);assertEquals("mp4",ep.extension);assertEquals("00:42:00",ep.duration);assertEquals("Third episode",ep.name);assertEquals("https://fixture.invalid/episode.mp4",ep.streamUrl);assertEquals("https://fixture.invalid/portal.php",ep.meta("portal_url"));
  for(StreamItem item:parsed.items)assertEquals("",item.meta("portal_url"));
 }
 @Test public void ordinaryLiveIdentityAndRequestMetadataStayUnchanged(){
  Map<String,String> metadata=new HashMap<>();metadata.put("managed_direct","true");
  StreamItem live=new StreamItem("channel","Title","category","logo","https://fixture.invalid/live.ts","epg",CatalogType.LIVE,Collections.singletonList("https://fixture.invalid/fallback.ts"),metadata);
  Catalog source=new Catalog();source.items.add(live);source.categories.add(new Category("category","News",CatalogType.LIVE));
  StreamItem result=ManagedSheetCatalogSource.enrich(source,Collections.emptyMap()).items.get(0);
  assertEquals(live.id,result.id);assertEquals(live.streamUrl,result.streamUrl);assertEquals(live.type,result.type);assertEquals(live.metadata,result.metadata);assertEquals(live.fallbackUrls,result.fallbackUrls);assertEquals(live.tvgId,result.tvgId);
 }
}
