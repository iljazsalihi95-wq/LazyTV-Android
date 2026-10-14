package de.lazytv.pro.catalog;
import java.util.Locale;
/** Managed catalogs only: shared Premium/personal provider country behavior is unchanged. */
public final class ManagedCountryResolver {
 private ManagedCountryResolver(){}
 public static String resolve(String code,String country,String category,String name){
  String explicit=(category+" "+name).toUpperCase(Locale.ROOT);
  boolean mixed=explicit.contains("ALBANIA + KOSOVO")||explicit.contains("ALBANIA/KOSOVO");
  if(!mixed&&explicit.matches("(?s).*\\b(KOSOVO|KOSOVA|KOSOVË|XK|RKS)\\b.*"))return "XK";
  return StalkerCountryResolver.resolve(code,country,category,name);
 }
}
