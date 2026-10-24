package de.lazytv.pro.live;

import android.app.Activity;
import de.lazytv.pro.playlist.*;

/** Only the selected provider's stored identity reaches its playback entry point. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class LiveProviderRouter {
  private LiveProviderRouter() {}

  public static Class<? extends Activity> activity(Playlist playlist) {
    if (playlist == null) return LiveTvActivity.class;
    switch (playlist.getType()) {
      case XTREAM_CODES:
        return XtreamLivePlayerActivity.class;
      case STALKER_PORTAL:
        return StalkerLivePlayerActivity.class;
      default:
        return IptvLivePlayerActivity.class;
    }
  }
}
