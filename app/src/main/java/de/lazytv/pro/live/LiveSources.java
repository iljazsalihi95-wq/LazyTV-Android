package de.lazytv.pro.live;

import android.content.Context;
import android.content.Intent;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.*;

/** Each screen owns one source/session; the shared Activity only renders the browser. */
final class LiveSources {
  interface Source {
    default void close() {}

    Catalog load() throws Exception;

    ResolvedStream resolve(StreamItem item, boolean refresh) throws Exception;
  }

  static Source create(Context context, Intent intent, Playlist playlist, boolean builtIn)
      throws CatalogException {
    String scope = intent.getStringExtra("source_scope");
    if ("SERVER_10".equals(scope)) return new Premium(context, intent);
    if ("MANAGED_BACKEND".equals(scope))
      return new Managed(context, intent.getStringExtra("server_name"));
    if (builtIn) return new Trial(context, "SERVER".equals(scope));
    if (playlist == null) throw new CatalogException("Playlist mungon");
    switch (playlist.getType()) {
      case XTREAM_CODES:
        return new Xtream(context, playlist);
      case STALKER_PORTAL:
        return new Stalker(context, playlist);
      default:
        return new M3u(context, playlist);
    }
  }

  private abstract static class PlaylistSource implements Source {
    final Context context;
    final Playlist playlist;
    final CatalogEngine engine = new CatalogEngine();

    PlaylistSource(Context context, Playlist playlist) {
      this.context = context;
      this.playlist = playlist;
    }

    public Catalog load() throws Exception {
      return engine.load(context, playlist);
    }

    public ResolvedStream resolve(StreamItem item, boolean refresh) throws Exception {
      return engine.resolveLiveStream(playlist, item);
    }
  }

  static final class M3u extends PlaylistSource {
    M3u(Context c, Playlist p) {
      super(c, p);
    }
  }

  static final class Xtream extends PlaylistSource {
    Xtream(Context c, Playlist p) {
      super(c, p);
    }
  }

  private static final class Stalker extends PlaylistSource {
    Stalker(Context c, Playlist p) {
      super(c, p);
    }
  }

  static final class Managed implements Source {
    final ManagedBackendCatalogSource source;
    final String name;

    Managed(Context c, String name) throws CatalogException {
      source = new ManagedBackendCatalogSource(c);
      this.name = name;
    }

    public Catalog load() throws Exception {
      return source.load(name);
    }

    public ResolvedStream resolve(StreamItem item, boolean refresh) throws Exception {
      return source.resolve(name, item, refresh);
    }
  }

  static final class Trial implements Source {
    final Context context;
    final boolean server;

    Trial(Context c, boolean server) {
      context = c;
      this.server = server;
    }

    public Catalog load() throws Exception {
      return server
          ? new BuiltInCatalogSource().load(context)
          : new TrialCatalogSource().load(context);
    }

    public ResolvedStream resolve(StreamItem i, boolean refresh) {
      return new ResolvedStream(i.streamUrl, i.type.name(), i.name, i.logo, i.tvgId, null, null);
    }
  }

  static final class Premium implements Source {
    private final ServerTenPlaybackResolver.OwnerSession ownerSession =
        new ServerTenPlaybackResolver.OwnerSession();

    public void close() {
      ownerSession.clear();
    }

    final Context context;
    final String spreadsheet, tab, server;

    Premium(Context c, Intent i) {
      context = c;
      spreadsheet = extra(i, "spreadsheet_id", "15csf4ExcYV0N-zWXLU8gEIbPctBo7DHCgwSaW7qhoX4");
      tab = extra(i, "sheet_name", "LIVE");
      server = extra(i, "server_id", "SERVER_10");
    }

    public Catalog load() throws CatalogException {
      return new SheetServerCatalogSource().load(context, spreadsheet, tab, server);
    }

    public ResolvedStream resolve(StreamItem item, boolean refresh) throws Exception {
      Catalog fresh;
      try {
        fresh = load();
      } catch (CatalogException unavailable) {
        return premiumLink(item, refresh);
      }
      for (StreamItem row : fresh.items)
        if (row.id.equals(item.id)) return premiumLink(row, refresh);
      throw new CatalogException("Kanali nuk gjendet më në Server 10.");
    }

    private ResolvedStream premiumLink(StreamItem item, boolean refresh) throws CatalogException {
      return ownerSession.resolve(item, refresh);
    }

    static String extra(Intent i, String key, String fallback) {
      String value = i.getStringExtra(key);
      return value == null || value.trim().isEmpty() ? fallback : value;
    }
  }
}
