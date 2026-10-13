package de.lazytv.pro.live;
import de.lazytv.pro.playlist.*;
/** Only the selected provider's stored identity reaches its playback entry point. */
public final class LiveProviderRouter {
 private LiveProviderRouter(){}
 public static Class<? extends LiveTvActivity> activity(Playlist playlist){if(playlist==null)return LiveTvActivity.class;switch(playlist.getType()){case XTREAM_CODES:return XtreamLivePlayerActivity.class;case STALKER_PORTAL:return StalkerLivePlayerActivity.class;default:return IptvLivePlayerActivity.class;}}
}
