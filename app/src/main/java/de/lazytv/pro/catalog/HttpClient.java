package de.lazytv.pro.catalog;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.GZIPInputStream;import javax.net.ssl.SSLException;import android.util.Log;
public final class HttpClient {
 private HttpClient(){}
 public static String get(String u,Map<String,String> headers)throws CatalogException{
  return get(u,headers,null);
 }
 public static String get(String u,Map<String,String> headers,CookieManager cookies)throws CatalogException{
  URL url;try{String raw=u==null?"":u.trim().replace("&amp;","&").replace(" ","%20");url=new URL(raw);}catch(Exception e){throw new CatalogException("URL nuk është valide",e);}
  URL initial=url;int dnsRetries=0,conflictRetries=0,forbiddenRetries=0;
  for(int redirect=0;redirect<=8;redirect++){
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)url.openConnection();
    c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);
    c.setRequestMethod("GET");c.setUseCaches(false);c.setDoInput(true);
    boolean stalker=headers!=null&&headers.containsKey("X-User-Agent");
    c.setRequestProperty("User-Agent",stalker?"Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG254 stbapp":"Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
    if(forbiddenRetries>0&&!stalker)c.setRequestProperty("User-Agent","IPTVSmartersPro");
    c.setRequestProperty("Accept","*/*");
    c.setRequestProperty("Accept-Encoding","identity");
    c.setRequestProperty("Connection","close");
    if(stalker)c.setRequestProperty("Accept-Language","en-US,en;q=0.8");
    boolean sameOrigin=url.getProtocol().equalsIgnoreCase(initial.getProtocol())&&url.getAuthority().equalsIgnoreCase(initial.getAuthority());
    if(headers!=null)for(Map.Entry<String,String>e:headers.entrySet())if(e.getKey()!=null&&e.getValue()!=null){
     boolean sensitive=e.getKey().equalsIgnoreCase("Authorization")||e.getKey().equalsIgnoreCase("Cookie");
     if(!sensitive||sameOrigin)c.setRequestProperty(e.getKey(),e.getValue());
    }
    if(cookies!=null){String value=cookieHeader(cookies,url,sameOrigin&&headers!=null?headers.get("Cookie"):null);if(!value.isEmpty())c.setRequestProperty("Cookie",value);}
    int code=c.getResponseCode();String ct=c.getContentType();String enc=c.getContentEncoding();Log.d("LazyTV-HTTP","GET host="+url.getHost()+" status="+code+" redirect="+redirect+" contentType="+ct+" encoding="+enc);
    dnsRetries=0;
    if(cookies!=null)cookies.put(url.toURI(),c.getHeaderFields());
    if(code==301||code==302||code==303||code==307||code==308){
     String loc=c.getHeaderField("Location");if(loc==null||loc.trim().isEmpty())throw new CatalogException("Redirect pa Location (HTTP "+code+")");
     URL next=new URL(url,loc);
     // Some Stalker/MAG portals redirect portal.php -> stalker_portal/c/ or server/load.php
     // without repeating the API query. Preserve it only for Stalker-style requests so the
     // handshake/action is not accidentally turned into an HTML landing-page request.
     if(stalker&&url.getQuery()!=null&&!url.getQuery().isEmpty()&&(next.getQuery()==null||next.getQuery().isEmpty())){
      try{next=new URL(next.getProtocol(),next.getHost(),next.getPort(),next.getPath()+"?"+url.getQuery());}catch(Exception ignored){}
     }
     url=next;continue;
    }
    InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
    if(in!=null&&"gzip".equalsIgnoreCase(c.getContentEncoding()))in=new GZIPInputStream(in);
    String body=read(in);
    if(code==403&&!stalker&&forbiddenRetries++==0&&(headers==null||!headers.containsKey("User-Agent"))){redirect--;continue;}
    if(code==409&&conflictRetries++==0){
     try{Thread.sleep(350);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new CatalogException("Lidhja u ndërpre",e);}
     redirect--;continue;
    }
    if(code<200||code>=300)throw new ProviderHttpException(code);
    return body;
   }catch(UnknownHostException e){
    if(dnsRetries<2){dnsRetries++;Log.w("LazyTV-HTTP","DNS retry="+dnsRetries+" host="+url.getHost());try{Thread.sleep(350L*dnsRetries);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CatalogException("Lidhja u ndërpre",interrupted);}redirect--;continue;}
    throw new CatalogException("Hosti i portalit nuk gjendet në DNS. Kontrollo URL-në ose rrjetin.",e);
   }
   catch(SocketTimeoutException e){throw new CatalogException("Serveri nuk u përgjigj brenda afatit",e);}
   catch(CatalogException e){throw e;}
   catch(SSLException e){String m=e.getClass().getSimpleName();Log.e("LazyTV-HTTP","TLS FAIL host="+url.getHost());if("https".equalsIgnoreCase(url.getProtocol())&&url.getPort()>0&&url.getPort()!=443){try{URL retry=new URL("http",url.getHost(),url.getPort(),url.getFile());Log.w("LazyTV-HTTP","Retrying custom-port provider over HTTP host="+url.getHost()+" port="+url.getPort());url=retry;continue;}catch(Exception ignored){}}throw new CatalogException("Lidhja TLS/SSL dështoi: "+(m==null?e.getClass().getSimpleName():m),e);}
   catch(Exception e){throw new CatalogException("Serveri nuk mund të arrihet: "+e.getClass().getSimpleName(),e);}
   finally{if(c!=null)c.disconnect();}
  }
  throw new CatalogException("Shumë redirects nga serveri");
 }
 static String cookieHeader(CookieManager manager,URL url,String seed)throws Exception{
  Map<String,String> values=new LinkedHashMap<>();
  if(seed!=null)for(String part:seed.split(";")){int eq=part.indexOf('=');if(eq>0)values.put(part.substring(0,eq).trim(),part.substring(eq+1).trim());}
  for(Map.Entry<String,List<String>> e:manager.get(url.toURI(),Collections.emptyMap()).entrySet())
   if("Cookie".equalsIgnoreCase(e.getKey()))for(String line:e.getValue())for(String part:line.split(";")){
    int eq=part.indexOf('=');if(eq>0&&!part.trim().startsWith("$"))values.put(part.substring(0,eq).trim(),part.substring(eq+1).trim());
   }
  StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:values.entrySet()){if(b.length()>0)b.append("; ");b.append(e.getKey()).append('=').append(e.getValue());}return b.toString();
 }
 private static String read(InputStream in)throws IOException{
  if(in==null)return"";
  try(InputStream x=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[32768];int n,total=0;while((n=x.read(b))!=-1){out.write(b,0,n);total+=n;if(total>80_000_000)throw new IOException("Përgjigjja është shumë e madhe");}
   byte[] data=out.toByteArray();int off=data.length>=3&&(data[0]&255)==0xEF&&(data[1]&255)==0xBB&&(data[2]&255)==0xBF?3:0;
   return new String(data,off,data.length-off,StandardCharsets.UTF_8);
  }
 }
 private static String safeStatus(String s){if(s==null)return"null";return s.length()>120?s.substring(0,120):s;}
 public static String enc(String s){try{return URLEncoder.encode(s==null?"":s,"UTF-8");}catch(Exception e){return"";}}
}
