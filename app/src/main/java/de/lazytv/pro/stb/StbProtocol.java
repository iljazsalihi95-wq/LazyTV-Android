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
    public enum Kind { LIVE, MOVIE, SERIES, SEASON, EPISODE, FILE }
    static boolean flag(JSONObject o,String key){Object v=o.opt(key);return Boolean.TRUE.equals(v)||"1".equals(String.valueOf(v))||"true".equalsIgnoreCase(String.valueOf(v));}
    static String value(JSONObject o,String key,String fallback){Object v=o.opt(key);return v==null||v==JSONObject.NULL?fallback:String.valueOf(v);}
    public static final class Channel {
        public final String id,name,genreId,cmd,logo,epgId,country,protocol,movieId,seasonId,episodeId,episodeNumber,plot;
        public final Kind kind;
        public final JSONArray episodeNumbers;
        Channel(JSONObject o){this(o,"itv",Kind.LIVE);}
        Channel(JSONObject o,String protocol,Kind fallback){
            this.protocol=protocol;id=value(o,"id","");name=value(o,"name",value(o,"title",""));genreId=value(o,"tv_genre_id",value(o,"genre_id",value(o,"category_id","")));
            cmd=value(o,"cmd","");logo=value(o,"logo",value(o,"screenshot_uri",value(o,"cover","")));epgId=value(o,"xmltv_id",value(o,"epg_id",""));country=value(o,"country_code",value(o,"country",""));
            movieId=value(o,"movie_id",value(o,"video_id",""));seasonId=value(o,"season_id","");episodeId=value(o,"episode_id","");episodeNumber=value(o,"series_number",value(o,"episode_number",""));plot=value(o,"description",value(o,"plot",""));episodeNumbers=o.optJSONArray("series")==null?new JSONArray():o.optJSONArray("series");
            String type=value(o,"item_type",value(o,"type","")).toLowerCase(Locale.ROOT);
            kind=flag(o,"is_file")?Kind.FILE:flag(o,"is_episode")?Kind.EPISODE:flag(o,"is_season")?Kind.SEASON:flag(o,"is_series")||type.equals("series")?Kind.SERIES:type.equals("vod")||type.equals("movie")?Kind.MOVIE:fallback;
        }
        public boolean linear(){return kind==Kind.LIVE;}
        public boolean playable(){return (kind==Kind.LIVE||kind==Kind.MOVIE||kind==Kind.FILE||kind==Kind.EPISODE)&&!cmd.trim().isEmpty();}
        public Channel numberedEpisode(String number){try{JSONObject o=new JSONObject().put("id",id+":"+number).put("name",name+" • Episode "+number).put("cmd",cmd).put("series_number",number).put("is_episode",true);return new Channel(o,protocol,Kind.EPISODE);}catch(JSONException e){throw new IllegalArgumentException("Episodi STB jo valid");}}
    }
    public static final class Page {
        public final List<Channel> items=new ArrayList<>();public final int total,pageSize;
        public Page(Object payload,String protocol,Kind fallback){JSONArray rows=rows(payload);for(int i=0;i<rows.length();i++){JSONObject o=rows.optJSONObject(i);if(o!=null)items.add(new Channel(o,protocol,fallback));}JSONObject meta=payload instanceof JSONObject?(JSONObject)payload:new JSONObject();total=meta.optInt("total_items",items.size());pageSize=meta.optInt("max_page_items",items.size());}
        public boolean hasNext(int page){return !items.isEmpty()&&pageSize>0&&(long)page*pageSize<total;}
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
