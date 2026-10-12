package de.lazytv.pro.catalog;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Provider-first country resolution used exclusively by Stalker LIVE. */
public final class StalkerCountryResolver {
    private static final Map<String,String> CODES = new HashMap<>(), NAMES = new HashMap<>();
    static {
        for (String code : Locale.getISOCountries()) {
            CODES.put(code, code);
            Locale country = new Locale("", code);
            try { CODES.put(country.getISO3Country(), code); } catch (Exception ignored) {}
            for (Locale language : new Locale[]{Locale.ENGLISH, Locale.GERMAN, new Locale("sq"), Locale.FRENCH})
                NAMES.put(fold(country.getDisplayCountry(language)), code);
        }
        for (String alias : new String[]{"XK", "XKX", "RKS", "KS", "KOS"}) CODES.put(alias, "XK");
        CODES.put("UK", "GB");
        NAMES.put("KOSOVO", "XK"); NAMES.put("KOSOVA", "XK");
        NAMES.put("MACEDONIA", "MK"); NAMES.put("MAQEDONI E VERIUT", "MK");
        NAMES.put("SHQIPERI", "AL"); NAMES.put("GJERMANI", "DE");
        NAMES.put("GREAT BRITAIN", "GB"); NAMES.put("USA", "US");
    }
    private StalkerCountryResolver() {}
    private static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }
    public static String normalize(String value) {
        String key = fold(value), code = CODES.get(key);
        if (code == null) code = NAMES.get(key);
        return code == null ? "OTHER" : code;
    }
    static String title(String value) {
        String raw = value == null ? "" : value;
        // Flags are explicit provider country markers too.
        int first = -1;
        for (int i=0;i<raw.length();) {
            int cp=raw.codePointAt(i); i+=Character.charCount(cp);
            if(cp>=0x1F1E6 && cp<=0x1F1FF) {
                if(first>=0) return normalize(""+(char)('A'+first-0x1F1E6)+(char)('A'+cp-0x1F1E6));
                first=cp;
            } else first=-1;
        }
        String key=fold(raw), direct=normalize(key);
        if(!"OTHER".equals(direct)) return direct;
        // Ignore an unrecognized prefix rather than discarding the remainder of the title.
        String best="OTHER"; int length=0;
        for(Map.Entry<String,String> name:NAMES.entrySet())
            if(name.getKey().length()>length && (" "+key+" ").contains(" "+name.getKey()+" ")) {
                best=name.getValue(); length=name.getKey().length();
            }
        if(!"OTHER".equals(best)) return best;
        Matcher prefix=Pattern.compile("^(?:\\[[ ]*)?([A-Za-z]{2,3})(?:[ ]*\\]|[ ]*[|:•»>_–—-]|[ ]+)").matcher(raw.trim());
        return prefix.find()?normalize(prefix.group(1)):"OTHER";
    }
    public static List<CountryGroup> groups(List<Category> categories) {
        Map<String,CountryGroup> groups=new LinkedHashMap<>();
        for(Category category:categories){String key=normalize(category.country);if("OTHER".equals(key))key=title(category.name);
            CountryGroup group=groups.get(key);if(group==null){group=new CountryGroup(key,label(key));groups.put(key,group);}group.categories.add(category);
        }
        List<CountryGroup> result=new ArrayList<>(groups.values());result.sort((a,b)->"OTHER".equals(a.key)?("OTHER".equals(b.key)?0:1):"OTHER".equals(b.key)?-1:a.label.compareToIgnoreCase(b.label));return result;
    }
    private static String label(String code) {
        if("OTHER".equals(code))return "🌐 Të tjera";
        String name="XK".equals(code)?"Kosovë":new Locale("",code).getDisplayCountry(new Locale("sq"));
        return new String(Character.toChars(0x1F1E6+code.charAt(0)-'A'))+new String(Character.toChars(0x1F1E6+code.charAt(1)-'A'))+" "+name+" - "+code;
    }
    public static String resolve(String channelCountry, String genreCountry, String genreTitle, String channelName) {
        for(String code:new String[]{normalize(channelCountry),normalize(genreCountry),title(genreTitle),title(channelName)})
            if(!"OTHER".equals(code)) return code;
        return "OTHER";
    }
}
