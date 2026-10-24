package de.lazytv.pro.recording;
import java.text.SimpleDateFormat;
import java.util.*;
/** Conservative recording support: clear progressive MPEG-TS only. */
public final class RecordingPolicy {
 private RecordingPolicy(){}
 public static boolean candidate(String url,Map<String,String> metadata){
  if(url==null||!url.matches("(?i)^https?://.*"))return false;
  String lower=url.toLowerCase(Locale.ROOT);
  if(lower.contains(".m3u8")||lower.contains(".mpd")||lower.contains(".ism"))return false;
  if(metadata!=null)for(Map.Entry<String,String> e:metadata.entrySet()){
   String key=e.getKey().toLowerCase(Locale.ROOT),value=e.getValue();
   if((key.contains("drm")||key.contains("license")||key.contains("protected"))&&value!=null&&!value.isEmpty()&&!value.equals("false")&&!value.equals("0"))return false;
  }
  return true;
 }
 public static String filename(String channel,long now){
  String safe=channel==null?"LazyTV":channel.replaceAll("[^\\p{L}\\p{N} _-]","_").trim();
  if(safe.isEmpty())safe="LazyTV";if(safe.length()>70)safe=safe.substring(0,70);
  return safe+"_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.ROOT).format(new Date(now))+".ts";
 }
 public static boolean clearPacket(byte[] packet){return packet.length==188&&(packet[0]&255)==0x47&&(packet[3]&0xc0)==0;}
}
