package de.lazytv.pro.catalog;
import android.content.Context;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class BuiltInCatalogSource{
 public static final String SOURCE_ID="LAZYTV_TRIAL";
 public Catalog load(Context c)throws CatalogException{
  Catalog out=new Catalog();LinkedHashMap<String,Category> cats=new LinkedHashMap<>();
  try(InputStream in=c.getAssets().open("lazytv_channels.json")){
   ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);
   JSONArray a=new JSONObject(b.toString(StandardCharsets.UTF_8.name())).getJSONArray("channels");java.util.HashSet<String> stableKeys=new java.util.HashSet<>();for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);String id=x.optString("id",""),name=x.optString("name",""),cat=x.optString("category","");if(!id.startsWith("CONNECTRA_"))stableKeys.add((cat+"|"+name).toLowerCase(java.util.Locale.US));}
   for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);String url=x.optString("url","").trim();if(url.isEmpty())continue;String id0=x.optString("id",""),key0=(x.optString("category","")+"|"+x.optString("name","")).toLowerCase(java.util.Locale.US);if(id0.startsWith("CONNECTRA_")&&url.contains("play_token=")&&stableKeys.contains(key0))continue;
    String country=x.optString("country","XX"),cn=x.optString("category","Tjera"),cid=SOURCE_ID+"|"+country+"|"+cn;url=normalizeTrialUrl(url);
    if(!cats.containsKey(cid)){Category cat=new Category(cid,cn,CatalogType.LIVE,country);cats.put(cid,cat);out.categories.add(cat);}
    out.items.add(new StreamItem(x.optString("id",String.valueOf(i)),x.optString("name","Channel"),cid,x.optString("logo",""),url,"",CatalogType.LIVE));
   }
  }catch(Exception e){throw new CatalogException("LazyTV built-in catalog error: "+e.getMessage());}
  return out;
 }
 private static String normalizeTrialUrl(String url){
  // Preserve the real provider URL. TS is a playback/container type, not a filename we may invent.
  return url==null?"":url.trim();
 }
}