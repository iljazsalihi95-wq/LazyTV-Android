package de.lazytv.pro.stb;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.URI;
import java.util.concurrent.*;
import okhttp3.*;
/** Bounded private image cache; provider tokens/MAC are never sent to third party logo hosts. */
final class StbLogos {
 private final LruCache<String,Bitmap> cache=new LruCache<>(48);
 private final ExecutorService worker=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),new ThreadPoolExecutor.DiscardOldestPolicy());
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build();
 private final String base;private volatile boolean closed;
 StbLogos(String portal){base=StbProtocol.endpoints(portal).get(0);}
 void load(String logo,ImageView target){
  target.setImageDrawable(null);target.setTag(null);if(closed||logo==null||logo.trim().isEmpty())return;String url;
  try{URI uri=URI.create(base).resolve(logo);if(!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme()))return;url=uri.toASCIIString();}catch(Exception e){return;}
  if(logo==null||logo.trim().isEmpty())return;target.setTag(url);Bitmap hit=cache.get(url);if(hit!=null){target.setImageBitmap(hit);return;}
  worker.execute(()->{try(Response r=http.newCall(new Request.Builder().url(url).build()).execute()){
   if(!r.isSuccessful()||r.body()==null)return;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];try(InputStream in=r.body().byteStream()){int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)return;out.write(b,0,n);}}
   byte[] bytes=out.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)return;BitmapFactory.Options options=new BitmapFactory.Options();int sample=1;while(bounds.outWidth/sample>256||bounds.outHeight/sample>256)sample*=2;options.inSampleSize=sample;Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(image==null||closed)return;cache.put(url,image);ui.post(()->{if(!closed&&url.equals(target.getTag()))target.setImageBitmap(image);});
  }catch(Exception ignored){}});
 }
 void close(){closed=true;http.dispatcher().cancelAll();worker.shutdownNow();ui.removeCallbacksAndMessages(null);cache.evictAll();}
}
