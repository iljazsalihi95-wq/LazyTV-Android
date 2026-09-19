package de.lazytv.pro.catalog;
import java.util.*;import java.util.regex.*;
public final class CountryResolver {
 private static final Pattern[] P={Pattern.compile("^\\s*\\[([A-Za-z]{2,3})\\]\\s*(.*)$"),Pattern.compile("^\\s*([A-Za-z]{2,3})\\s*[|:•»>-]\\s*(.+)$"),Pattern.compile("^\\s*([A-Za-z]{2,3})\\s+[-–—]\\s+(.+)$")};
 private CountryResolver(){}
 public static String key(String name){if(name==null)return"OTHER";for(Pattern p:P){Matcher m=p.matcher(name);if(m.matches())return m.group(1).toUpperCase(Locale.ROOT);}return"OTHER";}
 public static String categoryLabel(String name){if(name==null)return"";for(Pattern p:P){Matcher m=p.matcher(name);if(m.matches()&&!m.group(2).trim().isEmpty())return m.group(2).trim();}return name.trim();}
 public static List<CountryGroup> groups(List<Category> categories){LinkedHashMap<String,CountryGroup> map=new LinkedHashMap<>();for(Category c:categories){String k=key(c.name);CountryGroup g=map.get(k);if(g==null){g=new CountryGroup(k,k.equals("OTHER")?"Other":k);map.put(k,g);}g.categories.add(c);}return new ArrayList<>(map.values());}
}