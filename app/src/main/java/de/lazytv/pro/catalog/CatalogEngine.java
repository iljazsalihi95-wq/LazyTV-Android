package de.lazytv.pro.catalog;

import android.content.Context;
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

    /**
     * FREE TV is a native M3U source backed by iptv-org's public country-grouped playlist.
     * It stays completely separate from user Premium/Xtream/Stalker credentials.
     * The existing M3U parser preserves group-title/tvg-logo metadata and gives us the
     * full public catalog instead of the old four-channel seed list.
     */
    private Catalog loadFreeTv(Context c, Playlist source) throws CatalogException {
        Playlist publicSource = new Playlist(
                source == null ? "builtin-free-tv" : source.getId(),
                "FREE TV • iptv-org",
                PlaylistType.M3U_URL,
                IPTV_ORG_COUNTRY_PLAYLIST,
                "",
                "",
                "",
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
        return new StreamResolver(stalker).resolve(p, i);
    }

    public ResolvedStream resolveStream(Context c, Playlist p, StreamItem i) throws CatalogException {
        return new StreamResolver(stalker, c).resolve(p, i);
    }

    public ResolvedStream refreshStream(Playlist p, StreamItem i) throws CatalogException {
        if (p.getType() == PlaylistType.STALKER_PORTAL) stalker.clear();
        return resolveStream(p, i);
    }

    public void close() {
        stalker.clear();
    }
}
