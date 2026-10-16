package de.lazytv.pro.stb;

import java.text.Normalizer;
import java.util.*;

/** Display-only mapping. Provider IDs, category titles and full channel names stay unchanged. */
public final class StbDisplay {
    private static final Map<String,String> CODES=new LinkedHashMap<>(),NAMES=new LinkedHashMap<>();
    static {
        for(String code:Locale.getISOCountries()){
            Locale country=new Locale("",code);CODES.put(code,code);
            try{CODES.put(country.getISO3Country(),code);}catch(MissingResourceException ignored){}
            for(Locale language:new Locale[]{Locale.ENGLISH,new Locale("sq"),Locale.GERMAN,new Locale("sr"),new Locale("hr"),Locale.FRENCH}){
                String name=clean(country.getDisplayCountry(language));if(!name.isEmpty())NAMES.put(name,code);
            }
        }
        for(String alias:new String[]{"XK","RKS","KS","XKX"})CODES.put(alias,"XK");CODES.put("UK","GB");
        for(String alias:new String[]{"KOSOVO","KOSOVA","KOSOVE"})NAMES.put(alias,"XK");
        NAMES.put("MACEDONIA","MK");NAMES.put("MAQEDONIA","MK");NAMES.put("SHQIPERI","AL");NAMES.put("SHQIPERIA","AL");NAMES.put("BOSNIA","BA");
    }
    private StbDisplay(){}
    private static String clean(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]+"," ").trim();}
    private static String exact(String text){String s=clean(text);return CODES.containsKey(s)?CODES.get(s):NAMES.get(s);}
    private static String metadata(String text){
        String s=clean(text),e=exact(s);if(e!=null)return e;
        String padded=" "+s+" ";
        // Prefer explicit Kosovo wording when a provider combines Albania/Kosovo in one genre.
        for(String k:new String[]{"KOSOVO","KOSOVA","KOSOVE"})if(padded.contains(" "+k+" "))return "XK";
        String match=null;int longest=0;
        for(Map.Entry<String,String> n:NAMES.entrySet())if(n.getKey().length()>longest&&padded.contains(" "+n.getKey()+" ")){match=n.getValue();longest=n.getKey().length();}
        if(match!=null)return match;
        String[] words=s.split(" +");for(String word:words)if(CODES.containsKey(word))return CODES.get(word);
        return null;
    }
    public static String country(StbProtocol.Channel channel,StbProtocol.Genre genre){
        String result=exact(channel.country);if(result!=null)return result;
        if(genre!=null){result=exact(genre.country);if(result!=null)return result;result=metadata(genre.title);if(result!=null)return result;}
        result=metadata(channel.name);return result==null?"OTHER":result;
    }
    public static String abbreviation(String title){
        String s=clean(title);Set<String> words=new HashSet<>(Arrays.asList(s.split(" +")));
        for(String word:new String[]{"GENERAL","DOCUMENTARY","DOCUMENTARIES","MOVIES","MOVIE","CINEMA","SPORT","SPORTS","NEWS","KIDS","MUSIC","RELIGION","ISLAM"})if(words.contains(word)){
            switch(word){case "GENERAL":return "GEN";case "DOCUMENTARY":case "DOCUMENTARIES":return "DOK";case "MOVIES":case "MOVIE":case "CINEMA":return "FILM";case "SPORTS":case "SPORT":return "SPORT";case "RELIGION":case "ISLAM":return "ISLAM";default:return word;}
        }
        String[] tokens=s.split(" +");if(tokens.length>1&&CODES.containsKey(tokens[0]))s=s.substring(tokens[0].length()).trim();
        return s.isEmpty()?"OTHER":s;
    }
}
