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
  String exact=LOCAL_NAMES.get(fold(value));if(exact!=null)return exact;
  // Reuse existing aliases read-only, without changing the shared resolver or its consumers.
  String legacy=CountryResolver.key(new Category("",value,CatalogType.LIVE,value));
  if("EXYU".equals(legacy))return legacy;iso=StalkerCountryResolver.normalize(legacy);if(!"OTHER".equals(iso))return iso;
  return "OTHER";
 }
 public static String resolve(String code,String country,String category,String name){
  String primary="OTHER";for(String value:new String[]{code,country}){String found=provider(value);if(!"OTHER".equals(found)){primary=found;break;}}
  // Correct old AL imports only with unambiguous Kosovo evidence; a mixed genre alone
  // must not move every Albanian channel. A Kosovo-only channel name still takes priority.
  boolean kosovo=clearKosovo(country)||clearKosovo(category)||clearKosovo(name);
  if(kosovo&&("AL".equals(primary)||"OTHER".equals(primary)||"EXYU".equals(primary)))return "XK";
  if(!"OTHER".equals(primary))return primary;
  String categoryCountry=StalkerCountryResolver.resolve("","",category,name);if(!"OTHER".equals(categoryCountry))return categoryCountry;
  String regional=CountryResolver.key(category);return "EXYU".equals(regional)?regional:"OTHER";
 }
 private static boolean clearKosovo(String value){String text=fold(value);boolean kosovo=text.matches("(?s).*\\b(KOSOVO|KOSOVA|KOSOVE|XK|RKS|KS)\\b.*");boolean albania=text.matches("(?s).*\\b(ALBANIA|SHQIPERI|SHQIPERIA)\\b.*");return kosovo&&!albania;}
 public static List<CountryGroup> groups(List<Category> categories){List<Category> iso=new ArrayList<>();CountryGroup regional=new CountryGroup("EXYU","🌐 EXYU");for(Category c:categories){if("EXYU".equals(c.country))regional.categories.add(c);else iso.add(c);}List<CountryGroup> groups=StalkerCountryResolver.groups(iso);if(!regional.categories.isEmpty())groups.add(0,regional);return groups;}
}
