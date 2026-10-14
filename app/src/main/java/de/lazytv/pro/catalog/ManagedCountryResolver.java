package de.lazytv.pro.catalog;
import java.text.Normalizer;
import java.util.*;
/** Managed catalogs only: shared Premium/personal provider country behavior is unchanged. */
public final class ManagedCountryResolver {
 private static final Map<String,String> LOCAL_NAMES=new HashMap<>();
 static {for(String code:Locale.getISOCountries()){String name=fold(new Locale("",code).getDisplayCountry(new Locale("sq")));LOCAL_NAMES.put(name,code);LOCAL_NAMES.put(name+"A",code);}LOCAL_NAMES.put("MALI I ZI","ME");}
 private ManagedCountryResolver(){}
 private static String fold(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).trim();}
 private static String provider(String value){
  String iso=StalkerCountryResolver.normalize(value);if(!"OTHER".equals(iso))return iso;
  // Reuse existing aliases read-only, without changing the shared resolver or its consumers.
  String legacy=CountryResolver.key(new Category("",value,CatalogType.LIVE,value));
  if("EXYU".equals(legacy))return legacy;iso=StalkerCountryResolver.normalize(legacy);if(!"OTHER".equals(iso))return iso;
  return LOCAL_NAMES.getOrDefault(fold(value),"OTHER");
 }
 public static String resolve(String code,String country,String category,String name){
  String explicit=(category+" "+name).toUpperCase(Locale.ROOT);
  boolean mixed=explicit.contains("ALBANIA + KOSOVO")||explicit.contains("ALBANIA/KOSOVO");
  if(!mixed&&explicit.matches("(?s).*\\b(KOSOVO|KOSOVA|KOSOVË|XK|RKS)\\b.*"))return "XK";
  for(String value:new String[]{code,country}){String found=provider(value);if(!"OTHER".equals(found))return found;}
  String categoryCountry=StalkerCountryResolver.resolve("","",category,name);if(!"OTHER".equals(categoryCountry))return categoryCountry;
  String regional=CountryResolver.key(category);return "EXYU".equals(regional)?regional:"OTHER";
 }
 public static List<CountryGroup> groups(List<Category> categories){List<Category> iso=new ArrayList<>();CountryGroup regional=new CountryGroup("EXYU","🌐 EXYU");for(Category c:categories){if("EXYU".equals(c.country))regional.categories.add(c);else iso.add(c);}List<CountryGroup> groups=StalkerCountryResolver.groups(iso);if(!regional.categories.isEmpty())groups.add(0,regional);return groups;}
}
