package de.lazytv.pro.live;
import de.lazytv.pro.playlist.*;import org.junit.Test;import static org.junit.Assert.*;
public class LiveProviderRouterTest {
 private Playlist provider(PlaylistType type){return new Playlist("id","Fixture",type,"http://fixture.invalid:8080","user","pass","00:11:22:33:44:55",0);}
 @Test public void providerEntriesNeverCross(){Playlist xtream=provider(PlaylistType.XTREAM_CODES),stalker=provider(PlaylistType.STALKER_PORTAL);assertEquals(XtreamLivePlayerActivity.class,LiveProviderRouter.activity(xtream));assertEquals(StalkerLivePlayerActivity.class,LiveProviderRouter.activity(stalker));assertEquals("user",xtream.getUsername());assertEquals("pass",xtream.getPassword());assertEquals("00:11:22:33:44:55",stalker.getMacAddress());assertEquals("http://fixture.invalid:8080",xtream.getUrl());}
 @Test public void iptvAndTrialKeepTheirSources(){assertEquals(IptvLivePlayerActivity.class,LiveProviderRouter.activity(provider(PlaylistType.M3U_URL)));assertEquals(IptvLivePlayerActivity.class,LiveProviderRouter.activity(provider(PlaylistType.M3U_FILE)));assertEquals(LiveTvActivity.class,LiveProviderRouter.activity(null));}
}
