package de.lazytv.pro.catalog;
import java.util.*;
public final class M3uEpisode extends Episode implements M3uHeaders {
 private final Map<String,String> headers;
 public M3uEpisode(String id,String title,String seriesId,String category,String image,String url,String extension,String duration,int season,int episode,Map<String,String> metadata,Map<String,String> headers){super(id,title,seriesId,category,image,url,extension,duration,season,episode,metadata);this.headers=Collections.unmodifiableMap(new LinkedHashMap<>(headers));}
 public Map<String,String> requestHeaders(){return headers;}
}
