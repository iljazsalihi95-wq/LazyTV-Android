package de.lazytv.pro.catalog;
import android.content.Context;import android.net.Uri;import android.util.Log;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import de.lazytv.pro.playlist.*;
public class M3uCatalogSource {
 public Catalog load(Context ctx,Playlist p)throws CatalogException{
  try{
   if(p.getType()==PlaylistType.M3U_URL){
    String body=HttpClient.get(p.getUrl(),Collections.emptyMap());
    if(body==null)throw new CatalogException("Remote M3U response është bosh");
    String normalized=stripBom(body);
    if(!normalized.trim().startsWith("#EXTM3U"))throw new CatalogException("Remote URL nuk ktheu M3U (#EXTM3U mungon)");
    Log.d("LazyTV-M3U","REMOTE_DOWNLOAD OK host="+host(p.getUrl())+" bytes="+normalized.getBytes(StandardCharsets.UTF_8).length+" extm3u=true");
    return parseM3u(new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8)));
   }
   InputStream in=ctx.getContentResolver().openInputStream(Uri.parse(p.getUrl()));
   if(in==null)throw new CatalogException("Skedari M3U nuk mund të hapet");
   return parseM3u(in);
  }catch(CatalogException e){throw e;}catch(Exception e){throw new CatalogException("M3U nuk mund të lexohet: "+e.getMessage(),e);}
 }
 private Catalog parseM3u(InputStream in)throws Exception{
  try(InputStream x=in;BufferedReader r=new BufferedReader(new InputStreamReader(x,StandardCharsets.UTF_8))){return M3uParser.parse(r);}
 }
 private String stripBom(String s){return s!=null&&!s.isEmpty()&&s.charAt(0)=='\ufeff'?s.substring(1):s;}
 private String host(String u){try{return new java.net.URL(u).getHost();}catch(Exception e){return"unknown";}}
}