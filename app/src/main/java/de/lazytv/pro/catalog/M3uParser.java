package de.lazytv.pro.catalog;
import java.io.*;import java.net.*;import java.util.*;import java.util.regex.*;import org.json.*;
public final class M3uParser{
 private static final Pattern A=Pattern.compile("([\\w-]+)=\\\"([^\\\"]*)\\\"");private M3uParser(){}
 public static Catalog parse(BufferedReader r)throws Exception{Catalog c=new Catalog();Map<String,String> cats=new LinkedHashMap<>();String line,info=null;Map<String,String> pending=new LinkedHashMap<>();
  while((line=r.readLine())!=null){line=line.trim();
   if(line.startsWith("#EXTINF:")){info=line;pending.clear();}
   else if(info!=null&&line.startsWith("#EXTVLCOPT:")){option(pending,line.substring(11));}
   else if(info!=null&&line.startsWith("#KODIPROP:")){option(pending,line.substring(10));}
   else if(info!=null&&line.startsWith("#EXTHTTP:")){extHttp(pending,line.substring(9));}
   else if(info!=null&&!line.isEmpty()&&!line.startsWith("#")){Map<String,String>m=new HashMap<>();Matcher x=A.matcher(info);while(x.find())m.put(x.group(1).toLowerCase(Locale.US),x.group(2));int comma=info.lastIndexOf(',');String name=m.get("tvg-name");if(name==null||name.isEmpty())name=comma>=0?info.substring(comma+1).trim():"Stream";String group=m.get("group-title");if(group==null||group.isEmpty())group=inferGroup(name,m);CatalogType type=type(group,line,m);String country=country(m,group);String key=type.name()+"|"+country+"|"+group;if(!cats.containsKey(key)){String id="m3u-"+Integer.toHexString(key.hashCode());cats.put(key,id);c.categories.add(new Category(id,group,type,"OTHER".equals(country)?"":country));}
    String url=line;int pipe=url.indexOf('|');if(pipe>0){pipeHeaders(pending,url.substring(pipe+1));url=url.substring(0,pipe);}String id="m3u-"+Integer.toHexString((name+url).hashCode());c.items.add(new M3uStreamItem(id,name,cats.get(key),m.get("tvg-logo"),url,m.get("tvg-id"),type,pending));info=null;pending=new LinkedHashMap<>();
   }
  }if(c.items.isEmpty())throw new CatalogException("Playlist M3U është bosh ose e pavlefshme");return c;
 }
 private static String inferGroup(String name,Map<String,String>m){String n=name==null?"":name.trim();String tvg=m.get("tvg-id");if(tvg!=null&&!tvg.trim().isEmpty()){String id=tvg.trim();int dot=id.indexOf('.');if(dot>1)n=id.substring(0,dot)+" | "+n;}Matcher a=Pattern.compile("^\\s*\\[([^\\]]{2,32})\\]\\s*").matcher(n);if(a.find())return a.group(1).trim();Matcher b=Pattern.compile("^\\s*([^|:•»>\\-]{2,32})\\s*[|:•»>]\\s*").matcher(n);if(b.find())return b.group(1).trim();Matcher d=Pattern.compile("^\\s*([A-Za-z]{2,3})\\s*[-–—]\\s*").matcher(n);if(d.find())return d.group(1).trim();return "Të tjera";}
 private static void option(Map<String,String>h,String s){int q=s.indexOf('=');if(q<1)return;String k=s.substring(0,q).trim().toLowerCase(Locale.US),v=s.substring(q+1).trim();if(k.equals("http-user-agent")||k.equals("user-agent"))h.put("User-Agent",v);else if(k.equals("http-referrer")||k.equals("http-referer")||k.equals("referer"))h.put("Referer",v);else if(k.equals("http-origin")||k.equals("origin"))h.put("Origin",v);else if(k.equals("http-cookie")||k.equals("cookie"))h.put("Cookie",v);}
 private static void extHttp(Map<String,String>h,String s){try{JSONObject j=new JSONObject(s.trim());Iterator<String>it=j.keys();while(it.hasNext()){String k=it.next();String v=j.optString(k,"");if(!v.isEmpty())h.put(header(k),v);}}catch(Exception ignored){}}
 private static void pipeHeaders(Map<String,String>h,String s){for(String p:s.split("&")){int q=p.indexOf('=');if(q<1)continue;try{h.put(header(URLDecoder.decode(p.substring(0,q),"UTF-8")),URLDecoder.decode(p.substring(q+1),"UTF-8"));}catch(Exception ignored){}}}
 private static String header(String k){String x=k.trim();if(x.equalsIgnoreCase("user-agent"))return"User-Agent";if(x.equalsIgnoreCase("referer")||x.equalsIgnoreCase("referrer"))return"Referer";if(x.equalsIgnoreCase("cookie"))return"Cookie";if(x.equalsIgnoreCase("origin"))return"Origin";return x;}
 private static String country(Map<String,String>m,String group){String x=m.get("tvg-country");if(x==null||x.trim().isEmpty())x=m.get("country");if(x==null||x.trim().isEmpty())x=m.get("country-code");String k=CountryResolver.key(x);return "OTHER".equals(k)?CountryResolver.key(group):k;}
 private static CatalogType type(String g,String u,Map<String,String>m){
  String s=(g==null?"":g).toLowerCase(Locale.US),url=(u==null?"":u).toLowerCase(Locale.US);
  String meta=(m.get("type")+" "+m.get("content-type")+" "+m.get("stream-type")+" "+m.get("media-type")).toLowerCase(Locale.US);
  if(meta.contains("series")||meta.contains("episode")||url.contains("/series/"))return CatalogType.SERIES;
  if(meta.contains("movie")||meta.contains("vod")||url.contains("/movie/"))return CatalogType.MOVIES;
  return CatalogType.LIVE;
 }
}