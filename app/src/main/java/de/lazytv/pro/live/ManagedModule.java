package de.lazytv.pro.live;

import android.app.Activity;
import android.content.Intent;
import de.lazytv.pro.R;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.Playlist;
import java.util.*;

final class ManagedModule implements LiveModule {
  private final String server;
  private final boolean managed;
  private final LiveSources.Source source;

  ManagedModule(Activity a) throws CatalogException {
    server = a.getIntent().getStringExtra("server_name");
    managed = "MANAGED_BACKEND".equals(a.getIntent().getStringExtra("source_scope"));
    source =
        managed
            ? new LiveSources.Managed(a, server)
            : new LiveSources.Trial(
                a, "SERVER".equals(a.getIntent().getStringExtra("source_scope")));
  }

  public Playlist playlist() {
    return null;
  }

  public LiveSources.Source source() {
    return source;
  }

  public String identity() {
    return managed ? "MANAGED_BACKEND|" + server : "BUILTIN_LIVE";
  }

  public boolean managed() {
    return managed;
  }

  public int layout() {
    return R.layout.activity_servers_player;
  }

  public List<CountryGroup> groups(List<Category> c) {
    return managed ? ManagedCountryResolver.groups(c) : CountryResolver.groups(c);
  }

  public void openCatalog(Activity a, String type) {
    Intent i = new Intent(a, ManagedCatalogActivity.class);
    i.putExtra("server_name", server);
    i.putExtra("catalog_type", type);
    a.startActivity(i);
  }
}
