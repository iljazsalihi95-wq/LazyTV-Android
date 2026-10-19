package de.lazytv.pro.ai;

import org.json.*;
import java.util.*;

/** Provider-declared tracks only; labels never expose signed URLs or credentials. */
public final class ExternalSubtitleTracks {
    private ExternalSubtitleTracks() {}
    public static final class Track {
        public final String url, label;
        private Track(String url, String label) { this.url=url; this.label=label; }
    }
    public static List<Track> read(Map<String,String> metadata) {
        LinkedHashMap<String,Track> tracks=new LinkedHashMap<>();
        if(metadata==null)return new ArrayList<>();
        // Read rich metadata before the normalized first-URL alias, preserving language labels.
        for(String key:new String[]{"subtitles","subtitle","subtitles_url","srt","vtt","subtitle_url"}) {
            String raw=metadata.get(key);
            if(raw==null||raw.trim().isEmpty())continue;
            if(http(raw.trim())){add(tracks,raw);continue;}
            try { add(tracks,new JSONTokener(raw).nextValue()); }
            catch(JSONException ignored) { add(tracks,raw); }
        }
        return new ArrayList<>(tracks.values());
    }
    private static void add(Map<String,Track> out,Object raw) {
        if(raw instanceof JSONArray) {
            JSONArray array=(JSONArray)raw;
            for(int i=0;i<array.length();i++)add(out,array.opt(i));
            return;
        }
        String url="",label="";
        if(raw instanceof JSONObject) {
            JSONObject object=(JSONObject)raw;
            for(String key:new String[]{"url","file","src","subtitle_url"}) {
                String value=object.optString(key,"").trim();
                if(http(value)){url=value;break;}
            }
            for(String key:new String[]{"label","language","lang","name"}) {
                String value=object.optString(key,"").trim();
                if(!value.isEmpty()&&!value.equalsIgnoreCase("null")&&!value.contains("://")){label=value;break;}
            }
        } else if(raw instanceof String)url=((String)raw).trim();
        if(!http(url)||out.containsKey(url))return;
        if(label.isEmpty())label="External subtitle "+(out.size()+1);
        out.put(url,new Track(url,label));
    }
    private static boolean http(String value) {
        return value.startsWith("http://")||value.startsWith("https://");
    }
}
