package de.lazytv.pro.live;
import de.lazytv.pro.playlist.PlaylistType;
/** Dedicated provider entry; the native session mechanics remain shared. */
public final class StalkerLivePlayerActivity extends LiveTvActivity {
 @Override protected PlaylistType requiredProviderType(){return PlaylistType.STALKER_PORTAL;}
}
