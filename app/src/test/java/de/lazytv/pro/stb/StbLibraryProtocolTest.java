package de.lazytv.pro.stb;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class StbLibraryProtocolTest {
 @Test public void namesDoNotClassifyLinearMoviesChannels()throws Exception{assertEquals(StbProtocol.Kind.LIVE,new StbProtocol.Channel(new JSONObject().put("name","EN CHRISTMAS 4K").put("cmd","ffmpeg http://example.test/live")).kind);}
 @Test public void explicitProviderMovieMetadataWins()throws Exception{assertEquals(StbProtocol.Kind.MOVIE,new StbProtocol.Channel(new JSONObject().put("type","vod")).kind);}
 @Test public void containersAreNotPlayable()throws Exception{for(String flag:new String[]{"is_series","is_season"}){StbProtocol.Channel c=new StbProtocol.Channel(new JSONObject().put(flag,1).put("cmd","/media/1.mpg"),"vod",StbProtocol.Kind.MOVIE);assertFalse(c.playable());}}
 @Test public void keepsOriginalEpisodeAndFileIds()throws Exception{StbProtocol.Channel c=new StbProtocol.Channel(new JSONObject().put("id","800").put("is_file",true).put("cmd","/media/file_800.mpg").put("video_id","50").put("season_id","62").put("series_number","3"),"vod",StbProtocol.Kind.MOVIE);assertEquals("50",c.movieId);assertEquals("62",c.seasonId);assertEquals("3",c.episodeNumber);assertEquals("/media/file_800.mpg",c.cmd);assertTrue(c.playable());}
 @Test public void nullCommandIsNotAPlaybackUrl()throws Exception{assertFalse(new StbProtocol.Channel(new JSONObject().put("cmd",JSONObject.NULL),"vod",StbProtocol.Kind.EPISODE).playable());}
 @Test public void pageMetadataControlsNavigation()throws Exception{StbProtocol.Page p=new StbProtocol.Page(new JSONObject().put("total_items",30).put("max_page_items",14).put("data",new JSONArray().put(new JSONObject().put("id","1"))),"vod",StbProtocol.Kind.MOVIE);assertTrue(p.hasNext(1));assertFalse(p.hasNext(3));}
 @org.junit.Test public void contextualSeasonWinsOverGenericSeriesType()throws Exception{
  org.json.JSONObject row=new org.json.JSONObject().put("id","60").put("name","Season 1").put("type","series");
  assertEquals(StbProtocol.Kind.SEASON,new StbProtocol.Channel(row,"series",StbProtocol.Kind.SEASON).kind);
  assertEquals(StbProtocol.Kind.EPISODE,new StbProtocol.Channel(row,"series",StbProtocol.Kind.EPISODE).kind);
  row.put("item_type","season");assertEquals(StbProtocol.Kind.SEASON,new StbProtocol.Channel(row,"series",StbProtocol.Kind.SERIES).kind);
 }
}
