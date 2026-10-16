package de.lazytv.pro.catalog;
import android.content.Context;import org.json.*;import java.io.*;import java.util.*;
/** Original owner API contract, independent of personal Xtream/Stalker identity. */
public final class ManagedBackendCatalogSource {
 private String registryError="";public String registryError(){return registryError;}private final String endpoint; private final ManagedSheetCatalogSource sheets=new ManagedSheetCatalogSource();
 public ManagedBackendCatalogSource(Context context)throws CatalogException {try(InputStream in=context.getAssets().open("lazytv_managed_backend.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);endpoint=new JSONObject(bytes.toString("UTF-8")).getString("catalog_url");}catch(Exception invalid){throw new CatalogException("Konfigurimi i katalogut LazyTV nuk mund të lexohet");}}
 private final Map<String,String> labels=new LinkedHashMap<>();
 public Map<String,String> serverLabels(){return new LinkedHashMap<>(labels);}
 public List<String> servers()throws CatalogException {
  List<String> result=new ArrayList<>();labels.clear();registryError="";
  try{JSONObject data=new JSONObject(HttpClient.get(endpoint+"?action=servers&_ts="+System.currentTimeMillis(),null));if(!data.optBoolean("ok"))throw new CatalogException("API LazyTV refuzoi katalogun");JSONArray rows=data.optJSONArray("servers");if(rows!=null)for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);String name=row==null?"":row.optString("name").trim();if(!name.isEmpty()&&!name.equalsIgnoreCase("MASTER")&&!result.contains(name))result.add(name);}}catch(CatalogException|JSONException unavailable){registryError="API LazyTV nuk mund të lexohet";}
  try{for(ManagedSheetCatalogSource.Server server:sheets.servers().values()){if(!result.contains(server.id))result.add(server.id);labels.put(server.id,server.name.equals(server.id)?server.id:server.id+" • "+server.name);}}catch(CatalogException unavailable){registryError=registryError.isEmpty()?"Regjistri SOURCES nuk mund të lexohet":registryError+"; SOURCES nuk mund të lexohet";}
  if(result.isEmpty()&&!registryError.isEmpty())throw new CatalogException(registryError);return result;
 }
 public Catalog load(String server)throws CatalogException {
  CatalogException apiFailure=null,sheetFailure=null;String apiServer=legacyApiName(server);
  // Use the owner API first for every managed server. It is the same fast path used by the
  // working managed providers and returns the current playback URL. Sheets remain a fallback
  // for catalog continuity when the API is temporarily unavailable or has no rows.
  try{Catalog api=parse(apiServer,HttpClient.get(endpoint+"?action=channels&server="+HttpClient.enc(apiServer)+"&_ts="+System.currentTimeMillis(),null));if(!api.isEmpty())return api;}catch(CatalogException unavailable){apiFailure=unavailable;}
  ManagedSheetCatalogSource.Server registered=server!=null&&server.matches("SERVER_[0-9]+")?sheets.servers().get(server):null;
  if(registered!=null){try{Catalog catalog=sheets.load(registered);if(!catalog.isEmpty())return catalog;}catch(CatalogException unavailable){sheetFailure=unavailable;}}
  if(sheetFailure!=null)throw sheetFailure;if(apiFailure!=null)throw apiFailure;throw new CatalogException("Serveri nuk ka kanale aktive");
 }
 public Catalog loadType(String server,CatalogType type)throws CatalogException {
  Catalog all=load(server),out=new Catalog();CatalogException firstFailure=null;
  for(Category c:all.categories)if(c.type==type)out.categories.add(c);
  for(StreamItem item:all.items)if(item.type==type&&!(item instanceof Episode)){
   if(type==CatalogType.MOVIES&&ManagedStalkerLinkResolver.needsMovieCommand(item)){try{out.items.add(ManagedStalkerLinkResolver.verifyMovie(item));}catch(CatalogException unverified){if(firstFailure==null)firstFailure=unverified;}}else out.items.add(item);
  }
  if(out.isEmpty()&&firstFailure!=null)throw firstFailure;return out;
 }
 public SeriesDetails loadSeries(String server,Series series)throws CatalogException {Catalog all=load(server);SeriesDetails out=new SeriesDetails();out.metadata.putAll(series.metadata);Set<Integer> seasons=new TreeSet<>();for(StreamItem item:all.items)if(item instanceof Episode){Episode episode=(Episode)item;if(series.id.equals(episode.seriesId)){seasons.add(episode.seasonNumber);out.episodes.computeIfAbsent(episode.seasonNumber,n->new ArrayList<>()).add(episode);}}for(int number:seasons){out.seasons.add(new Season(series.id+"|"+number,"Sezoni "+number,series.id,number,series.logo));out.episodes.get(number).sort((a,b)->Integer.compare(a.episodeNumber,b.episodeNumber));}return out;}
 public ResolvedStream resolve(String server,StreamItem selected,boolean forceFresh)throws CatalogException {
  StreamItem item=selected;
  if(forceFresh){Catalog catalog=load(server);item=null;for(StreamItem candidate:catalog.items)if(candidate.type==selected.type&&(candidate.id.equals(selected.id)||candidate.id.equals(selected.meta("managed_original_item_id")))){item=candidate;break;}if(item==null)throw new CatalogException("Kanali nuk është më në katalogun e serverit");}
  String raw=item.streamUrl==null?"":item.streamUrl.trim();
  // First click must behave like Premium: hand a direct HTTP(S) URL to the player immediately.
  // Refresh an expiring LazyMapper token only on the bounded retry path after playback actually
  // fails; otherwise a slow owner-API request can make a visible channel look unclickable.
  if(forceFresh&&server!=null&&server.matches("SERVER_[0-9]+")&&!"SERVER_10".equalsIgnoreCase(server)&&expiring(raw)){
   try{String freshServer=legacyApiName(server);Catalog fresh=parse(freshServer,HttpClient.get(endpoint+"?action=channels&server="+HttpClient.enc(freshServer)+"&_ts="+System.currentTimeMillis(),null));for(StreamItem candidate:fresh.items){String source=item.meta("managed_source_id");boolean sameId=candidate.id.equals(item.id)||(!source.isEmpty()&&candidate.id.equals(source))||item.id.endsWith("|"+candidate.id);if(candidate.type==item.type&&(sameId||candidate.name.equals(item.name))){item=candidate;raw=item.streamUrl==null?"":item.streamUrl.trim();break;}}}catch(CatalogException unavailable){/* Keep the sheet row as a bounded fallback. */}
  }
  String url=raw;
  if(url.regionMatches(true,0,"ffmpeg ",0,7))url=url.substring(7).trim();
  if(url.matches("(?i)^https?://.*")){Map<String,String> headers=new LinkedHashMap<>();headers.put("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");headers.put("Accept","*/*");Map<String,String> metadata=new LinkedHashMap<>(item.metadata);metadata.put("managed_direct","true");return new ResolvedStream(url,item.meta("managed_transport").isEmpty()?item.type.name():item.meta("managed_transport"),item.name,item.logo,item.tvgId,headers,metadata);}
  if(ManagedStalkerLinkResolver.canRefresh(item))return ManagedStalkerLinkResolver.resolve(item);
  throw new CatalogException("Backend-i nuk ktheu link playback të vlefshëm");
 }
 static boolean expiring(String url){if(url==null)return false;String lower=url.toLowerCase(Locale.ROOT);return lower.contains("play_token=")||lower.contains("token=")||lower.contains("expires=")||lower.startsWith("ffmpeg ");}
 static Catalog parse(String server,String body)throws CatalogException {try{JSONObject root=new JSONObject(body);if(!root.optBoolean("ok"))throw new CatalogException("API LazyTV refuzoi kanalet");JSONArray rows=root.optJSONArray("channels");if(rows==null)throw new CatalogException("API LazyTV nuk ktheu kanale");Catalog catalog=new Catalog();Map<String,Category> categories=new LinkedHashMap<>();for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(row==null||!row.optBoolean("active",true))continue;String name=first(row,"name","tvgName"),url=first(row,"url","STREAM_URL_CMD");if(name.isEmpty()||url.isEmpty())continue;String category=first(row,"category","group");if(category.isEmpty())category="Të tjera";boolean linear24="live24".equalsIgnoreCase(first(row,"type"))||ManagedSheetCatalogSource.twentyFourCategory(category);String country=ManagedCountryResolver.resolve(first(row,"country_code","countryCode"),first(row,"country"),category,name);String categoryId="managed|"+server+"|"+(linear24?"24_7|":"")+country+"|"+category;String id=first(row,"id","sourceId","streamId");if(id.isEmpty())id=server+"|"+name+"|"+category;if(!categories.containsKey(categoryId)){Category value=new Category(categoryId,(linear24?"24/7 • ":"")+category,CatalogType.LIVE,country);categories.put(categoryId,value);catalog.categories.add(value);}Map<String,String> metadata=new LinkedHashMap<>();metadata.put("managed_server",server);metadata.put("managed_transport",first(row,"type"));metadata.put("managed_tab",linear24?"24_7":"LIVE");catalog.items.add(new StreamItem(id,name,categoryId,first(row,"logo"),url,first(row,"tvgId"),CatalogType.LIVE,Collections.emptyList(),metadata));}if(catalog.isEmpty())throw new CatalogException("Serveri nuk ka kanale aktive");return catalog;}catch(JSONException invalid){throw new CatalogException("Përgjigjja e API LazyTV nuk është e vlefshme");}}
 private static String legacyApiName(String server){if(server==null)return "";String value=server.trim();if(value.equalsIgnoreCase("KRYSTAL"))return "Krystal";if(value.equalsIgnoreCase("LAZYIPTV")||value.equalsIgnoreCase("LAZIIPTV")||value.equalsIgnoreCase("LAZI IPTV"))return "LAZI IPTV";if(value.equalsIgnoreCase("MASTER"))return "MASTER";if(value.equalsIgnoreCase("SARAY DARK")||value.equalsIgnoreCase("SARAYDARK"))return "SARAY DARK";return value;}
 private static String first(JSONObject row,String... keys){for(String key:keys){String value=row.optString(key).trim();if(!value.isEmpty()&&!"null".equals(value))return value;}return "";}
}
