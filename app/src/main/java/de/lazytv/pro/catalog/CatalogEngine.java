package de.lazytv.pro.catalog;

import android.content.Context;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import de.lazytv.pro.playlist.*;

public class CatalogEngine {
    private static final String IPTV_ORG_COUNTRY_PLAYLIST = "https://iptv-org.github.io/iptv/index.country.m3u";
    // Portal sessions belong to a provider, not an Activity. Keep a small process-local
    // cache so Live, Movies, Series and their players reuse the same token.
    private static final Map<String, StalkerCatalogSource> STALKER_SESSIONS =
            new LinkedHashMap<String, StalkerCatalogSource>(8, 0.75f, true);

    private static synchronized StalkerCatalogSource stalker(Playlist p) {
        String key = p.getId() + "\n" + p.getUrl() + "\n" + p.getMacAddress();
        StalkerCatalogSource source = STALKER_SESSIONS.get(key);
        if (source == null) {
            source = new StalkerNativePortalClient();
            STALKER_SESSIONS.put(key, source);
            if (STALKER_SESSIONS.size() > 8)
                STALKER_SESSIONS.remove(STALKER_SESSIONS.keySet().iterator().next());
        }
        return source;
    }

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
                return stalker(p).load(p);
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
        String key = CatalogDiskCache.key(p.getId()+"|"+p.getUpdatedAt(), t);
        Catalog cached = CatalogDiskCache.get(c, key);
        if (cached != null) return cached;
        Catalog out;
        if (p.getType() == PlaylistType.STALKER_PORTAL) out = stalker(p).loadType(p, t);
        else if (p.getType() == PlaylistType.XTREAM_CODES) out = new XtreamCatalogSource(c).loadType(p, t);
        else out = load(c, p);
        CatalogDiskCache.put(c, key, out);
        return out;
    }

    public Map<String,String> loadMovieInfo(Playlist p, StreamItem movie) throws CatalogException {
        if (p.getType() == PlaylistType.XTREAM_CODES)
            return new XtreamVodInfoSource().load(p, movie.id);
        if (p.getType() == PlaylistType.STALKER_PORTAL)
            return stalker(p).loadMovieInfo(p, movie);
        return movie.metadata;
    }

    public SeriesDetails loadSeries(Playlist p, Series s) throws CatalogException {
        if (p.getType() == PlaylistType.XTREAM_CODES) return new XtreamCatalogSource().loadSeries(p, s.id);
        if (p.getType() == PlaylistType.STALKER_PORTAL) return stalker(p).loadSeries(p, s);
        throw new CatalogException("Ky burim nuk ekspozon seasons/episodes të strukturuara");
    }

    /** Live selection resolves a stream immediately; EPG is a separate optional request. */
    public ResolvedStream resolveLiveStream(Playlist playlist,StreamItem item)throws CatalogException {
        return new StreamResolver(playlist.getType()==PlaylistType.STALKER_PORTAL?stalker(playlist):null,null,false).resolve(playlist,item);
    }
    public EpgSchedule loadLiveSchedule(Playlist playlist,StreamItem item)throws CatalogException {
        if(playlist==null||item==null)return EpgSchedule.empty();String body;
        if(playlist.getType()==PlaylistType.STALKER_PORTAL){
            StalkerCatalogSource source=stalker(playlist);if(!(source instanceof StalkerNativePortalClient))return EpgSchedule.empty();
            body=((StalkerNativePortalClient)source).loadEpg(playlist,item.id);
        }else if(playlist.getType()==PlaylistType.XTREAM_CODES&&!(item instanceof M3uStreamItem)){
            try{
                body=HttpClient.get(XtreamUrlBuilder.api(playlist,"get_short_epg","stream_id",item.id,"limit","6"),null);
                EpgSchedule schedule=EpgSchedule.parse(body,item.id,System.currentTimeMillis());
                if(schedule.current!=null||schedule.next!=null)return schedule;
            }catch(ProviderHttpException unavailable){if(unavailable.status!=404&&unavailable.status!=405)throw unavailable;}
            body=HttpClient.get(XtreamUrlBuilder.api(playlist,"get_simple_data_table","stream_id",item.id),null);
        }else return EpgSchedule.empty();
        return EpgSchedule.parse(body,item.id,System.currentTimeMillis());
    }

    public ResolvedStream resolveStream(Playlist p, StreamItem i) throws CatalogException {
        ResolvedStream r = new StreamResolver(p != null && p.getType() == PlaylistType.STALKER_PORTAL ? stalker(p) : null).resolve(p, i);
        return enrichXtreamSelection(p, i, r);
    }

    public ResolvedStream resolveStream(Context c, Playlist p, StreamItem i) throws CatalogException {
        ResolvedStream r = new StreamResolver(p != null && p.getType() == PlaylistType.STALKER_PORTAL ? stalker(p) : null, c).resolve(p, i);
        return enrichXtreamSelection(p, i, r);
    }

    private ResolvedStream enrichXtreamSelection(Playlist p, StreamItem i, ResolvedStream r) {
        if (p == null || i == null || r == null || p.getType() != PlaylistType.XTREAM_CODES) return r;
        if (i instanceof M3uStreamItem) return r;
        if (i.type == CatalogType.LIVE) return enrichXtreamLiveEpg(p, i, r);
        // Movie details are loaded on the details page, before explicit PLAY.
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
            String endpoint = XtreamUrlBuilder.api(p,"get_vod_info","vod_id",i.id);
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

    public ResolvedStream refreshStream(Playlist p, StreamItem i) throws CatalogException {
        // Request a fresh link while retaining a valid portal authorization token.
        return resolveStream(p, i);
    }

    public void close() { /* Activity teardown must not invalidate a shared portal session. */ }
}
