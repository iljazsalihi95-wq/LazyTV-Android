package de.lazytv.pro.catalog;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class StalkerLiveParserTest {
 @Test public void isoAliasesAndKosovo() {
  String[][] aliases={{"AL","ALB"},{"XK","RKS","KS","XKX"},{"MK","MKD"},{"DE","DEU"},{"CH","CHE"},{"AT","AUT"},{"IT","ITA"},{"IE","IRL"},{"GB","UK","GBR"},{"US","USA"},{"JP","JPN"}};
  for(String[] group:aliases) for(String alias:group) assertEquals(alias,group[0],StalkerCountryResolver.normalize(alias));
 }
 @Test public void providerPriorityAndUnknown() {
  assertEquals("DE",StalkerCountryResolver.resolve("DEU","AL","Ireland","UK | Example"));
  assertEquals("AL",StalkerCountryResolver.resolve("invalid","ALB","Ireland","DE | Example"));
  assertEquals("IE",StalkerCountryResolver.resolve("","","LIVE | Ireland","DE | Example"));
  assertEquals("CH",StalkerCountryResolver.resolve("","","[CHE] Sports","Example"));
  assertEquals("OTHER",StalkerCountryResolver.resolve("","","Sports","Example"));
 }
 @Test public void mixedGenreRetainsOriginalCmdAndEpg() throws Exception {
  Catalog c=new Catalog(); StalkerLiveParser p=new StalkerLiveParser(c,new JSONArray("[{\"id\":\"1\",\"title\":\"Sports\",\"country_code\":\"ALB\"}]"));
  p.add(new JSONArray("[{\"id\":\"9\",\"tv_genre_id\":\"1\",\"name\":\"Example\",\"cmd\":\"ffmpeg http://localhost/ch/9_\",\"country_code\":\"DEU\",\"xmltv_id\":\"epg9\"},{\"id\":\"10\",\"tv_genre_id\":\"1\",\"name\":\"Other\",\"cmd\":\"original10\"}]"),"");
  assertEquals(2,c.categories.size()); assertEquals("DE",c.categories.get(0).country);assertEquals("AL",c.categories.get(1).country);
  StreamItem item=c.items.get(0);assertEquals("ffmpeg http://localhost/ch/9_",item.streamUrl);assertEquals(item.streamUrl,item.meta("stalker_original_cmd"));assertEquals("1",item.meta("stalker_genre_id"));assertEquals("epg9",item.tvgId);
  p.add(new JSONArray("[{\"id\":\"9\"}]"),"");assertEquals(2,c.items.size());
 }
 @Test public void embeddedAndRequestedGenresRemainReachable() throws Exception {
  Catalog c=new Catalog();StalkerLiveParser p=new StalkerLiveParser(c,new JSONArray("[{\"id\":\"7\",\"title\":\"[IRL] News\"}]"));
  p.add(new JSONArray("[{\"id\":\"1\",\"name\":\"Neutral\",\"cmd\":\"cmd1\"}]"),"7");
  p.add(new JSONArray("[{\"id\":\"2\",\"genre\":{\"id\":\"8\",\"title\":\"Japan\"},\"cmd\":\"cmd2\"}]"),"");
  assertEquals("IE",c.categories.get(0).country);assertEquals("JP",c.categories.get(1).country);
  for(StreamItem item:c.items) assertFalse(c.items(CatalogType.LIVE,item.categoryId).isEmpty());
 }
}
