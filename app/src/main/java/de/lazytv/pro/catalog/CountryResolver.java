package de.lazytv.pro.catalog;
import java.util.*;import java.util.regex.*;
public final class CountryResolver {
 private static final Pattern[] P={Pattern.compile("^\\s*\\[([A-Za-z]{2,3})\\]\\s*(.*)$"),Pattern.compile("^\\s*([A-Za-z]{2,3})\\s*[|:•»>-]\\s*(.+)$"),Pattern.compile("^\\s*([A-Za-z]{2,3})\\s+[-–—]\\s+(.+)$")};
 private static final LinkedHashMap<String,String[]> WORDS=new LinkedHashMap<>();
 static{
  WORDS.put("AL",new String[]{"ALBANIA","ALBANIAN","SHQIP","SHQIPERIA","KOSOVA","KOSOVO"});
  WORDS.put("TR",new String[]{"TURKEY","TURKISH","TURK","TURKIYE"});
  WORDS.put("DE",new String[]{"GERMANY","GERMAN","DEUTSCH","DEUTSCHLAND"});
  WORDS.put("IT",new String[]{"ITALY","ITALIAN","ITALIA"});
  WORDS.put("MK",new String[]{"MACEDONIA","MACEDONIAN","MAQEDONI"});
  WORDS.put("XK",new String[]{"KOSOVAR"});
  WORDS.put("GB",new String[]{"UK ","UNITED KINGDOM","BRITISH","ENGLISH"});
  WORDS.put("US",new String[]{"USA","UNITED STATES","AMERICAN"});
  WORDS.put("FR",new String[]{"FRANCE","FRENCH"});
  WORDS.put("ES",new String[]{"SPAIN","SPANISH"});
  WORDS.put("GR",new String[]{"GREECE","GREEK"});
  WORDS.put("BA",new String[]{"BOSNIA","BOSNIAN"});
  WORDS.put("RS",new String[]{"SERBIA","SERBIAN"});
  WORDS.put("HR",new String[]{"CROATIA","CROATIAN"});
 }
 private CountryResolver(){}
 public static String key(String name){
  if(name==null)return"OTHER";
  for(Pattern p:P){Matcher m=p.matcher(name);if(m.matches())return normalize(m.group(1));}
  String u=(" "+name.toUpperCase(Locale.ROOT).replace('_',' ').replace('-',' ')+" ").replaceAll("\\s+"," ");
  for(Map.Entry<String,String[]>e:WORDS.entrySet())for(String w:e.getValue())if(u.contains(" "+w+" ")||u.startsWith(" "+w+" ")||u.contains(" "+w+":")||u.contains(" "+w+" |"))return e.getKey();
  return"OTHER";
 }
 private static String normalize(String raw){String k=raw.toUpperCase(Locale.ROOT);if("ALB".equals(k))return"AL";if("KOS".equals(k)||"XKX".equals(k))return"XK";if("MKD".equals(k))return"MK";if("DEU".equals(k))return"DE";if("ITA".equals(k))return"IT";if("TUR".equals(k))return"TR";return k;}
 public static String categoryLabel(String name){if(name==null)return"";for(Pattern p:P){Matcher m=p.matcher(name);if(m.matches()&&!m.group(2).trim().isEmpty())return m.group(2).trim();}return name.trim();}
 public static List<CountryGroup> groups(List<Category> categories){LinkedHashMap<String,CountryGroup> map=new LinkedHashMap<>();for(Category c:categories){String k=key(c.name);CountryGroup g=map.get(k);if(g==null){g=new CountryGroup(k,k.equals("OTHER")?"Other":k);map.put(k,g);}g.categories.add(c);}return new ArrayList<>(map.values());}
}