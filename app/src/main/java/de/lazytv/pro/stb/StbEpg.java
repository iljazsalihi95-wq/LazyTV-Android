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
 private static long time(JSONObject o,String...keys){
  for(String key:keys){String v=o.optString(key,"").trim();if(v.isEmpty()||v.equals("null"))continue;
   try{long t=Long.parseLong(v);if(t>0)return t<100000000000L?t*1000:t;}catch(NumberFormatException ignored){}
   for(String pattern:new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss"}){
    SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.ROOT);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));
    ParsePosition pos=new ParsePosition(0);Date date=f.parse(v,pos);if(date!=null&&pos.getIndex()==v.length())return date.getTime();
   }
  }return 0;
 }
 /** EPG-specific unwrapping; never interpret another channel's schedule as this channel. */
 private static void collect(Object data,String channelId,List<JSONObject> out,int depth){
  if(depth>8)return;
  if(data instanceof JSONArray){JSONArray a=(JSONArray)data;for(int i=0;i<a.length();i++)collect(a.opt(i),channelId,out,depth+1);return;}
  if(!(data instanceof JSONObject))return;JSONObject o=(JSONObject)data;
  if(o.has(channelId)){collect(o.opt(channelId),channelId,out,depth+1);return;}
  for(String key:new String[]{"js","data","epg_listings","epg","programs"})if(o.opt(key) instanceof JSONArray||o.opt(key) instanceof JSONObject){collect(o.opt(key),channelId,out,depth+1);return;}
  String rowChannel=o.optString("ch_id",o.optString("channel_id",""));
  if(!rowChannel.isEmpty()&&!rowChannel.equals(channelId))return;
  if(o.has("name")||o.has("title"))out.add(o);
 }
 public static List<Program> parse(Object data,String channelId){
  List<JSONObject> rows=new ArrayList<>();collect(data,channelId,rows,0);List<Program> out=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(JSONObject o:rows){
   String title=o.optString("name","").trim();if(title.isEmpty()||title.equals("null"))title=o.optString("title","").trim();
   long start=time(o,"start_timestamp","time","start","start_time","begin"),end=time(o,"stop_timestamp","end_timestamp","time_to","end","end_time","stop");
   long duration=o.optLong("duration",0);if(end<=start&&start>0&&duration>0&&duration<=604800)end=start+duration*1000;
   if(!title.isEmpty()&&!title.equals("null")&&start>0&&end>start&&seen.add(start+":"+end+":"+title))out.add(new Program(title,start,end));
  }
  out.sort(Comparator.comparingLong(p->p.start));return out;
 }
 public static Program current(List<Program> programs,long now){for(Program p:programs)if(p.start<=now&&now<p.end)return p;return null;}
 public static Program next(List<Program> programs,long now){Program current=current(programs,now);long after=current==null?now:current.end;for(Program p:programs)if(p.start>=after&&p.start>now)return p;return null;}
}
