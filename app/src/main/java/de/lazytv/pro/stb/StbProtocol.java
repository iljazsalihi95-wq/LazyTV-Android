package de.lazytv.pro.stb;

import java.net.URI;
import java.util.*;
import org.json.*;

/** Protocol-only DTOs: no dependency on LazyTV catalogs, playlists or players. */
public final class StbProtocol {
    private StbProtocol() {}
    public static List<String> endpoints(String input) {
        try {
            String raw=input.trim();
            if(raw.contains("://") && !raw.matches("(?i)^https?://.*")) throw new Exception();
            if(!raw.matches("(?i)^https?://.*")) raw="http://"+raw;
            URI u=new URI(raw);
            if(u.getHost()==null || u.getUserInfo()!=null || !("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))) throw new Exception();
            String origin=u.getScheme().toLowerCase(Locale.ROOT)+"://"+u.getRawAuthority();
            String p=u.getPath().replaceAll("/+$","");
            LinkedHashSet<String> result=new LinkedHashSet<>();
            if(p.endsWith(".php")) {
                result.add(origin+p);
                p=p.replaceFirst("/(?:server/load|portal|load)\\.php$","");
                if(p.endsWith(".php")) throw new Exception();
            }
            if(p.endsWith("/c")) p=p.substring(0,p.length()-2);
            result.add(origin+p+"/portal.php");
            result.add(origin+p+"/server/load.php");
            result.add(origin+"/portal.php");
            result.add(origin+"/server/load.php");
            result.add(origin+"/stalker_portal/server/load.php");
            return new ArrayList<>(result);
        } catch(Exception e) { throw new IllegalArgumentException("Portal URL nuk është valid"); }
    }
    public static Object payload(String text) throws JSONException {
        String s=text.trim().replaceFirst("^\\uFEFF", "");
        if(s.startsWith("var ")) { int i=s.indexOf('='); s=s.substring(i+1).trim().replaceFirst(";\\s*$",""); }
        Object root=new JSONTokener(s).nextValue();
        if(root instanceof JSONObject && ((JSONObject)root).has("js")) return ((JSONObject)root).get("js");
        return root;
    }
    public static String token(Object payload) {
        return payload instanceof JSONObject ? ((JSONObject)payload).optString("token", "").trim() : "";
    }
    public static JSONArray rows(Object payload) {
        if(payload instanceof JSONArray) return (JSONArray)payload;
        if(payload instanceof JSONObject) {
            JSONObject o=(JSONObject)payload;
            for(String k:new String[]{"data","channels","genres"}) if(o.optJSONArray(k)!=null) return o.optJSONArray(k);
        }
        return new JSONArray();
    }
    public static final class Genre {
        public final String id,title,country;
        Genre(JSONObject o) { id=o.optString("id");title=o.optString("title",o.optString("name"));country=o.optString("country_code",o.optString("country")); }
    }
    public static final class Channel {
        public final String id,name,genreId,cmd,logo,epgId,country;
        Channel(JSONObject o) {
            id=o.optString("id"); name=o.optString("name"); genreId=o.optString("tv_genre_id",o.optString("genre_id",o.optString("category_id")));
            cmd=o.optString("cmd");logo=o.optString("logo");epgId=o.optString("xmltv_id",o.optString("epg_id"));country=o.optString("country_code",o.optString("country"));
        }
    }
    public static List<Genre> genres(Object payload) {
        List<Genre> out=new ArrayList<>();JSONArray a=rows(payload);
        for(int i=0;i<a.length();i++) { JSONObject o=a.optJSONObject(i);if(o!=null) out.add(new Genre(o)); }
        return out;
    }
    public static List<Channel> channels(Object payload) {
        List<Channel> out=new ArrayList<>();JSONArray a=rows(payload);
        for(int i=0;i<a.length();i++) { JSONObject o=a.optJSONObject(i);if(o!=null) out.add(new Channel(o)); }
        return out;
    }
    public static String link(Object payload, String endpoint) {
        String cmd=payload instanceof JSONObject ? ((JSONObject)payload).optString("cmd",((JSONObject)payload).optString("url")) : payload instanceof String?(String)payload:"";
        cmd=cmd.trim().replace("\\/","/").replace("&amp;","&").replaceFirst("(?i)^(ffmpeg|ffrt|auto)\\s+","").trim();
        if(cmd.startsWith("\"") && cmd.endsWith("\"")) cmd=cmd.substring(1,cmd.length()-1);
        try {
            if(cmd.isEmpty()) throw new Exception();
            URI uri=new URI(cmd);if(!uri.isAbsolute()) uri=new URI(endpoint).resolve(uri);
            if(!Arrays.asList("http","https","rtsp","rtmp","udp","rtp").contains(uri.getScheme())) throw new Exception();
            if(uri.getHost()==null) throw new Exception();
            return uri.toASCIIString();
        } catch(Exception e) {throw new IllegalArgumentException("create_link nuk ktheu stream URL valid");}
    }
}
