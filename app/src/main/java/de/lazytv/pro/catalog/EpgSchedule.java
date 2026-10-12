package de.lazytv.pro.catalog;
import org.json.*;import java.util.*;import java.text.*;import okio.ByteString;
/** Provider EPG facts. Missing dates are never invented to manufacture progress. */
public final class EpgSchedule {
 public static final class Program {
  public final String title,description;public final long start,end;
  Program(String title,String description,long start,long end){this.title=title;this.description=description;this.start=start;this.end=end;}
  public boolean timed(){return start>0&&end>start;}
  public int progress(long now){if(!timed())return 0;return (int)Math.max(0,Math.min(100,100.0*(now-start)/(end-start)));}
  public String time(){if(!timed())return "";SimpleDateFormat format=new SimpleDateFormat("HH:mm",Locale.getDefault());return format.format(new Date(start))+" – "+format.format(new Date(end));}
  public String label(){String time=time();return (time.isEmpty()?"":time+" • ")+title;}
 }
 public final Program current,next;
 private EpgSchedule(Program current,Program next){this.current=current;this.next=next;}
 public static EpgSchedule empty(){return new EpgSchedule(null,null);}
 public Program active(long now){if(current!=null&&(!current.timed()||current.start<=now&&now<current.end))return current;if(next!=null&&next.timed()&&next.start<=now&&now<next.end)return next;return null;}
 public Program upcoming(long now){return next!=null&&(!next.timed()||next.start>now)?next:null;}
 public static EpgSchedule parse(String body,String channelId,long now)throws CatalogException {
  try {
   JSONArray rows=rows(new JSONTokener(body.trim()).nextValue(),channelId,0);List<Program> programs=new ArrayList<>();boolean hasTimes=false;
   for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(row==null)continue;
    String title=decode(StalkerLiveParser.first(row,"title","name","program_name"));if(title.isEmpty())continue;
    long start=timestamp(row,"start_timestamp","start_unixtime","start"),end=timestamp(row,"stop_timestamp","end_timestamp","stop_unixtime","end_unixtime","stop","end");
    Program program=new Program(title,decode(StalkerLiveParser.first(row,"description","descr")),start,end);hasTimes|=program.timed();programs.add(program);
   }
   if(programs.isEmpty())return empty();
   if(!hasTimes)return new EpgSchedule(programs.get(0),programs.size()>1?programs.get(1):null);
   Program current=null,next=null;
   for(Program program:programs){if(!program.timed())continue;if(program.start<=now&&now<program.end&&(current==null||program.start>current.start))current=program;
    if(program.start>now&&(next==null||program.start<next.start))next=program;
   }return new EpgSchedule(current,next);
  }catch(Exception invalid){throw new CatalogException("EPG përgjigje e pavlefshme");}
 }
 private static JSONArray rows(Object node,String channelId,int depth) {
  if(node instanceof JSONArray)return (JSONArray)node;
  if(!(node instanceof JSONObject)||depth>6)return new JSONArray();JSONObject object=(JSONObject)node;
  if(channelId!=null&&object.has(channelId))return rows(object.opt(channelId),channelId,depth+1);
  for(String key:new String[]{"js","epg_listings","data","epg","items","results","programs","listings"}){Object child=object.opt(key);if(child instanceof JSONObject||child instanceof JSONArray)return rows(child,channelId,depth+1);}
  return new JSONArray();
 }
 private static long timestamp(JSONObject object,String...keys){
  for(String key:keys){String value=object.optString(key,"").trim();if(value.isEmpty())continue;
   try{long number=Long.parseLong(value);if(number>0)return number<1_000_000_000_000L?number*1000:number;}catch(Exception ignored){}
   if(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}.*"))try{SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);format.setLenient(false);Date date=format.parse(value);if(date!=null)return date.getTime();}catch(Exception ignored){}
  }return 0;
 }
 private static String decode(String raw){if(raw==null)return "";String value=raw.trim();if(value.isEmpty())return value;
  try{ByteString decoded=ByteString.decodeBase64(value);if(decoded!=null){String text=decoded.utf8();boolean printable=!text.isEmpty();for(int n=0;n<text.length();n++){char c=text.charAt(n);if(c=='\uFFFD'||Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'){printable=false;break;}}if(printable)return text;}}catch(Exception ignored){}return value;
 }
}
