package de.lazytv.pro.catalog;

import java.util.*;

/** Dynamic SOURCES registry and typed LazyMapper tabs. No provider secrets are configured here. */
final class ManagedSheetCatalogSource {
    static final String REGISTRY = "1COgz8NtU9QzKg_4BgNvykKqeHepk3oIGO-DsWXQhRsQ";
    static final class Server {
        final String id, name, sheet;
        Server(String id,String name,String sheet){this.id=id;this.name=name;this.sheet=sheet;}
    }
    Map<String,Server> servers() throws CatalogException {
        Map<String,Server> result=new LinkedHashMap<>();
        for(Map<String,String> row:rows(fetch(REGISTRY,"SOURCES"))){
            String id=get(row,"ID"),sheet=get(row,"SPREADSHEET_ID");
            if(!id.matches("SERVER_[0-9]+")||!sheet.matches("[A-Za-z0-9_-]+")||off(get(row,"AKTIV")))continue;
            String name=get(row,"EMRI");if(name.isEmpty())name=id;
            result.put(name,new Server(id,name,sheet));
        }
        return result;
    }
    static String fetch(String sheet,String tab)throws CatalogException {
        if("Sheet1".equalsIgnoreCase(tab))throw new CatalogException("Sheet1 nuk është katalog LazyMapper");
        return HttpClient.get("https://docs.google.com/spreadsheets/d/"+sheet+"/gviz/tq?tqx=out:csv&headers=1&sheet="+HttpClient.enc(tab)+"&_ts="+System.currentTimeMillis(),null);
    }
    Catalog load(Server server)throws CatalogException {
        // LIVE and 24_7 are both linear video, with distinct category namespaces.
        Map<String,String> identity=new LinkedHashMap<>();for(Map<String,String> row:rows(fetch(server.sheet,"INFO")))identity.put(get(row,"KEY").toUpperCase(Locale.ROOT),get(row,"VALUE"));
        Catalog out=parse(server,"LIVE",fetch(server.sheet,"LIVE"));
        Catalog linear=parse(server,"24_7",fetch(server.sheet,"24_7"));
        merge(out,linear);for(String tab:new String[]{"VOD","SERIES"})merge(out,parse(server,tab,fetch(server.sheet,tab)));
        // These tabs are part of the server contract, not a fallback to Sheet1.
        rows(fetch(server.sheet,"CATEGORIES"));rows(fetch(server.sheet,"STATUS"));
        Catalog enriched=new Catalog();enriched.categories.addAll(out.categories);for(StreamItem item:out.items){Map<String,String> metadata=new LinkedHashMap<>(item.metadata);String portal=get(identity,"PORTAL_URL","PORTAL"),mac=get(identity,"MAC");if(!portal.isEmpty()&&!metadata.containsKey("portal_url"))metadata.put("portal_url",portal);if(!mac.isEmpty()&&!metadata.containsKey("mac"))metadata.put("mac",mac);enriched.items.add(new StreamItem(item.id,item.name,item.categoryId,item.logo,item.streamUrl,item.tvgId,item.type,item.fallbackUrls,metadata));}Set<String> used=new HashSet<>();for(StreamItem item:enriched.items)used.add(item.categoryId);enriched.categories.removeIf(c->!used.contains(c.id));return enriched;
    }
    static void merge(Catalog into,Catalog more){
        Set<String> categories=new HashSet<>();for(Category c:into.categories)categories.add(c.id);
        for(Category c:more.categories)if(categories.add(c.id))into.categories.add(c);
        LinkedHashMap<String,StreamItem> items=new LinkedHashMap<>();for(StreamItem i:into.items)items.put(i.id,i);for(StreamItem i:more.items)items.put(i.id,i);
        into.items.clear();into.items.addAll(items.values());
    }
    static Catalog parse(Server server,String tab,String csv)throws CatalogException {
        if(!Arrays.asList("LIVE","24_7","VOD","SERIES").contains(tab))throw new CatalogException("Tab katalogu i pavlefshëm");
        CatalogType type="VOD".equals(tab)?CatalogType.MOVIES:"SERIES".equals(tab)?CatalogType.SERIES:CatalogType.LIVE;
        Catalog out=new Catalog();Map<String,Category> cats=new LinkedHashMap<>();Map<String,StreamItem> items=new LinkedHashMap<>();
        for(Map<String,String> row:rows(csv)){
            String rowServer=get(row,"SERVER_ID");if(!rowServer.isEmpty()&&!server.id.equals(rowServer)||off(get(row,"STATUS")))continue;
            String name=get(row,"NAME"),cmd=get(row,"STREAM_URL_CMD");if(name.isEmpty()||"STREAM_URL_CMD".equalsIgnoreCase(cmd)||cmd.isEmpty()&&!"SERIES".equals(tab))continue;
            String source=get(row,"SOURCE_ID");if(source.isEmpty())source=get(row,"ID");
            if(source.isEmpty())throw new CatalogException("Katalogu nuk ka identitet të qëndrueshëm kanali");
            String category=get(row,"CATEGORY");if(category.isEmpty())category="Të tjera";
            String country=ManagedCountryResolver.resolve(get(row,"COUNTRY_CODE"),get(row,"COUNTRY"),category,name);
            CatalogType rowType=linearCommand(cmd)?CatalogType.LIVE:type;String rowTab=rowType==CatalogType.LIVE&&!"24_7".equals(tab)?"LIVE":tab;
            String categoryId=server.id+"|"+rowTab+"|"+country+"|"+category;
            if(!cats.containsKey(categoryId)){Category value=new Category(categoryId,("24_7".equals(tab)?"24/7 • ":"")+category,rowType,country);cats.put(categoryId,value);out.categories.add(value);}
            Map<String,String> metadata=new LinkedHashMap<>();metadata.put("managed_server_id",server.id);metadata.put("managed_server",server.name);metadata.put("managed_source_id",source);metadata.put("managed_tab",rowTab);metadata.put("managed_import_tab",tab);
            put(metadata,"portal_url",get(row,"PORTAL_URL","PORTAL"));put(metadata,"mac",get(row,"MAC"));put(metadata,"stalker_original_cmd",get(row,"ORIGINAL_CMD","CMD"));
            if(cmd.startsWith("ffmpeg ")||cmd.contains("localhost/ch/"))metadata.put("stalker_original_cmd",cmd);
            for(String field:new String[]{"PLOT","DESCRIPTION","YEAR","RATING","DURATION","POSTER","BACKDROP","GENRE","DIRECTOR","CAST"})put(metadata,field.toLowerCase(Locale.ROOT),get(row,field));
            StreamItem item=new StreamItem(server.id+"|"+source,name,categoryId,get(row,"LOGO"),cmd,get(row,"TVG_ID","EPG_ID"),rowType,Collections.emptyList(),metadata);
            if(rowType==CatalogType.SERIES){
                String parent=get(row,"SERIES_ID"),season=get(row,"SEASON","SEASON_NUMBER"),episode=get(row,"EPISODE","EPISODE_NUMBER");
                if(!cmd.isEmpty()&&!parent.isEmpty()&&!episode.isEmpty()){
                    item=new Episode(item.id,get(row,"EPISODE_TITLE").isEmpty()?name:get(row,"EPISODE_TITLE"),server.id+"|series|"+parent,categoryId,get(row,"POSTER","LOGO"),cmd,get(row,"EXTENSION"),get(row,"DURATION"),number(season,1),number(episode,1),metadata);
                    String parentKey=server.id+"|series|"+parent;
                    if(!items.containsKey(parentKey))items.put(parentKey,new Series(parentKey,name,categoryId,get(row,"POSTER","LOGO"),"","",metadata));
                }else if(cmd.isEmpty())item=new Series(server.id+"|series|"+(parent.isEmpty()?source:parent),name,categoryId,get(row,"POSTER","LOGO"),"","",metadata);
                else continue; // A generic series object or unstructured stream is never an episode.
            }
            items.put(item.id,item); // upsert by SERVER_ID + SOURCE_ID, independent of token/country/row order
        }
        out.items.addAll(items.values());return out;
    }
    private static int number(String raw,int fallback){try{return Math.max(1,Integer.parseInt(raw));}catch(Exception ignored){return fallback;}}
    static boolean linearCommand(String command){
        String cmd=command.toLowerCase(Locale.ROOT);
        if(cmd.contains("localhost/ch/"))return true;
        if(cmd.contains("/movie/")||cmd.contains("/vod/")||cmd.contains("type=vod")||cmd.contains("type=movie")||cmd.contains("movie.php"))return false;
        return ServerTenPlaybackResolver.reference(command)!=null;
    }
    private static void put(Map<String,String> m,String k,String v){if(!v.isEmpty())m.put(k,v);}
    static String get(Map<String,String> row,String... keys){for(String key:keys){String v=row.get(key);if(v!=null&&!v.trim().isEmpty())return v.trim();}return "";}
    static boolean off(String value){return Arrays.asList("FALSE","DISABLED","OFF","INACTIVE","BLOCKED").contains(value.toUpperCase(Locale.ROOT));}
    static List<Map<String,String>> rows(String body)throws CatalogException {
        if(body==null||body.trim().startsWith("<"))throw new CatalogException("Google Sheets nuk ktheu CSV të vlefshëm");
        List<List<String>> values=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;
        for(int n=0;n<body.length();n++){char c=body.charAt(n);if(c=='"'){if(quoted&&n+1<body.length()&&body.charAt(n+1)=='"'){field.append('"');n++;}else quoted=!quoted;}else if(c==','&&!quoted){row.add(field.toString());field.setLength(0);}else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&n+1<body.length()&&body.charAt(n+1)=='\n')n++;row.add(field.toString());field.setLength(0);values.add(row);row=new ArrayList<>();}else field.append(c);}
        if(quoted)throw new CatalogException("CSV i papërfunduar");if(field.length()>0||!row.isEmpty()){row.add(field.toString());values.add(row);}List<Map<String,String>> result=new ArrayList<>();if(values.isEmpty())return result;
        List<String> header=values.get(0);for(int n=1;n<values.size();n++){Map<String,String> mapped=new LinkedHashMap<>();List<String> cells=values.get(n);for(int c=0;c<header.size();c++)mapped.put(header.get(c).replace("\uFEFF","").trim().toUpperCase(Locale.ROOT),c<cells.size()?cells.get(c):"");result.add(mapped);}return result;
    }
}
