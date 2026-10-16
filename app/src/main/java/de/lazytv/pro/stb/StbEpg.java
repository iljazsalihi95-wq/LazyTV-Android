package de.lazytv.pro.stb;
import org.json.*;
import java.text.*;
import java.util.*;
public final class StbEpg {
 public static final class Program {
  public final String title;public final long start,end;
  Program(String title,long start,long end){this.title=title;this.start=start;this.end=end;}
  public int progress(long now){return end<=start?0:(int)Math.max(0,Math.min(100,100L*(now-start)/(end-start)));}
  public String time(){DateFormat f=new SimpleDateFormat("HH:mm",Locale.getDefault());return f.format(new Date(start))+" – "+f.format(new Date(end));}
 }
 private StbEpg(){}
 private static long time(JSONObject o,String...keys){for(String key:keys){String v=o.optString(key,"");if(v.isEmpty())continue;try{long t=Long.parseLong(v);return t<100000000000L?t*1000:t;}catch(NumberFormatException ignored){}for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ssXXX"})try{return new SimpleDateFormat(pattern,Locale.ROOT).parse(v).getTime();}catch(ParseException ignored){}}return 0;}
 public static List<Program> parse(Object data,String channelId){
  if(data instanceof JSONObject){JSONObject o=(JSONObject)data;if(o.has(channelId))data=o.opt(channelId);else if(o.has("epg_listings"))data=o.opt("epg_listings");}
  JSONArray rows=StbProtocol.rows(data);List<Program> out=new ArrayList<>();
  for(int i=0;i<rows.length();i++){JSONObject o=rows.optJSONObject(i);if(o==null)continue;String title=o.optString("name",o.optString("title"));long start=time(o,"start_timestamp","time","start","start_time"),end=time(o,"stop_timestamp","time_to","end","end_time","stop");if(!title.isEmpty()&&end>start)out.add(new Program(title,start,end));}
  out.sort(Comparator.comparingLong(p->p.start));return out;
 }
 public static Program current(List<Program> programs,long now){for(Program p:programs)if(p.start<=now&&now<p.end)return p;return null;}
 public static Program next(List<Program> programs,long now){for(Program p:programs)if(p.start>now)return p;return null;}
}
