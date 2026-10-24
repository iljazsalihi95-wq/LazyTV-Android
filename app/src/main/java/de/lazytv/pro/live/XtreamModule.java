package de.lazytv.pro.live;

import android.app.Activity;
import de.lazytv.pro.R;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.*;

final class XtreamModule implements LiveModule {
  private final Playlist playlist;
  private final LiveSources.Source source;

  XtreamModule(Activity a) throws CatalogException {
    playlist = LiveModule.require(a, PlaylistType.XTREAM_CODES);
    source = new LiveSources.Xtream(a, playlist);
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
    return R.layout.activity_xtream_player;
  }

  public void openCatalog(Activity a, String type) {
    LiveModule.catalog(a, playlist, type);
  }
}
