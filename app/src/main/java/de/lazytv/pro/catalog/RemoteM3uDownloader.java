package de.lazytv.pro.catalog;
import android.util.Log;import java.net.*;import java.util.*;import java.util.concurrent.TimeUnit;import okhttp3.*;
public final class RemoteM3uDownloader{
 public static final class Result{public final String body;public final int status;public final String host;public final int redirects;Result(String b,int s,String h,int r){body=b;status=s;host=h;redirects=r;}}
 private RemoteM3uDownloader(){}
 public static Result download(String raw)throws CatalogException{
  OkHttpClient client=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).followRedirects(true).followSslRedirects(true).build();
  try{
   Request req=new Request.Builder().url(raw).header("User-Agent","okhttp/4.12.0").header("Accept","*/*").header("Accept-Encoding","gzip").build();
   try(Response r=client.newCall(req).execute()){
    int redirects=0;Response prior=r.priorResponse();while(prior!=null){redirects++;prior=prior.priorResponse();}
    String host=r.request().url().host(),ct=r.header("Content-Type",""),body=r.body()==null?"":r.body().string();
    Log.d("LazyTV-M3U","REMOTE status="+r.code()+" host="+host+" redirects="+redirects+" contentType="+ct+" bytes="+body.length());
    if(r.code()<200||r.code()>=300)throw new RemoteHttpException(r.code(),host,"Remote M3U HTTP "+r.code());
    return new Result(body,r.code(),host,redirects);
   }
  }catch(RemoteHttpException e){throw e;}catch(javax.net.ssl.SSLException e){Log.e("LazyTV-M3U","REMOTE TLS host="+safeHost(raw)+" error="+e.getMessage());throw new CatalogException("TLS/SSL: "+safe(e.getMessage()),e);}catch(Exception e){Log.e("LazyTV-M3U","REMOTE FAIL host="+safeHost(raw)+" error="+e.getClass().getSimpleName()+": "+e.getMessage());throw new CatalogException("Remote M3U: "+e.getClass().getSimpleName()+" - "+safe(e.getMessage()),e);}
 }
 static final class RemoteHttpException extends CatalogException{final int status;final String host;RemoteHttpException(int s,String h,String m){super(m);status=s;host=h;}}
 private static String safeHost(String s){try{return new URL(s).getHost();}catch(Exception e){return"unknown";}}
 private static String safe(String s){if(s==null)return"unknown";return s.replaceAll("(?i)(username|password|token)=[^&\\s]+","$1=<redacted>");}
}