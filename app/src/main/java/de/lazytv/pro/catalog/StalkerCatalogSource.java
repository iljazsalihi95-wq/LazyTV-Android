package de.lazytv.pro.catalog;
import org.json.*;import java.util.*;import android.util.Log;import de.lazytv.pro.playlist.Playlist;
public class StalkerCatalogSource {
 private String endpoint="",requestedEndpoint="",mac="",token=""; private Map<String,String> headers=new LinkedHashMap<>(); private final java.net.CookieManager cookies=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER); private long sessionAt=0; private static final long SESSION_MS=20*60*1000L; private static final Map<String,String> ENDPOINT_CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 public static class LinkResult{public final String url;public final Map<String,String> headers;LinkResult(String u,Map<String,String>h){url=u;headers=h;}}
 public synchronized void clear(){token="";sessionAt=0;headers.clear();cookies.getCookieStore().removeAll();}
 private synchronized void ensure(Playlist p)throws CatalogException{String ep=normalize(p.getUrl()),m=p.getMacAddress().trim();if(!token.isEmpty()&&ep.equals(requestedEndpoint)&&m.equals(mac)&&System.currentTimeMillis()-sessionAt<SESSION_MS)return;if(!ep.equals(requestedEndpoint)||!m.equals(mac))cookies.getCookieStore().removeAll();endpoint=ep;requestedEndpoint=ep;mac=m;headers=new LinkedHashMap<>();headers.put("Cookie","mac="+mac+"; stb_lang=en; timezone=Europe%2FBerlin;");headers.put("X-User-Agent","Model: MAG254; Link: Ethernet");headers.put("Referer",portalReferer(endpoint));headers.put("Origin",portalOrigin(endpoint));headers.put("Accept","*/*");handshake();}
 private void handshake()throws CatalogException{JSONObject h=data(raw("type=stb&action=handshake&token=&prehash=0&JsHttpRequest=1-xml"));token=h.optString("token");if(token.isEmpty())throw new CatalogException("Stalker handshake dështoi: token mungon");headers.put("Authorization","Bearer "+token);sessionAt=System.currentTimeMillis();try{raw("type=stb&action=get_profile&hd=1&stb_type=MAG254&JsHttpRequest=1-xml");}catch(Exception e){Log.d("LazyTV-Stalker","profile optional failure="+safe(e));}try{accountState(data(raw("type=account_info&action=get_main_info&JsHttpRequest=1-xml")));}catch(ProviderHttpException e){if(e.status==402||e.status==401||e.status==403){token="";sessionAt=0;throw e;}}catch(CatalogException e){if(e.getMessage()!=null&&e.getMessage().startsWith("Llogaria"))throw e;Log.d("LazyTV-Stalker","account information unavailable");}}
 private void accountState(JSONObject account)throws CatalogException{
  String status=account.optString("status",account.optString("account_status")).toLowerCase(Locale.US);
  if(status.equals("expired")||status.equals("disabled")||status.equals("banned")||account.optInt("blocked",0)==1||account.optInt("is_blocked",0)==1){token="";sessionAt=0;throw new CatalogException("Llogaria e portalit është "+(status.isEmpty()?"blocked":status));}
 }
 private String get(String q)throws CatalogException{
  try{return authorizedRaw(q);}catch(CatalogException e){if(!token.isEmpty()&&isAuthFailure(e)){refreshSession();return authorizedRaw(q);}throw e;}
 }
 private String authorizedRaw(String q)throws CatalogException{
  String body=raw(q);try{Object decoded=new JSONTokener(body.trim()).nextValue();
   if(decoded instanceof JSONObject){JSONObject root=(JSONObject)decoded,d=root.optJSONObject("js");if(d==null)d=root;
    if(looksExpired(d))throw new CatalogException("Stalker unauthorized: session-i u refuzua");
   }
  }catch(CatalogException e){throw e;}catch(Exception ignored){}return body;
 }
 private void refreshSession()throws CatalogException{token="";sessionAt=0;headers.remove("Authorization");handshake();}
 private boolean isAuthFailure(CatalogException e){String s=e.getMessage();if(s==null)return false;s=s.toLowerCase(Locale.US);return s.contains("http 401")||s.contains("http 403")||s.contains("credentials/refuzim")||s.contains("unauthorized")||s.contains("forbidden")||s.contains("token expired");}
 private String raw(String q)throws CatalogException{
  CatalogException last=null;for(String ep:endpoints(endpoint)){
   try{String body=HttpClient.get(ep+"?"+q,headers,cookies);
    Object decoded;try{decoded=new JSONTokener(body.trim()).nextValue();}catch(Exception e){throw new CatalogException("Përgjigje Stalker e pavlefshme",e);}
    if(!(decoded instanceof JSONObject)&&!(decoded instanceof JSONArray))throw new CatalogException("Përgjigje Stalker e pavlefshme");
    if(q.contains("action=handshake")&&data(body).optString("token").isEmpty())throw new CatalogException("Stalker handshake: token mungon");
    endpoint=ep;headers.put("Referer",portalReferer(ep));headers.put("Origin",portalOrigin(ep));ENDPOINT_CACHE.put(portalOrigin(ep),ep);
    return body;
   }catch(ProviderHttpException e){if(e.status!=404&&e.status!=405)throw e;last=e;}
    catch(CatalogException e){last=e;}
  }throw last==null?new CatalogException("Stalker portal nuk u përgjigj"):last;
 }
 private java.util.List<String> endpoints(String ep){java.util.LinkedHashSet<String>x=new java.util.LinkedHashSet<>();String origin=portalOrigin(ep),cached=ENDPOINT_CACHE.get(origin);if(cached!=null&&!cached.isEmpty())x.add(cached);x.add(ep);String path=portalPath(ep),base=origin;if(path.startsWith("/stalker_portal/"))base=origin+"/stalker_portal";else{for(String marker:new String[]{"/portal.php","/server/load.php","/c/portal.php","/c/server/load.php"}){int cut=path.indexOf(marker);if(cut>0){base=origin+path.substring(0,cut);break;}}}x.add(base+"/portal.php");x.add(base+"/server/load.php");x.add(base+"/c/portal.php");x.add(base+"/c/server/load.php");x.add(origin+"/portal.php");x.add(origin+"/server/load.php");x.add(origin+"/c/portal.php");x.add(origin+"/c/server/load.php");x.add(origin+"/stalker_portal/server/load.php");x.add(origin+"/stalker_portal/portal.php");return new java.util.ArrayList<>(x);}
 private String step(String name,String q)throws CatalogException{try{String x=get(q);Log.d("LazyTV-Stalker","STALKER STEP="+name+" OK host="+host(endpoint));return x;}catch(CatalogException e){Log.e("LazyTV-Stalker","STALKER STEP="+name+" FAIL host="+host(endpoint)+" error="+safe(e));throw e;}}
 public synchronized Catalog load(Playlist p)throws CatalogException{ensure(p);Catalog c=new Catalog();load(c,"itv",CatalogType.LIVE);if(c.isEmpty())throw new CatalogException("Portal u lidh, por nuk ktheu LIVE të lexueshme");return c;} public synchronized Catalog loadType(Playlist p,CatalogType wanted)throws CatalogException{ensure(p);Catalog c=new Catalog();if(wanted==CatalogType.LIVE)load(c,"itv",wanted);else if(wanted==CatalogType.MOVIES)load(c,"vod",wanted);else if(wanted==CatalogType.SERIES)load(c,"series",wanted);if(c.isEmpty())throw new CatalogException("Portal u lidh, por kjo rubrikë është bosh");return c;}
 public synchronized Map<String,String> loadMovieInfo(Playlist p,StreamItem movie)throws CatalogException{
  ensure(p);JSONObject d=data(get("type=vod&action=get_movie&movie_id="+HttpClient.enc(movie.id)+"&JsHttpRequest=1-xml"));
  Map<String,String> out=new LinkedHashMap<>(movie.metadata);out.putAll(ProviderMetadata.read(d));
  for(String key:new String[]{"info","movie_data","movie"}){JSONObject nested=d.optJSONObject(key);if(nested!=null)out.putAll(ProviderMetadata.read(nested));}
  return out;
 }
 public synchronized SeriesDetails loadSeries(Playlist p,Series series)throws CatalogException{
  ensure(p);SeriesDetails d=new SeriesDetails();d.metadata.putAll(series.metadata);String sid=series.id;
  JSONArray seasons;
  try{seasons=flexArray(get("type=series&action=get_ordered_list&movie_id="+HttpClient.enc(sid)+"&JsHttpRequest=1-xml"));}
  catch(ProviderHttpException e){if(e.status!=404&&e.status!=405)throw e;seasons=new JSONArray();}
  if(seasons.length()==0)seasons=flexArray(get("type=vod&action=get_ordered_list&movie_id="+HttpClient.enc(sid)+"&JsHttpRequest=1-xml"));
  for(int i=0;i<seasons.length();i++){
   JSONObject o=seasons.optJSONObject(i);if(o==null)continue;
   int sn=o.optInt("season",o.optInt("season_number",i+1));String seasonId=o.optString("id",String.valueOf(sn));
   d.seasons.add(new Season(seasonId,o.optString("name",o.optString("title","Season "+sn)),sid,sn,o.optString("screenshot_uri",series.logo)));
   JSONArray embedded=o.optJSONArray("episodes");if(embedded==null)embedded=o.optJSONArray("series");
   if(embedded!=null)parseSeasonEpisodes(d,embedded,sid,sn,series.logo,o);
   else parseEpisodes(d,tryEpisodes(sid,seasonId,sn),sid,sn,series.logo);
  }
  if(d.seasons.isEmpty()){JSONArray eps=tryEpisodes(sid,"",0);parseEpisodes(d,eps,sid,0,series.logo);for(Integer sn:d.episodes.keySet())d.seasons.add(new Season(String.valueOf(sn),"Season "+sn,sid,sn,series.logo));}
  if(d.episodes.isEmpty())throw new CatalogException("Portali nuk ktheu episode për këtë serial");return d;
 }
 private void parseSeasonEpisodes(SeriesDetails d,JSONArray entries,String sid,int sn,String cover,JSONObject season){
  JSONArray objects=new JSONArray();
  for(int i=0;i<entries.length();i++){
   Object entry=entries.opt(i);if(entry instanceof JSONObject){objects.put(entry);continue;}
   if(entry==null||entry==JSONObject.NULL)continue;int number;
   try{number=Integer.parseInt(String.valueOf(entry));}catch(Exception ignored){continue;}
   if(number<=0)continue;try{JSONObject ep=new JSONObject();
    ep.put("id",season.optString("id")+"_"+number);ep.put("episode",number);ep.put("season",sn);
    ep.put("name","Episode "+number);ep.put("cmd",season.optString("cmd",season.optString("url")));
    ep.put("screenshot_uri",season.optString("screenshot_uri",cover));objects.put(ep);
   }catch(JSONException ignored){}
  }parseEpisodes(d,objects,sid,sn,cover);
 }
 private JSONArray tryEpisodes(String sid,String seasonId,int sn){String[]qs={"type=series&action=get_ordered_list&movie_id="+HttpClient.enc(sid)+"&season_id="+HttpClient.enc(seasonId)+"&JsHttpRequest=1-xml","type=vod&action=get_ordered_list&movie_id="+HttpClient.enc(sid)+"&season="+sn+"&JsHttpRequest=1-xml"};for(String q:qs)try{JSONArray a=flexArray(get(q));if(a.length()>0)return a;}catch(Exception ignored){}return new JSONArray();}
 private void parseEpisodes(SeriesDetails d,JSONArray a,String sid,int fallback,String cover){for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e==null)continue;int sn=e.optInt("season",e.optInt("season_number",fallback>0?fallback:1)),en=e.optInt("episode",e.optInt("episode_num",i+1));String id=e.optString("id",e.optString("ch_id")),cmd=e.optString("cmd",e.optString("url"));Map<String,String>meta=ProviderMetadata.read(e);meta.put("stalker_series",String.valueOf(en));Episode z=new Episode(id,e.optString("name",e.optString("title","Episode "+en)),sid,"",e.optString("screenshot_uri",e.optString("logo",cover)),cmd,e.optString("container_extension"),e.optString("time",e.optString("duration")),sn,en,meta);d.episodes.computeIfAbsent(sn,k->new ArrayList<>()).add(z);}}
 public synchronized LinkResult resolve(Playlist p,StreamItem item)throws CatalogException{ensure(p);String cmd=item.streamUrl;if(cmd==null||cmd.trim().isEmpty())throw new CatalogException("Portal stream reference mungon");String portalType=item.type==CatalogType.LIVE?"itv":"vod",q="type="+portalType+"&action=create_link&cmd="+HttpClient.enc(cmd)+"&series="+(item instanceof Episode?String.valueOf(((Episode)item).episodeNumber):"0")+"&forced_storage=undefined&disable_ad=0&download=0&JsHttpRequest=1-xml";JSONObject d=data(get(q));String out=cleanCmd(d.optString("cmd",d.optString("url")));if(out.isEmpty()&&looksExpired(d)){refreshSession();d=data(raw(q));out=cleanCmd(d.optString("cmd",d.optString("url")));}if(out.isEmpty())throw new CatalogException("Portali nuk ktheu URL për stream-in");Map<String,String>h=new LinkedHashMap<>();try{h.put("Cookie",HttpClient.cookieHeader(cookies,new java.net.URL(endpoint),headers.get("Cookie")));}catch(Exception e){h.put("Cookie",headers.get("Cookie"));}h.put("X-User-Agent",headers.get("X-User-Agent"));h.put("User-Agent","Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG254 stbapp");for(String k:new String[]{"Origin","Authorization","Referer"})if(headers.get(k)!=null)h.put(k,headers.get(k));return new LinkResult(out,h);}
 private boolean looksExpired(JSONObject d){StringBuilder b=new StringBuilder();for(String k:new String[]{"error","message","msg","status"}){String v=d.optString(k);if(v!=null&&!v.isEmpty())b.append(' ').append(v);}String s=b.toString().toLowerCase(Locale.US);return s.contains("unauthorized")||s.contains("forbidden")||s.contains("expired")||s.contains("invalid token")||s.contains("authorization failed")||s.contains("authorization required");}
 private void load(Catalog c,String type,CatalogType ct)throws CatalogException{JSONArray gs;try{gs=flexArray(step(type+"_categories","type="+type+"&action="+(type.equals("itv")?"get_genres":"get_categories")+"&JsHttpRequest=1-xml"));}catch(ProviderHttpException e){if(e.status!=404&&e.status!=405)throw e;gs=new JSONArray();}catch(CatalogException e){gs=new JSONArray();}for(int i=0;i<gs.length();i++){JSONObject g=gs.optJSONObject(i);if(g!=null)c.categories.add(new Category(g.optString("id"),g.optString("title",g.optString("name","Të tjera")),ct,g.optString("country",g.optString("country_code",""))));}Set<String>seen=new HashSet<>();for(int page=1;page<=50;page++){String resp;try{resp=step(type+"_channels_p"+page,"type="+type+"&action=get_ordered_list&p="+page+"&JsHttpRequest=1-xml");}catch(CatalogException first){if(!type.equals("itv"))throw first;resp=step(type+"_all_p"+page,"type=itv&action=get_all_channels&p="+page+"&JsHttpRequest=1-xml");}JSONObject root=json(resp),js=root.optJSONObject("js");if(js==null)js=root.optJSONObject("data");if(js==null)js=root;JSONArray a=js.optJSONArray("data");if(a==null)a=js.optJSONArray("items");if(a==null)a=js.optJSONArray("results");if(a==null){Object direct=root.opt("js");if(direct instanceof JSONArray)a=(JSONArray)direct;}if(a==null||a.length()==0)break;int before=seen.size();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("id",o.optString("ch_id"));if(id.isEmpty()||!seen.add(id))continue;String cat=o.optString("tv_genre_id",o.optString("category_id",o.optString("genre_id","")));c.items.add(CatalogItems.create(id,o.optString("name","Stream"),cat,o.optString("logo",o.optString("screenshot_uri")),o.optString("cmd",o.optString("url")),"",ct,ProviderMetadata.read(o)));}int total=js.optInt("total_items",js.optInt("total",0));if(total>0&&seen.size()>=total)break;if(seen.size()==before)break;}removeEmptyCategories(c,ct);}
 private void removeEmptyCategories(Catalog c,CatalogType t){HashSet<String>used=new HashSet<>();for(StreamItem i:c.items)if(i.type==t&&i.categoryId!=null&&!i.categoryId.isEmpty())used.add(i.categoryId);Iterator<Category>it=c.categories.iterator();while(it.hasNext()){Category z=it.next();if(z.type==t&&!used.contains(z.id))it.remove();}}
 private JSONObject json(String s)throws CatalogException{try{String t=s==null?"":s.trim();int a=t.indexOf('{'),b=t.lastIndexOf('}');if(a>=0&&b>a)t=t.substring(a,b+1);if(t.startsWith("\uFEFF"))t=t.substring(1);return new JSONObject(t);}catch(Exception e){throw new CatalogException("Përgjigje Stalker e pavlefshme",e);}}
 private JSONObject data(String s)throws CatalogException{JSONObject j=json(s);Object x=j.opt("js");if(x instanceof JSONObject)return(JSONObject)x;Object d=j.opt("data");if(d instanceof JSONObject)return(JSONObject)d;return j;}
 private JSONArray flexArray(String s)throws CatalogException{Object direct;try{direct=new JSONTokener(s.trim()).nextValue();}catch(Exception e){throw new CatalogException("Përgjigje Stalker e pavlefshme",e);}if(direct instanceof JSONArray)return(JSONArray)direct;JSONObject j=json(s);Object x=j.opt("js");if(x instanceof JSONArray)return(JSONArray)x;if(x instanceof JSONObject){JSONObject o=(JSONObject)x;for(String k:new String[]{"data","items","results"}){JSONArray a=o.optJSONArray(k);if(a!=null)return a;}}JSONArray d=j.optJSONArray("data");return d!=null?d:new JSONArray();}
 private String cleanCmd(String s){if(s==null)return"";s=s.trim();if(s.startsWith("ffmpeg "))s=s.substring(7).trim();if(s.startsWith("auto "))s=s.substring(5).trim();int h=s.indexOf("http://"),hs=s.indexOf("https://"),at=h>=0&&hs>=0?Math.min(h,hs):Math.max(h,hs);if(at>0)s=s.substring(at);return s.trim();}
 private String portalOrigin(String ep){try{java.net.URL u=new java.net.URL(ep);return u.getProtocol()+"://"+u.getAuthority();}catch(Exception e){return"";}}
 private String portalPath(String ep){try{return new java.net.URL(ep).getPath();}catch(Exception e){return"";}}
 private String portalReferer(String ep){String o=portalOrigin(ep),p=portalPath(ep);return p.startsWith("/stalker_portal/")?o+"/stalker_portal/c/":o+"/c/";}
 private String host(String u){try{return new java.net.URL(u).getHost();}catch(Exception e){return"unknown";}}
 private String safe(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s.replaceAll("(?i)(token|mac|password|username)=[^&\\s]+","$1=<redacted>");}
 private String normalize(String u){u=u==null?"":u.trim().replace("&amp;","&");int q=u.indexOf('?');if(q>0)u=u.substring(0,q);u=u.replaceAll("/+$","");if(u.endsWith("/portal.php")||u.endsWith("/server/load.php")||u.endsWith("/c/portal.php")||u.endsWith("/c/server/load.php"))return u;if(u.endsWith("/stalker_portal/c"))return u.substring(0,u.length()-2)+"/server/load.php";if(u.endsWith("/c"))u=u.substring(0,u.length()-2);if(u.endsWith("/stalker_portal"))return u+"/server/load.php";return u+"/portal.php";}
}