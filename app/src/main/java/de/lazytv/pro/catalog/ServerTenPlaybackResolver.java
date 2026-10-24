package de.lazytv.pro.catalog;

import de.lazytv.pro.playlist.*;
import java.net.*;
import java.util.*;

/** Fresh Server 10 portal links; sheet rows remain immutable references. */
public final class ServerTenPlaybackResolver {
  private static final Map<String, StalkerCatalogSource> SESSIONS =
      new LinkedHashMap<String, StalkerCatalogSource>(8, 0.75f, true);

  private ServerTenPlaybackResolver() {}

  /** Premium activity owns its auth cache; clearing it cannot invalidate managed/STB players. */
  public static final class OwnerSession {
    private final Map<String, StalkerCatalogSource> sessions =
        new LinkedHashMap<String, StalkerCatalogSource>(8, 0.75f, true);

    public ResolvedStream resolve(StreamItem item, boolean refresh) throws CatalogException {
      if (!refresh && canRefresh(item)) return ServerTenPlaybackResolver.resolve(item, sessions);
      return resolvePremium(item, refresh, sessions);
    }

    public void clear() {
      synchronized (sessions) {
        sessions.clear();
      }
    }
  }

  static final class Reference {
    final String portal, mac, command;

    Reference(String portal, String mac, String command) {
      this.portal = portal;
      this.mac = mac;
      this.command = command;
    }
  }

  static Reference reference(String url) {
    try {
      URL parsed = new URL(url);
      Map<String, String> query = new HashMap<>();
      if (parsed.getQuery() == null) return null;
      for (String pair : parsed.getQuery().split("&")) {
        int eq = pair.indexOf('=');
        if (eq > 0)
          query.put(
              URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
              URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
      }
      String mac = query.get("mac"), stream = query.get("stream");
      if (mac == null
          || !mac.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")
          || stream == null
          || !stream.matches("[0-9]+")) return null;
      String portal = parsed.getProtocol() + "://" + parsed.getAuthority();
      return new Reference(portal, mac, "ffmpeg http://localhost/ch/" + stream + "_");
    } catch (Exception ignored) {
      return null;
    }
  }

  static Reference reference(StreamItem item) {
    if (item == null) return null;
    String raw = item.streamUrl == null ? "" : item.streamUrl.trim();
    boolean signed = raw.toLowerCase(Locale.ROOT).contains("play_token=");
    String hinted = item.meta("server10_portal");
    if (hinted.isEmpty()) hinted = item.meta("portal_url");
    String hintedMac = item.meta("server10_mac");
    if (hintedMac.isEmpty()) hintedMac = item.meta("mac");
    String command = item.meta("stalker_original_cmd");
    boolean validHintedMac = hintedMac.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
    boolean explicitMag = !hinted.isEmpty() && validHintedMac && !command.isEmpty();
    // Stable direct owner rows stay direct. Signed play_token rows remain immutable references
    // for recovery; the normal Premium start may still use the supplied direct request.
    if ("true".equalsIgnoreCase(item.meta("managed_direct")) && !signed && !explicitMag)
      return null;
    if (!item.meta("managed_source_id").isEmpty()
        && raw.matches("(?i)^https?://.*")
        && !signed
        && !explicitMag) return null;
    Reference signedRef = reference(item.streamUrl);
    if (signedRef == null) {
      if (!explicitMag) return null;
      String portal = portalIdentity(hinted, "");
      if (portal.isEmpty()) return null;
      return new Reference(portal, hintedMac, command);
    }
    String portal = portalIdentity(hinted, signedRef.portal);
    String mac = validHintedMac ? hintedMac : signedRef.mac;
    if (command.isEmpty()) command = signedRef.command;
    return new Reference(portal, mac, command);
  }

  private static String portalIdentity(String hint, String fallback) {
    if (hint == null || hint.trim().isEmpty()) return fallback;
    try {
      URL u = new URL(hint.trim());
      String path = u.getPath() == null ? "" : u.getPath();
      String lower = path.toLowerCase(Locale.US);
      for (String marker :
          new String[] {"/portal.php", "/server/load.php", "/c/portal.php", "/c/server/load.php"}) {
        int at = lower.indexOf(marker);
        if (at >= 0) {
          path = path.substring(0, at);
          break;
        }
      }
      while (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
      return u.getProtocol() + "://" + u.getAuthority() + path;
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static StalkerCatalogSource session(
      Map<String, StalkerCatalogSource> sessions, String key) {
    synchronized (sessions) {
      StalkerCatalogSource source = sessions.get(key);
      if (source == null) {
        source = new StalkerCatalogSource();
        sessions.put(key, source);
        if (sessions.size() > 8) sessions.remove(sessions.keySet().iterator().next());
      }
      return source;
    }
  }

  /** Drop only this Premium identity after an HTTP playback refusal; other sessions stay cached. */
  public static void invalidateSession(StreamItem item) {
    invalidateSession(item, SESSIONS);
  }

  private static void invalidateSession(
      StreamItem item, Map<String, StalkerCatalogSource> sessions) {
    Reference ref = reference(item);
    if (ref == null) return;
    synchronized (sessions) {
      sessions.remove(ref.portal + "\n" + ref.mac);
    }
  }

  /**
   * Premium entry: start with the complete provider URL like LazyIPTV. On a playback refusal
   * discard only this MAG session and obtain a fresh handshake + create_link for the same row.
   */
  public static ResolvedStream resolvePremium(StreamItem item, boolean afterFailure)
      throws CatalogException {
    return resolvePremium(item, afterFailure, SESSIONS);
  }

  private static ResolvedStream resolvePremium(
      StreamItem item, boolean afterFailure, Map<String, StalkerCatalogSource> sessions)
      throws CatalogException {
    if (item == null) throw new CatalogException("Kanali Premium mungon");
    String raw = item.streamUrl == null ? "" : item.streamUrl.trim();
    if (!afterFailure
        && raw.matches("(?i)^https?://.*")
        && item.meta("stalker_original_cmd").isEmpty()) {
      String transport = item.meta("managed_transport");
      return DirectPlaybackRequest.resolve(
          item, raw, transport.isEmpty() ? item.type.name() : transport);
    }
    if (afterFailure && !canRefresh(item))
      throw new CatalogException("Premium: provider-i nuk dha identitet për fresh create_link.");
    if (afterFailure) invalidateSession(item, sessions);
    return resolve(item, sessions);
  }

  public static boolean canRefresh(StreamItem item) {
    return reference(item) != null;
  }

  public static ResolvedStream resolve(StreamItem item) throws CatalogException {
    return resolve(item, SESSIONS);
  }

  private static ResolvedStream resolve(StreamItem item, Map<String, StalkerCatalogSource> sessions)
      throws CatalogException {
    Reference ref = reference(item);
    if (ref == null) return DirectPlaybackRequest.resolve(item, item.streamUrl, item.type.name());
    Playlist portal =
        new Playlist(
            "server10-runtime",
            "Server 10",
            PlaylistType.STALKER_PORTAL,
            ref.portal,
            "",
            "",
            ref.mac,
            0);
    StreamItem original =
        new StreamItem(
            item.id,
            item.name,
            item.categoryId,
            item.logo,
            ref.command,
            item.tvgId,
            CatalogType.LIVE);
    StalkerCatalogSource source = session(sessions, ref.portal + "\n" + ref.mac);
    StalkerCatalogSource.LinkResult fresh;
    synchronized (source) {
      fresh = source.resolve(portal, original);
    }
    Map<String, String> metadata = new LinkedHashMap<>(item.metadata);
    metadata.put("server10_fresh_link", "true");
    metadata.put("stalker_session", "true");
    Map<String, String> headers = new LinkedHashMap<>(fresh.headers);
    for (String name : new String[] {"User-Agent", "Referer", "Origin", "X-User-Agent"}) {
      String value = item.meta(name);
      if (value.isEmpty()) value = item.meta(name.toLowerCase(Locale.ROOT).replace('-', '_'));
      if (!value.isEmpty() && !value.contains("\r") && !value.contains("\n"))
        headers.put(name, value);
    }
    return new ResolvedStream(
        fresh.url, item.type.name(), item.name, item.logo, item.tvgId, headers, metadata);
  }
}
