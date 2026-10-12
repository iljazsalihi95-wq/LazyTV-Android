package de.lazytv.pro.catalog;
import java.net.URL;
import java.util.regex.*;
/** Extract provider commands without rewriting signed query strings or transport. */
final class StalkerLinkParser {
 static String url(String command,String endpoint)throws CatalogException {
  if(command==null||command.trim().isEmpty())return "";
  String value=command.trim().replaceFirst("^(?i)(ffmpeg|auto|ffrt)\\s+", "").trim();
  Matcher quoted=Pattern.compile("^[\"']([^\"']+)[\"'](?:\\s.*)?$").matcher(value);
  if(quoted.matches())value=quoted.group(1);
  else {Matcher url=Pattern.compile("https?://[^\\s\"']+").matcher(value);if(url.find())value=url.group();}
  try {
   URL result=new URL(new URL(endpoint),value);
   if(!result.getProtocol().equalsIgnoreCase("http")&&!result.getProtocol().equalsIgnoreCase("https"))throw new Exception();
   if(result.getHost().isEmpty())throw new Exception();
   return result.toExternalForm();
  }catch(Exception e){throw new CatalogException("Portali ktheu URL stream-i të pavlefshme");}
 }
}
