package de.lazytv.pro.catalog;
import java.util.*;import java.util.regex.*;
public final class CountryResolver {
 private static final Pattern[] P={Pattern.compile("^\\s*\\[([A-Za-z]{2,3})\\]\\s*(.*)$"),Pattern.compile("^\\s*([A-Za-z]{2,3})\\s*[|:•»>-]\\s*(.+)$"),Pattern.compile("^\\s*([A-Za-z]{2,3})\\s+[-–—]\\s+(.+)$")};
 private CountryResolver(){}
 public static String key(Category c){if(c!=null&&c.country!=null&&!c.country.trim().isEmpty())return normalize(c.country);return key(c==null?null:c.name);}
 public static String key(String name){if(name==null)return"OTHER";for(Pattern p:P){Matcher m=p.matcher(name);if(m.matches())return normalize(m.group(1));}String u=name.trim().toUpperCase(Locale.ROOT);if(u.equals("MACEDONIA")||u.equals("NORTH MACEDONIA")||u.equals("MAQEDONIA")||u.equals("MAQEDONIA E VERIUT"))return"MK";return"OTHER";}
 private static String normalize(String raw){String k=raw.trim().toUpperCase(Locale.ROOT);if("ALB".equals(k))return"AL";if("KOS".equals(k)||"XKX".equals(k)||"XK".equals(k))return"KS";if("MKD".equals(k)||"MACEDONIA".equals(k)||"NORTH MACEDONIA".equals(k)||"MAQEDONIA".equals(k)||"MAQEDONIA E VERIUT".equals(k))return"MK";if("DEU".equals(k))return"DE";if("ITA".equals(k))return"IT";if("TUR".equals(k))return"TR";return k;}
 public static String categoryLabel(String name){if(name==null)return"";for(Pattern p:P){Matcher m=p.matcher(name);if(m.matches()&&!m.group(2).trim().isEmpty())return m.group(2).trim();}return name.trim();}
 private static String label(String k){if("MK".equals(k))return"🇲🇰 Maqedonia e Veriut";if("AL".equals(k))return"🇦🇱 Shqipëria";if("KS".equals(k))return"🇽🇰 Kosova";if("OTHER".equals(k))return"Other";return k;} public static List<CountryGroup> groups(List<Category> categories){LinkedHashMap<String,CountryGroup> map=new LinkedHashMap<>();for(Category c:categories){String k=key(c);CountryGroup g=map.get(k);if(g==null){g=new CountryGroup(k,label(k));map.put(k,g);}g.categories.add(c);}return new ArrayList<>(map.values());}
}