package de.lazytv.pro.catalog;

import org.json.*;
import java.util.*;

/** Retains portal identities/CMD and makes every channel reachable through a provider category. */
final class StalkerLiveParser {
    private final Map<String,JSONObject> genres = new LinkedHashMap<>();
    private final Map<String,Category> visible = new LinkedHashMap<>();
    private final Set<String> seen = new HashSet<>();
    private final Catalog catalog;
    StalkerLiveParser(Catalog catalog, JSONArray categories) {
        this.catalog=catalog;
        for(int i=0;i<categories.length();i++) {
            JSONObject g=categories.optJSONObject(i);
            if(g!=null) genres.put(first(g,"id","genre_id","category_id"),g);
        }
    }
    Set<String> genreIds() { return new LinkedHashSet<>(genres.keySet()); }
    int size() { return seen.size(); }
    static String first(JSONObject object,String...keys) {
        if(object==null) return "";
        for(String key:keys) {
            Object value=object.opt(key);
            if(value!=null && value!=JSONObject.NULL && !(value instanceof JSONObject) && !(value instanceof JSONArray)) {
                String text=String.valueOf(value).trim();
                if(!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
            }
        }
        return "";
    }
    private static String country(JSONObject object) {
        String code=first(object,"country_code","country","country_iso","country_iso2","country_iso3");
        JSONObject nested=object==null?null:object.optJSONObject("country");
        if(nested!=null) code=first(nested,"code","iso2","iso3","name");
        return code;
    }
    void add(JSONArray channels,String requestedGenre) {
        for(int i=0;i<channels.length();i++) {
            JSONObject o=channels.optJSONObject(i); if(o==null) continue;
            String id=first(o,"id","ch_id","channel_id"); if(id.isEmpty()||!seen.add(id)) continue;
            JSONObject nested=o.optJSONObject("genre"); if(nested==null) nested=o.optJSONObject("category");
            String genreId=first(o,"tv_genre_id","category_id","genre_id");
            if(genreId.isEmpty()) genreId=first(nested,"id","genre_id","category_id");
            if(genreId.isEmpty()) genreId=requestedGenre;
            JSONObject genre=genres.get(genreId);
            String genreTitle=first(genre,"title","name");
            if(genreTitle.isEmpty()) genreTitle=first(nested,"title","name");
            if(genreTitle.isEmpty()) genreTitle=first(o,"category_name","genre_name","tv_genre_name","category_title","genre_title","genre","category");
            if(genreTitle.isEmpty()) genreTitle="Të tjera";
            String genreCountry=country(genre); if(genreCountry.isEmpty()) genreCountry=country(nested);
            String name=first(o,"name","title"), providerCountry=country(o);
            String code=StalkerCountryResolver.resolve(providerCountry,genreCountry,genreTitle,name);
            // A mixed genre must split by country without losing the original provider ID.
            String visibleId="stalker:"+(genreId.isEmpty()?genreTitle:genreId)+":"+code;
            if(!visible.containsKey(visibleId)) {
                Category category=new Category(visibleId,genreTitle,CatalogType.LIVE,code);
                visible.put(visibleId,category); catalog.categories.add(category);
            }
            Map<String,String> meta=new LinkedHashMap<>(ProviderMetadata.read(o));
            meta.put("stalker_original_cmd",first(o,"cmd","url"));
            meta.put("stalker_genre_id",genreId); meta.put("stalker_genre_title",genreTitle);
            meta.put("stalker_provider_country",providerCountry); meta.put("stalker_genre_country",genreCountry);
            meta.put("stalker_country_code",code);
            // Direct StreamItem construction deliberately avoids changing the shared LIVE factory.
            catalog.items.add(new StreamItem(id,name,visibleId,first(o,"logo","screenshot_uri","stream_icon"),
                    first(o,"cmd","url"),first(o,"xmltv_id","epg_id","tvg_id"),CatalogType.LIVE,
                    Collections.emptyList(),meta));
        }
    }
}
