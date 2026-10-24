package de.lazytv.pro.live;

import android.app.Activity;
import de.lazytv.pro.R;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.*;

final class M3uModule implements LiveModule {
  private final Playlist playlist;
  private final LiveSources.Source source;

  M3uModule(Activity a) throws CatalogException {
    playlist =
        LiveModule.require(a, PlaylistType.M3U_URL, PlaylistType.M3U_FILE, PlaylistType.FREE_TV);
    source = new LiveSources.M3u(a, playlist);
  }

  public Playlist playlist() {
    return playlist;
  }

  public LiveSources.Source source() {
    return source;
  }

  public String identity() {
    return playlist.getId();
  }

  public int layout() {
    return R.layout.activity_m3u_player;
  }

  public void openCatalog(Activity a, String type) {
    LiveModule.catalog(a, playlist, type);
  }
}
