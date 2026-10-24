package de.lazytv.pro.live;

import android.app.Activity;
import android.content.Intent;
import de.lazytv.pro.R;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.Playlist;

/** Server 10 identity/resolver stays isolated from playlists and STB sessions. */
final class PremiumModule implements LiveModule {
  private final LiveSources.Source source;

  PremiumModule(Activity a) throws CatalogException {
    source = new LiveSources.Premium(a, a.getIntent());
  }

  public Playlist playlist() {
    return null;
  }

  public LiveSources.Source source() {
    return source;
  }

  public String identity() {
    return "SERVER_10";
  }

  public boolean premium() {
    return true;
  }

  public int layout() {
    return R.layout.activity_premium_player;
  }

  public void openCatalog(Activity a, String type) {
    Intent i = new Intent(a, ManagedCatalogActivity.class);
    i.putExtra("server_name", "SERVER_10");
    i.putExtra("catalog_type", type);
    a.startActivity(i);
  }
}
