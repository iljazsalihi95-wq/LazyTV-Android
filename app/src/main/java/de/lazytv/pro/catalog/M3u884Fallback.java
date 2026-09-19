package de.lazytv.pro.catalog;
import android.util.Log;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import okhttp3.HttpUrl;import org.json.*;
final class M3u884Fallback{
 private M3u884Fallback(){}
 static InputStream tryBuild(String raw)throws CatalogException{
  HttpUrl u=HttpUrl.parse(raw);if(u==null)return null;String user=u.queryParameter("username"),pass=u.queryParameter("password");if(user==null||pass==null)return null;
  String base=u.scheme()+"://"+u.host()+(u.port()==HttpUrl.defaultPort(u.scheme())?"":":"+u.port());
  String auth="username="+HttpClient.enc(user)+"&password="+HttpClient.enc(pass);
  RemoteM3uDownloader.Result login=RemoteM3uDownloader.download(base+"/player_api.php?"+auth);
  JSONObject root=obj(login.body),ui=root.optJSONObject("user_info");if(ui==null||!"1".equals(ui.optString("auth")))throw new CatalogException("M3U fallback: llogaria API nuk është aktive");
  Map<String,String> liveCats=cats(RemoteM3uDownloader.download(base+"/player_api.php?action=get_live_categories&"+auth).body);
  JSONArray live=arr(RemoteM3uDownloader.download(base+"/player_api.php?action=get_live_streams&"+auth).body);
  StringBuilder m=new StringBuilder("#EXTM3U\n");append(m,live,liveCats,base,user,pass,"live","ts",false);
  Map<String,String> vodCats=cats(RemoteM3uDownloader.download(base+"/player_api.php?action=get_vod_categories&"+auth).body);
  JSONArray vod=arr(RemoteM3uDownloader.download(base+"/player_api.php?action=get_vod_streams&"+auth).body);
  append(m,vod,vodCats,base,user,pass,"movie","mp4",true);
  Log.d("LazyTV-M3U","HTTP884 fallback API OK host="+u.host()+" live="+live.length()+" vod="+vod.length());
  return new ByteArrayInputStream(m.toString().getBytes(StandardCharsets.UTF_8));
 }
 private static void append(StringBuilder m,JSONArray a,Map<String,String> cats,String base,String user,String pass,String path,String defExt,boolean movie)throws CatalogException{
  for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("stream_id");if(id.isEmpty())continue;String name=o.optString("name","Stream"),group=cats.get(o.optString("category_id"));if(group==null||group.isEmpty())group="Të tjera";if(movie)group="MOVIES | "+group;String logo=o.optString("stream_icon"),epg=o.optString("epg_channel_id"),ext=o.optString("container_extension",defExt);m.append("#EXTINF:-1 tvg-id=\"").append(esc(epg)).append("\" tvg-logo=\"").append(esc(logo)).append("\" group-title=\"").append(esc(group)).append("\",").append(name.replace("\n"," ")).append('\n');m.append(base).append('/').append(path).append('/').append(user).append('/').append(pass).append('/').append(id).append('.').append(ext).append('\n');}
 }
 private static Map<String,String> cats(String s)throws CatalogException{JSONArray a=arr(s);Map<String,String>m=new LinkedHashMap<>();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)m.put(o.optString("category_id"),o.optString("category_name","Të tjera"));}return m;}
 private static JSONObject obj(String s)throws CatalogException{try{return new JSONObject(s);}catch(Exception e){throw new CatalogException("M3U fallback API login invalid",e);}}
 private static JSONArray arr(String s)throws CatalogException{try{return new JSONArray(s);}catch(Exception e){throw new CatalogException("M3U fallback API response invalid",e);}}
 private static String esc(String s){return s==null?"":s.replace("\\","").replace("\"","'");}
}