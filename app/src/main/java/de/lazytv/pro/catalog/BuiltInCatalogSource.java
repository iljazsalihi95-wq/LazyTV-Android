package de.lazytv.pro.catalog;
import android.content.Context;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class BuiltInCatalogSource{
 public Catalog load(Context c)throws CatalogException{
  Catalog out=new Catalog();LinkedHashMap<String,Category> cats=new LinkedHashMap<>();
  try(InputStream in=c.getAssets().open("lazytv_channels.json")){
   ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);
   JSONArray a=new JSONObject(b.toString(StandardCharsets.UTF_8.name())).getJSONArray("channels");
   for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);String url=x.optString("url","").trim();if(url.isEmpty())continue;
    String country=x.optString("country","XX"),cn=x.optString("category","Tjera"),cid=country+"|"+cn;
    if(!cats.containsKey(cid)){Category cat=new Category(cid,country+" • "+cn,CatalogType.LIVE);cats.put(cid,cat);out.categories.add(cat);}
    out.items.add(new StreamItem(x.optString("id",String.valueOf(i)),x.optString("name","Channel"),cid,x.optString("logo",""),url,"",CatalogType.LIVE));
   }
  }catch(Exception e){throw new CatalogException("LazyTV built-in catalog error: "+e.getMessage());}
  return out;
 }
}