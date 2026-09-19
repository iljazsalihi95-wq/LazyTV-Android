package de.lazytv.pro.catalog;
import java.util.*; public class SeriesDetails { public final List<Season> seasons=new ArrayList<>(); public final Map<Integer,List<Episode>> episodes=new LinkedHashMap<>(); public boolean isEmpty(){return seasons.isEmpty()&&episodes.isEmpty();} }
