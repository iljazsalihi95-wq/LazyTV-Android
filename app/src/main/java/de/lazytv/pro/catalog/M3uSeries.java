package de.lazytv.pro.catalog;
import java.util.*;
/** Original playlist episodes travel with the parent, including local SAF sources. */
public final class M3uSeries extends Series {
 private final List<Episode> episodes=new ArrayList<>();
 public M3uSeries(String id,String name,String category,String logo,Map<String,String> metadata){super(id,name,category,logo,"","",metadata);}
 void add(Episode episode){episodes.add(episode);}
 public SeriesDetails details(){SeriesDetails details=new SeriesDetails();details.metadata.putAll(metadata);Map<Integer,List<Episode>> seasons=new TreeMap<>();for(Episode episode:episodes){List<Episode> list=seasons.get(episode.seasonNumber);if(list==null){list=new ArrayList<>();seasons.put(episode.seasonNumber,list);}list.add(episode);}for(Map.Entry<Integer,List<Episode>> season:seasons.entrySet()){details.seasons.add(new Season(id+"-"+season.getKey(),season.getKey()>0?"Sezoni "+season.getKey():"Episode",id,season.getKey(),logo));Collections.sort(season.getValue(),(a,b)->Integer.compare(a.episodeNumber,b.episodeNumber));details.episodes.put(season.getKey(),new ArrayList<>(season.getValue()));}return details;}
}
