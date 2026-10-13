package de.lazytv.pro.live;
import de.lazytv.pro.playlist.PlaylistType;
/** Dedicated provider entry; the native session mechanics remain shared. */
public final class XtreamLivePlayerActivity extends LiveTvActivity {
 @Override protected PlaylistType requiredProviderType(){return PlaylistType.XTREAM_CODES;}
}
