package de.lazytv.pro.live;

import android.app.Activity;
import android.content.Intent;
import de.lazytv.pro.R;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.Playlist;

/** Server 10 identity/resolver stays isolated from playlists and STB sessions. */
final class PremiumModule implements LiveModule {
  private final LiveSources.Source source;
  private final boolean managedEntry;

  PremiumModule(Activity a) throws CatalogException {
    managedEntry =
        "MANAGED_BACKEND".equals(a.getIntent().getStringExtra("source_scope"))
            && "SERVER_10".equalsIgnoreCase(a.getIntent().getStringExtra("server_name"));
    source =
        managedEntry
            ? new LiveSources.Managed(a, "SERVER_10")
            : new LiveSources.Premium(a, a.getIntent());
  }

  public Playlist playlist() {
    return null;
  }

  public LiveSources.Source source() {
    return source;
  }

  public String identity() {
    return managedEntry ? "MANAGED_BACKEND|SERVER_10" : "SERVER_10";
  }

  public boolean premium() {
    return true;
  }

  public boolean managed() {
    return managedEntry;
  }

  public java.util.List<CountryGroup> groups(java.util.List<Category> categories) {
    return managedEntry
        ? ManagedCountryResolver.groups(categories)
        : CountryResolver.groups(categories);
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
