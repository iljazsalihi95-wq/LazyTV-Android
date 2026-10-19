package de.lazytv.pro.ai;

import de.lazytv.pro.catalog.ResolvedStream;
import org.json.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import android.media.MediaExtractor;
import android.media.MediaFormat;

/** Reads existing subtitles. Never transcribes audio or invents subtitle URLs. */
public final class SubtitleSources {
 private SubtitleSources(){}
 public static String external(Map<String,String> meta){
  java.util.List<ExternalSubtitleTracks.Track> tracks=ExternalSubtitleTracks.read(meta);
  return tracks.isEmpty()?"":tracks.get(0).url;
 }
 private static boolean http(String s){return s!=null&&(s.startsWith("https://")||s.startsWith("http://"));}
 public static JSONObject load(ResolvedStream stream)throws Exception{
  return load(stream,external(stream.metadata));
 }
 public static JSONObject load(ResolvedStream stream,String url)throws Exception{
  if(url!=null&&!url.isEmpty())return TimedSubtitleSource.parse(download(url,stream),"auto");
  return embedded(stream);
 }
 private static String download(String source,ResolvedStream stream)throws Exception{
  URL current=new URL(source);URL provider=new URL(stream.url);
  for(int hop=0;hop<4;hop++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();
   if(!http(current.toString()))throw new IOException("SUBTITLE_PROTOCOL");
   HttpURLConnection c=(HttpURLConnection)current.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
   if(current.getProtocol().equals(provider.getProtocol())&&current.getAuthority().equalsIgnoreCase(provider.getAuthority()))for(Map.Entry<String,String> h:stream.headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
   try{int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("SUBTITLE_REDIRECT");current=new URL(current,location);continue;}if(code<200||code>=300)throw new IOException("SUBTITLE_HTTP_"+code);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(out.size()+n>4000000)throw new IOException("SUBTITLE_SIZE_LIMIT");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8).replace("\uFEFF","");}
   }finally{c.disconnect();}
  }throw new IOException("SUBTITLE_REDIRECT_LIMIT");
 }
 private static JSONObject embedded(ResolvedStream stream)throws Exception{
  MediaExtractor extractor=new MediaExtractor();
  try{
   extractor.setDataSource(stream.url,stream.headers);int selected=-1;String mime="";long duration=0;
   for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);String m=f.getString(MediaFormat.KEY_MIME);if("text/3gpp".equals(m)||"application/x-quicktime-tx3g".equals(m)||"application/x-subrip".equals(m)){selected=i;mime=m;if(f.containsKey(MediaFormat.KEY_DURATION))duration=f.getLong(MediaFormat.KEY_DURATION)/1000;break;}}
   if(selected<0)throw new IOException("NO_EXTRACTABLE_SUBTITLES");extractor.selectTrack(selected);
   ByteBuffer buffer=ByteBuffer.allocate(65536);JSONArray cues=new JSONArray();String previous="";long start=0;StringBuilder srt=new StringBuilder();
   while(true){if(Thread.currentThread().isInterrupted())throw new InterruptedException();buffer.clear();int size=extractor.readSampleData(buffer,0);if(size<0)break;if(size>65536)throw new IOException("SUBTITLE_SAMPLE_LIMIT");long time=extractor.getSampleTime()/1000;
    if(!previous.trim().isEmpty()&&time>start)cues.put(new JSONObject().put("start_ms",start).put("end_ms",time).put("text",previous));
    byte[] bytes=new byte[size];buffer.position(0);buffer.get(bytes);int offset=0,length=size;
    if(!mime.equals("application/x-subrip")){if(size<2){previous="";extractor.advance();continue;}length=((bytes[0]&255)<<8)|(bytes[1]&255);offset=2;if(length>size-2)throw new IOException("SUBTITLE_SAMPLE_INVALID");}
    previous=new String(bytes,offset,length,StandardCharsets.UTF_8).replaceAll("<[^>]+>","").trim();start=time;
    if(mime.equals("application/x-subrip")&&previous.contains("-->"))srt.append(previous).append("\n\n");
    if(cues.length()>5000)throw new IOException("CUE_LIMIT");extractor.advance();
   }
   if(srt.length()>0)return TimedSubtitleSource.parse(srt.toString(),"auto");
   if(!previous.isEmpty()&&duration>start)cues.put(new JSONObject().put("start_ms",start).put("end_ms",duration).put("text",previous));
   if(cues.length()==0)throw new IOException("NO_EXTRACTABLE_SUBTITLES");return new JSONObject().put("cues",cues).put("format","container").put("source_language","auto");
  }finally{extractor.release();}
 }
}
