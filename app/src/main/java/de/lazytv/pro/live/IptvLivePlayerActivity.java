package de.lazytv.pro.live;
import de.lazytv.pro.playlist.PlaylistType;
public final class IptvLivePlayerActivity extends LiveTvActivity {
 @Override protected boolean acceptsProvider(PlaylistType type){return type==PlaylistType.M3U_URL||type==PlaylistType.M3U_FILE||type==PlaylistType.FREE_TV;}
}
