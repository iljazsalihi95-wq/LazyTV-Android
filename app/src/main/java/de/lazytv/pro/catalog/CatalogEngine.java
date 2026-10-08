package de.lazytv.pro.catalog;

import android.content.Context;
import de.lazytv.pro.playlist.*;

public class CatalogEngine {
    private final StalkerCatalogSource stalker = new StalkerCatalogSource();

    public Catalog load(Context c, Playlist p) throws CatalogException {
        if (p != null && p.getType() == PlaylistType.FREE_TV) return loadFreeTv();
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

    private Catalog loadFreeTv() {
        Catalog out = new Catalog();
        for (FreeTvCatalog.Region region : FreeTvCatalog.Region.values()) {
            String categoryId = "free:" + region.name().toLowerCase(java.util.Locale.ROOT);
            out.categories.add(new Category(categoryId, region.label, CatalogType.LIVE, region.label));
        }
        for (FreeTvCatalog.Channel channel : FreeTvCatalog.all()) {
            String categoryId = "free:" + channel.region.name().toLowerCase(java.util.Locale.ROOT);
            out.items.add(new StreamItem(
                    "free:" + channel.id,
                    channel.name,
                    categoryId,
                    channel.logoUrl,
                    channel.streamUrl,
                    "",
                    CatalogType.LIVE));
        }
        return out;
    }

    public Catalog loadType(Context c, Playlist p, CatalogType t) throws CatalogException {
        if (p != null && p.getType() == PlaylistType.FREE_TV) return loadFreeTv();
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
