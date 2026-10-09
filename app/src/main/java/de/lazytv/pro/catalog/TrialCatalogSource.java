package de.lazytv.pro.catalog;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Dedicated LazyTV FREE TV source.
 * FREE TV is intentionally separate from user M3U/Xtream/Stalker providers.
 * Only public playlist endpoints are referenced here; no provider credentials,
 * MAC addresses, private tokens or user stream URLs belong in this source.
 */
public final class TrialCatalogSource {
 private static final String[][] PUBLIC_PLAYLISTS={
  {"AL","https://iptv-org.github.io/iptv/countries/al.m3u"},
  {"XK","https://iptv-org.github.io/iptv/countries/xk.m3u"},
  {"MK","https://iptv-org.github.io/iptv/countries/mk.m3u"}
 };

 public Catalog load(Context context)throws CatalogException{
  Catalog out=new Catalog();
  LinkedHashMap<String,Category> categories=new LinkedHashMap<>();
  int loaded=0;
  StringBuilder errors=new StringBuilder();
  for(String[] source:PUBLIC_PLAYLISTS){
   try{loaded+=loadPlaylist(source[0],source[1],out,categories);}catch(Exception e){
    if(errors.length()>0)errors.append("; ");
    errors.append(source[0]).append(": ").append(e.getClass().getSimpleName());
   }
  }
  if(loaded==0)throw new CatalogException("FREE TV public catalog unavailable"+(errors.length()==0?"":" ("+errors+")"));
  return out;
 }

 private int loadPlaylist(String country,String endpoint,Catalog out,LinkedHashMap<String,Category> categories)throws Exception{
  HttpURLConnection cn=(HttpURLConnection)new URL(endpoint).openConnection();
  cn.setInstanceFollowRedirects(true);
  cn.setConnectTimeout(8000);cn.setReadTimeout(12000);
  cn.setRequestProperty("User-Agent","LazyTV-PRO/1.0");
  cn.setRequestProperty("Accept","application/x-mpegURL,audio/mpegurl,text/plain,*/*;q=0.8");
  try{
   int code=cn.getResponseCode();if(code<200||code>=300)throw new java.io.IOException("HTTP "+code);
   BufferedReader r=new BufferedReader(new InputStreamReader(cn.getInputStream(),StandardCharsets.UTF_8));
   String line,pending=null;int count=0,index=0;
   while((line=r.readLine())!=null){
    line=line.trim();if(line.isEmpty())continue;
    if(line.startsWith("#EXTINF:")){pending=line;continue;}
    if(line.startsWith("#"))continue;
    if(pending==null||!(line.startsWith("http://")||line.startsWith("https://")))continue;
    String name=attributeOrName(pending,"tvg-name");
    if(name.isEmpty()){int comma=pending.lastIndexOf(',');name=comma>=0?pending.substring(comma+1).trim():"Channel";}
    String logo=attribute(pending,"tvg-logo"),tvgId=attribute(pending,"tvg-id"),group=attribute(pending,"group-title");
    if(group.isEmpty())group="FREE TV";
    String categoryId="FREE_PUBLIC|"+country+"|"+group;
    if(!categories.containsKey(categoryId)){
     Category c=new Category(categoryId,group,CatalogType.LIVE,country);categories.put(categoryId,c);out.categories.add(c);
    }
    String stable=(country+"|"+tvgId+"|"+name).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+","_");
    if(stable.length()>96)stable=stable.substring(0,96);
    out.items.add(new StreamItem("FREE_"+stable+"_"+(index++),name,categoryId,logo,line,tvgId,CatalogType.LIVE));
    count++;pending=null;
   }
   return count;
  }finally{cn.disconnect();}
 }

 private static String attributeOrName(String extinf,String key){String v=attribute(extinf,key);return v==null?"":v.trim();}
 private static String attribute(String extinf,String key){
  String marker=key+"=\"";int s=extinf.indexOf(marker);if(s<0)return "";s+=marker.length();int e=extinf.indexOf('\"',s);return e<0?"":extinf.substring(s,e).trim();
 }
}
