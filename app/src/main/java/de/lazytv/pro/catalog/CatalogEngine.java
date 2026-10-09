package de.lazytv.pro.catalog;

import android.content.Context;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import de.lazytv.pro.playlist.*;

public class CatalogEngine {
    private static final String IPTV_ORG_COUNTRY_PLAYLIST = "https://iptv-org.github.io/iptv/index.country.m3u";
    private final StalkerCatalogSource stalker = new StalkerCatalogSource();

    public Catalog load(Context c, Playlist p) throws CatalogException {
        if (p != null && p.getType() == PlaylistType.FREE_TV) return loadFreeTv(c, p);
        String e = PlaylistValidator.validateForConnect(c, p);
        if (e != null) throw new CatalogException(e);
        switch (p.getType()) {
            case M3U_URL:
            case M3U_FILE:
                return new M3uCatalogSource().load(c, p);
            case XTREAM_CODES:
                return new XtreamCatalogSource(c).load(p);
            case STALKER_PORTAL:
                return stalker.load(p);
            default:
                throw new CatalogException("Lloj burimi i panjohur");
        }
    }

    private Catalog loadFreeTv(Context c, Playlist source) throws CatalogException {
        Playlist publicSource = new Playlist(
                source == null ? "builtin-free-tv" : source.getId(),
                "FREE TV • iptv-org",
                PlaylistType.M3U_URL,
                IPTV_ORG_COUNTRY_PLAYLIST,
                "", "", "",
                source == null ? System.currentTimeMillis() : source.getUpdatedAt());
        return new M3uCatalogSource().load(c, publicSource);
    }

    public Catalog loadType(Context c, Playlist p, CatalogType t) throws CatalogException {
        if (p != null && p.getType() == PlaylistType.FREE_TV) return loadFreeTv(c, p);
        String e = PlaylistValidator.validateForConnect(c, p);
        if (e != null) throw new CatalogException(e);
        String key = CatalogDiskCache.key(p.getId(), t);
        Catalog cached = CatalogDiskCache.get(c, key);
        if (cached != null) return cached;
        Catalog out;
        if (p.getType() == PlaylistType.STALKER_PORTAL) out = stalker.loadType(p, t);
        else if (p.getType() == PlaylistType.XTREAM_CODES) out = new XtreamCatalogSource(c).loadType(p, t);
        else out = load(c, p);
        CatalogDiskCache.put(c, key, out);
        return out;
    }

    public SeriesDetails loadSeries(Playlist p, Series s) throws CatalogException {
        if (p.getType() == PlaylistType.XTREAM_CODES) return new XtreamCatalogSource().loadSeries(p, s.id);
        if (p.getType() == PlaylistType.STALKER_PORTAL) return stalker.loadSeries(p, s);
        throw new CatalogException("Ky burim nuk ekspozon seasons/episodes të strukturuara");
    }

    public ResolvedStream resolveStream(Playlist p, StreamItem i) throws CatalogException {
        ResolvedStream r = new StreamResolver(stalker).resolve(p, i);
        return enrichXtreamSelection(p, i, r);
    }

    public ResolvedStream resolveStream(Context c, Playlist p, StreamItem i) throws CatalogException {
        ResolvedStream r = new StreamResolver(stalker, c).resolve(p, i);
        return enrichXtreamSelection(p, i, r);
    }

    private ResolvedStream enrichXtreamSelection(Playlist p, StreamItem i, ResolvedStream r) {
        if (p == null || i == null || r == null || p.getType() != PlaylistType.XTREAM_CODES) return r;
        if (i.type == CatalogType.LIVE) return enrichXtreamLiveEpg(p, i, r);
        if (i.type == CatalogType.MOVIES) return enrichXtreamVodInfo(p, i, r);
        return r;
    }

    /** Fetch short EPG only for the selected live channel; EPG failure never blocks playback. */
    private ResolvedStream enrichXtreamLiveEpg(Playlist p, StreamItem i, ResolvedStream r) {
        try {
            Map<String,String> epg = new XtreamCatalogSource().loadLiveEpg(p, i.id);
            if (epg == null || epg.isEmpty()) return r;
            Map<String,String> metadata = new LinkedHashMap<>(r.metadata);
            metadata.putAll(epg);
            return new ResolvedStream(r.url, r.streamType, r.title, r.artwork, r.epgId, r.headers, metadata);
        } catch (Exception ignored) {
            return r;
        }
    }

    /**
     * Xtream VOD lists are intentionally lightweight. When a movie is selected we query
     * get_vod_info once and merge the rich movie metadata into the resolved item. This
     * provides plot/rating/year/genre/duration/poster/backdrop data without slowing the
     * category/list screen with one request per movie. Failure is non-fatal to playback.
     */
    private ResolvedStream enrichXtreamVodInfo(Playlist p, StreamItem i, ResolvedStream r) {
        try {
            String endpoint = xtreamBase(p) + "/player_api.php?username=" + HttpClient.enc(p.getUsername())
                    + "&password=" + HttpClient.enc(p.getPassword())
                    + "&action=get_vod_info&vod_id=" + HttpClient.enc(i.id);
            JSONObject root = new JSONObject(HttpClient.get(endpoint, null));
            JSONObject info = root.optJSONObject("info");
            JSONObject movie = root.optJSONObject("movie_data");
            Map<String,String> metadata = new LinkedHashMap<>(r.metadata);
            mergeVodMetadata(metadata, movie);
            mergeVodMetadata(metadata, info);
            String artwork = firstJson(info, "movie_image", "cover_big", "cover", "poster");
            if (artwork.isEmpty()) artwork = firstJson(movie, "stream_icon", "movie_image", "cover");
            if (artwork.isEmpty()) artwork = r.artwork;
            return new ResolvedStream(r.url, r.streamType, r.title, artwork, r.epgId, r.headers, metadata);
        } catch (Exception ignored) {
            return r;
        }
    }

    private void mergeVodMetadata(Map<String,String> out, JSONObject o) {
        if (o == null) return;
        putIfPresent(out, "plot", firstJson(o, "plot", "description", "overview"));
        putIfPresent(out, "rating", firstJson(o, "rating", "rating_5based", "imdb_rating"));
        putIfPresent(out, "year", firstJson(o, "year", "releasedate", "release_date"));
        putIfPresent(out, "genre", firstJson(o, "genre", "genres"));
        putIfPresent(out, "duration", firstJson(o, "duration", "duration_secs"));
        putIfPresent(out, "director", firstJson(o, "director"));
        putIfPresent(out, "cast", firstJson(o, "cast", "actors"));
        putIfPresent(out, "backdrop", firstJson(o, "backdrop_path", "backdrop"));
    }

    private static void putIfPresent(Map<String,String> out, String key, String value) {
        if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) out.put(key, value.trim());
    }

    private static String firstJson(JSONObject o, String... keys) {
        if (o == null) return "";
        for (String key : keys) {
            Object raw = o.opt(key);
            if (raw == null || raw == JSONObject.NULL) continue;
            if (raw instanceof org.json.JSONArray) {
                org.json.JSONArray a = (org.json.JSONArray) raw;
                if (a.length() > 0) {
                    String v = a.optString(0, "").trim();
                    if (!v.isEmpty()) return v;
                }
            } else {
                String v = String.valueOf(raw).trim();
                if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
            }
        }
        return "";
    }

    private static String xtreamBase(Playlist p) {
        String x = p.getUrl() == null ? "" : p.getUrl().trim().replace("&amp;", "&");
        if (!x.matches("(?i)^https?://.*")) x = "http://" + x;
        try {
            URI u = new URI(x);
            String scheme = u.getScheme() == null ? "http" : u.getScheme();
            String host = u.getHost();
            if (host == null || host.trim().isEmpty()) throw new Exception();
            int port = u.getPort();
            String path = u.getPath() == null ? "" : u.getPath().replaceAll("/+$", "");
            path = path.replaceFirst("(?i)/(player_api\\.php|get\\.php)$", "").replaceAll("/+$", "");
            return scheme + "://" + host + (port > 0 ? ":" + port : "") + path;
        } catch (Exception e) {
            int q = x.indexOf('?');
            if (q > 0) x = x.substring(0, q);
            return x.replaceAll("(?i)/(player_api\\.php|get\\.php)/?$", "").replaceAll("/+$", "");
        }
    }

    public ResolvedStream refreshStream(Playlist p, StreamItem i) throws CatalogException {
        if (p.getType() == PlaylistType.STALKER_PORTAL) stalker.clear();
        return resolveStream(p, i);
    }

    public void close() { stalker.clear(); }
}
