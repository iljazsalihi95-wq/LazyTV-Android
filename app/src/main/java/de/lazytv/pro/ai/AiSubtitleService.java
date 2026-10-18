package de.lazytv.pro.ai;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Existing-cue translation only: bounded batches, exact timing/order validation, resumable cache. */
public final class AiSubtitleService {
 public static final String ALBANIAN="sq";
 public interface Progress{void update(int done,int total);}
 private final AiSubtitleCache cache;private final AiBackendClient backend;
 public AiSubtitleService(Context c){cache=new AiSubtitleCache(c);backend=new AiBackendClient(c);}
 public AiSubtitleDocument cachedAlbanian(String key){String s=cache.get(key,"sq");if(s==null)return null;try{AiSubtitleDocument d=AiSubtitleDocument.parse(s);if(!"sq".equals(d.language))throw new Exception();return d;}catch(Exception e){cache.remove(key,"sq");return null;}}
 public AiSubtitleDocument translateAlbanian(String key,JSONObject request)throws Exception{return translateAlbanian(key,request,(d,t)->{});}
 public AiSubtitleDocument translateAlbanian(String key,JSONObject request,Progress progress)throws Exception{
  AiSubtitleDocument hit=cachedAlbanian(key);if(hit!=null)return hit;
  JSONArray source=request.getJSONArray("cues"),all=new JSONArray();if(source.length()==0||source.length()>5000)throw new IllegalArgumentException("CUE_LIMIT");
  for(int offset=0;offset<source.length();){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();
   JSONArray batch=new JSONArray();int chars=0;
   while(offset+batch.length()<source.length()&&batch.length()<40){JSONObject c=source.getJSONObject(offset+batch.length());String text=c.getString("text");if(text.length()>4000)throw new IllegalArgumentException("CUE_TEXT_LIMIT");if(batch.length()>0&&chars+text.length()>12000)break;chars+=text.length();batch.put(new JSONObject().put("start",c.getLong("start_ms")).put("end",c.getLong("end_ms")).put("text",text));}
   String batchKey=key+"|v2|"+hash(batch.toString());String cached=cache.get(batchKey,"sq");JSONObject response;
   if(cached!=null)response=new JSONObject(cached);else{
    JSONObject body=new JSONObject().put("mediaId",hash(key)).put("mediaType",request.optString("mediaType","movie")).put("sourceLanguage",request.optString("source_language","auto")).put("targetLanguage","sq").put("format","cues").put("cues",batch);
    response=backend.translateSubtitles(body);
   }
   JSONArray result=response.getJSONArray("cues");if(result.length()!=batch.length())throw new IllegalArgumentException("CUE_COUNT_MISMATCH");
   for(int n=0;n<result.length();n++){JSONObject got=result.getJSONObject(n),expected=batch.getJSONObject(n);if(got.getDouble("start")!=expected.getLong("start")||got.getDouble("end")!=expected.getLong("end")||got.optString("text").trim().isEmpty())throw new IllegalArgumentException("CUE_TIMING_MISMATCH");all.put(new JSONObject().put("start_ms",expected.getLong("start")).put("end_ms",expected.getLong("end")).put("text",got.getString("text")));}
   cache.put(batchKey,"sq",response.toString());offset+=batch.length();progress.update(offset,source.length());
  }
  String json=new JSONObject().put("language","sq").put("version",2).put("timeUnit","ms").put("cues",all).toString();AiSubtitleDocument doc=AiSubtitleDocument.parse(json);cache.put(key,"sq",json);return doc;
 }
 public void invalidateAlbanian(String key){cache.remove(key,"sq");}
 public static String hash(String s){try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
