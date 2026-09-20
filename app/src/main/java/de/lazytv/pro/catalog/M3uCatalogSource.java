package de.lazytv.pro.catalog;
import android.content.Context;import android.net.Uri;import android.util.Log;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.TimeUnit;import de.lazytv.pro.playlist.*;import okhttp3.*;
public class M3uCatalogSource {
 private static final OkHttpClient REMOTE=new OkHttpClient.Builder().connectTimeout(25,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(25,TimeUnit.SECONDS).followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(true).build();
 public Catalog load(Context ctx,Playlist p)throws CatalogException{
  try{
   if(p.getType()==PlaylistType.M3U_URL)return loadRemote(p.getUrl());
   InputStream in=ctx.getContentResolver().openInputStream(Uri.parse(p.getUrl()));
   if(in==null)throw new CatalogException("Skedari M3U nuk mund të hapet");
   return parseM3u(in);
  }catch(CatalogException e){throw e;}catch(Exception e){throw new CatalogException("M3U nuk mund të lexohet: "+safe(e),e);}
 }
 private Catalog loadRemote(String exactUrl)throws CatalogException{
  Request req;
  try{req=new Request.Builder().url(exactUrl).header("User-Agent","VLC/3.0.20 LibVLC/3.0.20").header("Accept","*/*").get().build();}
  catch(Exception e){throw new CatalogException("URL M3U nuk është valide",e);}
  try(Response res=REMOTE.newCall(req).execute()){
   int code=res.code();ResponseBody rb=res.body();String finalUrl=res.request().url().toString();String ct=res.header("Content-Type","");int redirects=redirectCount(res);
   Log.d("LazyTV-M3U","REMOTE_HTTP host="+res.request().url().host()+" status="+code+" redirects="+redirects+" contentType="+ct);
   if(!res.isSuccessful())throw new CatalogException("Remote M3U HTTP "+code);
   if(rb==null)throw new CatalogException("Remote M3U response është bosh");
   byte[] data=rb.bytes();if(data.length==0)throw new CatalogException("Remote M3U response është bosh");
   int off=data.length>=3&&(data[0]&255)==0xEF&&(data[1]&255)==0xBB&&(data[2]&255)==0xBF?3:0;
   String probe=new String(data,off,Math.min(data.length-off,4096),StandardCharsets.UTF_8).trim();
   boolean ext=probe.startsWith("#EXTM3U");
   Log.d("LazyTV-M3U","REMOTE_BODY host="+res.request().url().host()+" bytes="+data.length+" extm3u="+ext);
   if(!ext)throw new CatalogException("Remote URL nuk ktheu M3U (#EXTM3U mungon)");
   try{return parseM3u(new ByteArrayInputStream(data,off,data.length-off));}catch(CatalogException e){throw e;}catch(Exception e){throw new CatalogException("Remote M3U parse: "+safe(e),e);}
  }catch(javax.net.ssl.SSLException e){Log.e("LazyTV-M3U","REMOTE_TLS host="+host(exactUrl)+" reason="+safe(e));throw new CatalogException("TLS/SSL: "+safe(e),e);}
   catch(IOException e){Log.e("LazyTV-M3U","REMOTE_IO host="+host(exactUrl)+" reason="+safe(e));throw new CatalogException("Remote M3U network: "+safe(e),e);}
 }
 private Catalog parseM3u(InputStream in)throws Exception{try(InputStream x=in;BufferedReader r=new BufferedReader(new InputStreamReader(x,StandardCharsets.UTF_8))){return M3uParser.parse(r);}}
 private int redirectCount(Response r){int n=0;for(Response x=r.priorResponse();x!=null;x=x.priorResponse())n++;return n;}
 private String host(String u){try{return new java.net.URL(u).getHost();}catch(Exception e){return"unknown";}}
 private String safe(Exception e){String s=e.getMessage();if(s==null||s.trim().isEmpty())s=e.getClass().getSimpleName();return s.replaceAll("(?i)(username|password|token|mac)=([^&\\s]+)","$1=<redacted>");}
}