package de.lazytv.pro.player;
import de.lazytv.pro.catalog.*;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;import java.util.Locale;
/** Same key contract as previous player releases; signed URLs never participate. */
public final class PlaybackContentKey {
 private PlaybackContentKey(){}
 public static String of(String provider,StreamItem item){
  if(item==null)return "";StringBuilder raw=new StringBuilder("lazytv|").append(safe(provider)).append('|').append(item.type==null?"":item.type.name()).append('|').append(safe(item.id));
  if(item instanceof Episode){Episode episode=(Episode)item;raw.append('|').append(safe(episode.seriesId)).append('|').append(episode.seasonNumber).append('|').append(episode.episodeNumber);}
  try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(raw.toString().getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte value:bytes)key.append(String.format(Locale.US,"%02x",value&255));return key.toString();}
  catch(Exception unavailable){return Integer.toHexString(raw.toString().hashCode());}
 }
 private static String safe(String value){return value==null?"":value;}
}
