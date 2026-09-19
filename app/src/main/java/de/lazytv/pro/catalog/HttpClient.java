package de.lazytv.pro.catalog;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.GZIPInputStream;import javax.net.ssl.SSLException;import android.util.Log;
public final class HttpClient {
 private HttpClient(){}
 public static String get(String u,Map<String,String> headers)throws CatalogException{
  URL url;try{url=new URL(u);}catch(Exception e){throw new CatalogException("URL nuk është valide",e);}
  for(int redirect=0;redirect<=8;redirect++){
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)url.openConnection();
    c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);
    c.setRequestMethod("GET");c.setUseCaches(false);c.setDoInput(true);
    c.setRequestProperty("User-Agent","VLC/3.0.20 LibVLC/3.0.20");
    c.setRequestProperty("Accept","*/*");
    c.setRequestProperty("Accept-Encoding","identity");
    c.setRequestProperty("Connection","close");
    if(headers!=null)for(Map.Entry<String,String>e:headers.entrySet())if(e.getKey()!=null&&e.getValue()!=null)c.setRequestProperty(e.getKey(),e.getValue());
    int code=c.getResponseCode();String ct=c.getContentType();String enc=c.getContentEncoding();Log.d("LazyTV-HTTP","GET host="+url.getHost()+" status="+code+" redirect="+redirect+" contentType="+ct+" encoding="+enc);
    if(code==301||code==302||code==303||code==307||code==308){
     String loc=c.getHeaderField("Location");if(loc==null||loc.trim().isEmpty())throw new CatalogException("Redirect pa Location (HTTP "+code+")");
     url=new URL(url,loc);continue;
    }
    InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
    if(in!=null&&"gzip".equalsIgnoreCase(c.getContentEncoding()))in=new GZIPInputStream(in);
    String body=read(in);
    if(code==401||code==403)throw new CatalogException("Credentials/refuzim nga serveri (HTTP "+code+")");
    if(code<200||code>=300)throw new CatalogException("Serveri ktheu HTTP "+code);
    return body;
   }catch(SocketTimeoutException e){throw new CatalogException("Serveri nuk u përgjigj brenda afatit",e);}
   catch(CatalogException e){throw e;}
   catch(SSLException e){throw new CatalogException("Lidhja TLS/SSL dështoi: "+e.getClass().getSimpleName(),e);}
   catch(Exception e){throw new CatalogException("Serveri nuk mund të arrihet: "+e.getClass().getSimpleName(),e);}
   finally{if(c!=null)c.disconnect();}
  }
  throw new CatalogException("Shumë redirects nga serveri");
 }
 private static String read(InputStream in)throws IOException{
  if(in==null)return"";
  try(InputStream x=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[32768];int n,total=0;while((n=x.read(b))!=-1){out.write(b,0,n);total+=n;if(total>80_000_000)throw new IOException("Përgjigjja është shumë e madhe");}
   byte[] data=out.toByteArray();int off=data.length>=3&&(data[0]&255)==0xEF&&(data[1]&255)==0xBB&&(data[2]&255)==0xBF?3:0;
   return new String(data,off,data.length-off,StandardCharsets.UTF_8);
  }
 }
 public static String enc(String s){try{return URLEncoder.encode(s==null?"":s,"UTF-8");}catch(Exception e){return"";}}
}
