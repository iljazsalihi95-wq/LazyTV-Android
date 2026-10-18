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
  try{appendApiServers(HttpClient.get(endpoint+"?action=servers",null),result,labels);}catch(Exception e){registryError="API e regjistrit nuk u lexua";}
  try{for(ManagedSheetCatalogSource.Server source:sheets.servers().values()){if(!result.contains(source.id))result.add(source.id);labels.put(source.id,source.name);}}catch(Exception e){registryError="SOURCES nuk u lexua";}
  // Retain numbered entry points when the registry is temporarily unavailable.
  for(int n=1;n<=20;n++){String id=String.format(Locale.US,"SERVER_%02d",n);if(!result.contains(id))result.add(id);if(!labels.containsKey(id))labels.put(id,"Server "+n);}
  return result;
 }
 static void appendApiServers(String body,List<String> result,Map<String,String> labels)throws Exception{
  JSONObject root=new JSONObject(body);if(!root.optBoolean("ok"))throw new CatalogException("SERVER_REGISTRY_REJECTED");JSONArray rows=root.getJSONArray("servers");
  for(int n=0;n<rows.length();n++){Object raw=rows.get(n);String name;if(raw instanceof JSONObject){JSONObject row=(JSONObject)raw;if(!row.optBoolean("active",true))continue;name=row.optString("name",row.optString("id","")).trim();}else name=String.valueOf(raw).trim();if(name.isEmpty()||name.length()>120)continue;if(!result.contains(name))result.add(name);labels.put(name,name);}
 }
 public Catalog load(String server)throws CatalogException {
  CatalogException apiFailure=null,sheetFailure=null;String apiServer=legacyApiName(server);
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
  // Managed Live rows that contain the portal/MAC/original command must never prefer a stale
  // Sheet/API playback token. Use the proven Server10 fresh create_link flow for every managed
  // server (including SERVER_06 and SERVER_09); its own bounded session handling refreshes tokens.
  if(selected!=null&&selected.type==CatalogType.LIVE&&ServerTenPlaybackResolver.canRefresh(selected))return ServerTenPlaybackResolver.resolve(selected);
  if(ManagedStalkerLinkResolver.canRefresh(selected))return ManagedStalkerLinkResolver.resolve(selected);
  StreamItem item=selected;String raw=item.streamUrl==null?"":item.streamUrl.trim();
  if(forceFresh&&expiring(raw)){
   try{String freshServer=legacyApiName(server);Catalog fresh=parse(freshServer,HttpClient.get(endpoint+"?action=channels&server="+HttpClient.enc(freshServer)+"&_ts="+System.currentTimeMillis(),null));StreamItem matched=matchSelected(fresh,selected);if(matched!=null){item=matched;raw=item.streamUrl==null?"":item.streamUrl.trim();}}catch(CatalogException unavailable){}
  }
  if(item.type==CatalogType.LIVE&&ServerTenPlaybackResolver.canRefresh(item))return ServerTenPlaybackResolver.resolve(item);
  if(ManagedStalkerLinkResolver.canRefresh(item))return ManagedStalkerLinkResolver.resolve(item);
  String url=raw;if(url.regionMatches(true,0,"ffmpeg ",0,7))url=url.substring(7).trim();
  if(url.matches("(?i)^https?://.*"))return DirectPlaybackRequest.resolve(item,url,item.meta("managed_transport").isEmpty()?item.type.name():item.meta("managed_transport"));
  throw new CatalogException("Backend-i nuk ktheu link playback të vlefshëm");
 }
 private static StreamItem matchSelected(Catalog catalog,StreamItem selected){String selectedSource=selected.meta("managed_source_id"),selectedOriginal=selected.meta("managed_original_item_id");for(StreamItem candidate:catalog.items){String candidateSource=candidate.meta("managed_source_id");boolean sameId=candidate.id.equals(selected.id)||(!selectedSource.isEmpty()&&(candidate.id.equals(selectedSource)||candidate.id.endsWith("|"+selectedSource)||selected.id.endsWith("|"+candidate.id)||selectedSource.equals(candidateSource)))||(!selectedOriginal.isEmpty()&&candidate.id.equals(selectedOriginal));boolean sameName=selected.name!=null&&selected.name.equals(candidate.name);if(candidate.type==selected.type&&(sameId||sameName))return candidate;}return null;}
 static boolean expiring(String url){if(url==null)return false;String lower=url.toLowerCase(Locale.ROOT);return lower.contains("play_token=")||lower.contains("token=")||lower.contains("expires=")||lower.startsWith("ffmpeg ");}
 static Catalog parse(String server,String body)throws CatalogException {try{JSONObject root=new JSONObject(body);if(!root.optBoolean("ok"))throw new CatalogException("API LazyTV refuzoi kanalet");JSONArray rows=root.optJSONArray("channels");if(rows==null)throw new CatalogException("API LazyTV nuk ktheu kanale");Catalog catalog=new Catalog();Map<String,Category> categories=new LinkedHashMap<>();for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(row==null||!row.optBoolean("active",true))continue;String name=first(row,"name","tvgName"),url=first(row,"url","STREAM_URL_CMD");if(name.isEmpty()||url.isEmpty())continue;String category=first(row,"category","group");if(category.isEmpty())category="Të tjera";boolean linear24="live24".equalsIgnoreCase(first(row,"type"))||ManagedSheetCatalogSource.twentyFourCategory(category);String country=ManagedCountryResolver.resolve(first(row,"country_code","countryCode"),first(row,"country"),category,name);String categoryId="managed|"+server+"|"+(linear24?"24_7|":"")+country+"|"+category;String id=first(row,"id","sourceId","streamId");if(id.isEmpty())id=server+"|"+name+"|"+category;if(!categories.containsKey(categoryId)){Category value=new Category(categoryId,(linear24?"24/7 • ":"")+category,CatalogType.LIVE,country);categories.put(categoryId,value);catalog.categories.add(value);}Map<String,String> metadata=new LinkedHashMap<>();metadata.put("managed_server",server);JSONObject suppliedHeaders=row.optJSONObject("headers");if(suppliedHeaders==null)suppliedHeaders=row.optJSONObject("request_headers");for(String header:new String[]{"User-Agent","Referer","Origin","Cookie","Authorization","Accept","X-User-Agent"}){String value=suppliedHeaders==null?"":suppliedHeaders.optString(header,"");if(value.isEmpty())value=first(row,header,header.toLowerCase(Locale.ROOT).replace('-','_'));if(!value.isEmpty())metadata.put(header,value);}metadata.put("managed_source_id",id);metadata.put("managed_transport",first(row,"type"));metadata.put("managed_tab",linear24?"24_7":"LIVE");catalog.items.add(new StreamItem(id,name,categoryId,first(row,"logo"),url,first(row,"tvgId"),CatalogType.LIVE,Collections.emptyList(),metadata));}if(catalog.isEmpty())throw new CatalogException("Serveri nuk ka kanale aktive");return catalog;}catch(JSONException invalid){throw new CatalogException("Përgjigjja e API LazyTV nuk është e vlefshme");}}
 private static String legacyApiName(String server){if(server==null)return "";String value=server.trim();if(value.equalsIgnoreCase("KRYSTAL"))return "Krystal";if(value.equalsIgnoreCase("LAZYIPTV")||value.equalsIgnoreCase("LAZIIPTV")||value.equalsIgnoreCase("LAZI IPTV"))return "LAZI IPTV";if(value.equalsIgnoreCase("MASTER"))return "MASTER";if(value.equalsIgnoreCase("SARAY DARK")||value.equalsIgnoreCase("SARAYDARK"))return "SARAY DARK";return value;}
 private static String first(JSONObject row,String... keys){for(String key:keys){String value=row.optString(key).trim();if(!value.isEmpty()&&!"null".equals(value))return value;}return "";}
}
