package de.lazytv.pro.catalog;

import org.json.*;
import java.util.*;

/** Only fields actually returned by the provider; no invented catalogue facts. */
final class ProviderMetadata {
    static Map<String,String> read(JSONObject o) {
        Map<String,String> m=new LinkedHashMap<>();
        field(m,o,"plot","plot","description","overview","storyline");
        field(m,o,"year","year","releasedate","release_date");
        field(m,o,"rating","rating","imdb_rating");
        field(m,o,"genre","genre","genres");
        field(m,o,"duration","duration","time");
        field(m,o,"duration_secs","duration_secs");
        field(m,o,"country","country","countries");
        field(m,o,"director","director","directors");
        field(m,o,"cast","cast","actors");
        field(m,o,"poster","movie_image","cover_big","cover","screenshot_uri","logo");
        field(m,o,"backdrop","backdrop_path","backdrop","backdrop_url");
        subtitleFields(m,o);
        return m;
    }
    static void subtitleFields(Map<String,String> out,JSONObject o){
        if(o==null)return;
        for(String key:new String[]{"subtitle_url","subtitles_url","subtitles","subtitle","srt","vtt"}){
            Object v=o.opt(key);if(v==null||v==JSONObject.NULL)continue;
            String raw=v instanceof String?(String)v:String.valueOf(v);if(raw!=null&&!raw.trim().isEmpty())out.put(key,raw.trim());
            if(!out.containsKey("subtitle_url")){
                String direct=subtitleUrl(v);if(!direct.isEmpty())out.put("subtitle_url",direct);
            }
        }
    }
    private static String subtitleUrl(Object raw){
        if(raw==null||raw==JSONObject.NULL)return "";
        if(raw instanceof String){String s=((String)raw).trim();return http(s)?s:"";}
        if(raw instanceof JSONObject){JSONObject o=(JSONObject)raw;for(String key:new String[]{"url","file","src","subtitle_url"}){String s=o.optString(key,"").trim();if(http(s))return s;}return "";}
        if(raw instanceof JSONArray){JSONArray a=(JSONArray)raw;for(int i=0;i<a.length();i++){String s=subtitleUrl(a.opt(i));if(!s.isEmpty())return s;}}
        return "";
    }
    private static boolean http(String s){return s!=null&&(s.startsWith("https://")||s.startsWith("http://"));}
    private static void field(Map<String,String> m,JSONObject o,String name,String... keys) {
        if(o==null)return;
        for(String key:keys){Object raw=o.opt(key);if(raw==null||raw==JSONObject.NULL)continue;
            String value="";
            if(raw instanceof JSONArray){JSONArray a=(JSONArray)raw;List<String> parts=new ArrayList<>();
                for(int n=0;n<a.length();n++){Object entry=a.opt(n);if(entry==null||entry==JSONObject.NULL)continue;
                    String part=entry instanceof JSONObject?((JSONObject)entry).optString("name",((JSONObject)entry).optString("title")):String.valueOf(entry);
                    if(!part.trim().isEmpty())parts.add(part.trim());
                    if(name.equals("backdrop")&&!parts.isEmpty())break;
                }StringBuilder joined=new StringBuilder();for(String part:parts){if(joined.length()>0)joined.append(", ");joined.append(part);}value=joined.toString();
            }else if(!(raw instanceof JSONObject))value=String.valueOf(raw).trim();
            if(!value.isEmpty()&&!value.equalsIgnoreCase("null")){m.put(name,value);return;}
        }
    }
}
