package de.lazytv.pro.catalog;
import android.content.Context;import android.net.Uri;import android.util.Log;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.TimeUnit;import de.lazytv.pro.playlist.*;import okhttp3.*;
public class M3uCatalogSource {
 private static final OkHttpClient REMOTE=new OkHttpClient.Builder().connectTimeout(25,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(true).build();
 public Catalog load(Context ctx,Playlist p)throws CatalogException{
  try{
   if(p.getType()==PlaylistType.M3U_URL)return loadRemote(p);
   Uri fileUri=Uri.parse(p.getUrl());InputStream in=ctx.getContentResolver().openInputStream(fileUri);
   if(in==null)throw new CatalogException("Skedari M3U nuk mund të hapet");
   return parseM3u(in);
  }catch(CatalogException e){throw e;}catch(Exception e){throw new CatalogException("M3U nuk mund të lexohet: "+safe(e),e);}
 }
 private Catalog loadRemote(Playlist source)throws CatalogException{
  String original=source.getUrl();
  HttpUrl u=parseUrl(original);
  diag("INPUT",u,null);
  try{Catalog direct=downloadAndParse(u.toString());if(hasXtreamCredentials(u)&&needsXtreamMetadata(direct)){Log.w("LazyTV-M3U","M3U categories collapsed -> Xtream metadata fallback");return xtreamFromM3u(source,u);}return direct;}
  catch(CatalogException first){
   if(isHttp884(first)){Log.w("LazyTV-M3U","HTTP_884 host="+u.host()+" port="+u.port()+" -> Xtream fallback");return xtreamFromM3u(source,u);}
   if("https".equalsIgnoreCase(u.scheme())&&u.port()!=443&&isTlsFailure(first)){
    HttpUrl http=u.newBuilder().scheme("http").build();
    Log.w("LazyTV-M3U","TLS_FAIL custom_port="+u.port()+" host="+u.host()+" -> runtime HTTP retry");
    try{return downloadAndParse(http.toString());}
    catch(CatalogException second){
     if(isHttp884(second)||isNetworkFailure(second)){Log.w("LazyTV-M3U","HTTP_RETRY_FAIL host="+u.host()+" port="+u.port()+" -> Xtream fallback");return xtreamFromM3u(source,http);}
     throw second;
    }
   }
   if(isNetworkFailure(first)&&hasXtreamCredentials(u)){Log.w("LazyTV-M3U","NETWORK_FAIL host="+u.host()+" port="+u.port()+" -> Xtream fallback");return xtreamFromM3u(source,u);}
   throw first;
  }
 }
 private Catalog downloadAndParse(String exactUrl)throws CatalogException{
  HttpUrl u=parseUrl(exactUrl);Request req=new Request.Builder().url(u).header("User-Agent","VLC/3.0.20 LibVLC/3.0.20").header("Accept","application/x-mpegURL,application/vnd.apple.mpegurl,text/plain,*/*").header("Accept-Encoding","identity").header("Connection","keep-alive").get().build();
  try(Response res=REMOTE.newCall(req).execute()){
   int code=res.code();String ct=res.header("Content-Type","");int redirects=redirectCount(res);
   Log.d("LazyTV-M3U","HTTP_RESPONSE scheme="+res.request().url().scheme()+" host="+res.request().url().host()+" port="+res.request().url().port()+" path="+res.request().url().encodedPath()+" status="+code+" redirects="+redirects+" contentType="+ct);
   if(!res.isSuccessful())throw new CatalogException("HTTP_RESPONSE_ERROR status="+code);
   ResponseBody rb=res.body();if(rb==null)throw new CatalogException("HTTP_RESPONSE_ERROR empty_body");
   byte[] data=rb.bytes();if(data.length==0)throw new CatalogException("HTTP_RESPONSE_ERROR empty_body");
   int off=data.length>=3&&(data[0]&255)==0xEF&&(data[1]&255)==0xBB&&(data[2]&255)==0xBF?3:0;
   String probe=new String(data,off,Math.min(data.length-off,4096),StandardCharsets.UTF_8).trim();
   boolean ext=probe.startsWith("#EXTM3U");Log.d("LazyTV-M3U","BODY host="+u.host()+" bytes="+data.length+" extm3u="+ext);
   if(!ext){
    // Some providers return a bare list of stream URLs instead of EXTINF records.
    if(looksLikeUrlList(probe)) data=promoteUrlList(data,off);
    else throw new CatalogException("HTTP_RESPONSE_ERROR body_not_m3u");
   }
   try{return parseM3u(new ByteArrayInputStream(data,off,data.length-off));}catch(CatalogException e){throw e;}catch(Exception e){throw new CatalogException("Remote M3U parse: "+safe(e),e);}
  }catch(javax.net.ssl.SSLException e){throw network("TLS",u,e);}
   catch(java.net.UnknownHostException e){throw network("DNS",u,e);}
   catch(java.net.SocketTimeoutException e){throw network("TIMEOUT",u,e);}
   catch(java.net.ConnectException e){throw network("CONNECT",u,e);}
   catch(java.net.ProtocolException e){throw network("PROTOCOL",u,e);}
   catch(IOException e){throw network("IO",u,e);}
 }
 private boolean looksLikeUrlList(String probe){String[] ls=probe.split("\\r?\\n");int n=0;for(String s:ls){s=s.trim();if(s.startsWith("http://")||s.startsWith("https://"))n++;}return n>0;}
 private byte[] promoteUrlList(byte[] data,int off){String raw=new String(data,off,data.length-off,StandardCharsets.UTF_8);StringBuilder b=new StringBuilder("#EXTM3U\\n");int n=1;for(String s:raw.split("\\r?\\n")){s=s.trim();if(!(s.startsWith("http://")||s.startsWith("https://")))continue;b.append("#EXTINF:-1 group-title=\\\"Të tjera\\\",Stream ").append(n++).append("\\n").append(s).append("\\n");}return b.toString().getBytes(StandardCharsets.UTF_8);}
 private Catalog xtreamFromM3u(Playlist source,HttpUrl u)throws CatalogException{
  String user=u.queryParameter("username"),pass=u.queryParameter("password");
  if(user==null||user.isEmpty()||pass==null||pass.isEmpty())throw new CatalogException("Fallback Xtream nuk mund të përdoret: credentials mungojnë në M3U URL");
  String base=u.scheme()+"://"+u.host()+(u.port()==HttpUrl.defaultPort(u.scheme())?"":":"+u.port());
  Playlist xp=new Playlist(source.getId(),source.getName(),PlaylistType.XTREAM_CODES,base,user,pass,"",source.getUpdatedAt());
  Log.d("LazyTV-M3U","XTREAM_FALLBACK scheme="+u.scheme()+" host="+u.host()+" port="+u.port());
  try{return new XtreamCatalogSource().load(xp);}
  catch(CatalogException e){
   if("https".equalsIgnoreCase(u.scheme())&&u.port()!=443){
    String hb="http://"+u.host()+":"+u.port();Playlist hp=new Playlist(source.getId(),source.getName(),PlaylistType.XTREAM_CODES,hb,user,pass,"",source.getUpdatedAt());
    Log.d("LazyTV-M3U","XTREAM_FALLBACK_HTTP host="+u.host()+" port="+u.port());return new XtreamCatalogSource().load(hp);
   }
   throw e;
  }
 }
 private boolean needsXtreamMetadata(Catalog c){java.util.List<Category> cats=c.categories(CatalogType.LIVE);int total=0,other=0;for(Category z:cats){int n=c.items(CatalogType.LIVE,z.id).size();total+=n;String s=z.name==null?"":z.name.trim();if("Të tjera".equalsIgnoreCase(s)||"Other".equalsIgnoreCase(s)||"Uncategorized".equalsIgnoreCase(s))other+=n;}return total>100&&other>0&&(cats.size()==1||other*100/Math.max(1,total)>=35);}
 private CatalogException network(String stage,HttpUrl u,Exception e){String cause=e.getCause()==null?"none":e.getCause().getClass().getSimpleName()+":"+msg(e.getCause());String m="NETWORK_ERROR stage="+stage+" exception="+e.getClass().getSimpleName()+" cause="+cause+" scheme="+u.scheme()+" host="+u.host()+" port="+u.port()+" path="+u.encodedPath();Log.e("LazyTV-M3U",m,e);return new CatalogException(m,e);}
 private void diag(String stage,HttpUrl u,Exception e){Log.d("LazyTV-M3U","REMOTE_M3U "+stage+" scheme="+u.scheme()+" host="+u.host()+" port="+u.port()+" path="+u.encodedPath()+" query_present="+(u.querySize()>0));}
 private HttpUrl parseUrl(String s)throws CatalogException{
  if(s==null)throw new CatalogException("URL M3U nuk është valide");
  String raw=s.trim();
  // IPTV links are often pasted from chats/web forms with spaces or HTML escaped query separators.
  raw=raw.replace("&amp;","&").replace(" ","%20");
  HttpUrl u=HttpUrl.parse(raw);
  if(u==null)throw new CatalogException("URL M3U nuk është valide");
  return u;
 }
 private boolean hasXtreamCredentials(HttpUrl u){return u.queryParameter("username")!=null&&u.queryParameter("password")!=null;}
 private boolean isHttp884(CatalogException e){return e.getMessage()!=null&&e.getMessage().contains("status=884");}
 private boolean isTlsFailure(CatalogException e){String m=e.getMessage();return m!=null&&m.contains("stage=TLS");}
 private boolean isNetworkFailure(CatalogException e){String m=e.getMessage();return m!=null&&m.startsWith("NETWORK_ERROR");}
 private Catalog parseM3u(InputStream in)throws Exception{try(InputStream x=in;BufferedReader r=new BufferedReader(new InputStreamReader(x,StandardCharsets.UTF_8))){return M3uParser.parse(r);}}
 private int redirectCount(Response r){int n=0;for(Response x=r.priorResponse();x!=null;x=x.priorResponse())n++;return n;}
 private String safe(Exception e){return msg(e).replaceAll("(?i)(username|password|token|mac)=([^&\\s]+)","$1=<redacted>");}
 private String msg(Throwable e){String s=e==null?null:e.getMessage();return s==null||s.trim().isEmpty()?(e==null?"none":e.getClass().getSimpleName()):s;}
}