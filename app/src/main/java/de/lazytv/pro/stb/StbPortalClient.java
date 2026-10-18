package de.lazytv.pro.stb;

import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** One private portal/MAC session per Lazy STB connection. Never shared with managed servers. */
public final class StbPortalClient implements AutoCloseable {
    private static final String UA="Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 MAG200 stbapp ver: 2 rev: 250 Safari/533.3";
    private final OkHttpClient http;
    private final List<String> endpoints;
    private final String mac;
    private final Map<String,List<Cookie>> cookies=new HashMap<>();
    private volatile String endpoint,token="";
    private long authenticatedAt;
    private JSONObject profile=new JSONObject(),account=new JSONObject();
    private volatile boolean closed;
    public StbPortalClient(String portal,String mac) {
        if(mac==null || !mac.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) throw new IllegalArgumentException("MAC Address nuk është valid");
        this.mac=mac.toUpperCase(Locale.ROOT);this.endpoints=StbProtocol.endpoints(portal);
        http=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).cookieJar(new CookieJar(){
                public synchronized void saveFromResponse(HttpUrl u,List<Cookie> values){
                    List<Cookie> existing=cookies.computeIfAbsent(u.host(),k->new ArrayList<>());
                    for(Cookie c:values){existing.removeIf(old->old.name().equals(c.name())&&old.path().equals(c.path()));existing.add(c);}
                }
                public synchronized List<Cookie> loadForRequest(HttpUrl u){List<Cookie> out=new ArrayList<>();for(Cookie c:cookies.getOrDefault(u.host(),Collections.emptyList())) if(c.expiresAt()>System.currentTimeMillis()&&c.matches(u)&&!c.name().equalsIgnoreCase("mac"))out.add(c);
                    out.add(new Cookie.Builder().name("stb_lang").value("en").hostOnlyDomain(u.host()).path("/").build());out.add(new Cookie.Builder().name("timezone").value("UTC").hostOnlyDomain(u.host()).path("/").build());
                    out.add(new Cookie.Builder().name("mac").value(mac.replace(":","%3A")).hostOnlyDomain(u.host()).path("/").build());return out;}
            }).build();
    }
    private static final class AuthExpired extends IOException {}
    static final class HttpFailure extends IOException {final int status;HttpFailure(int status){super("STB HTTP "+status);this.status=status;}}
    private String referer(){return endpoint.replaceFirst("/(?:server/load|portal|load)\\.php$","")+"/c/";}
    private Object request(String type,String action,Map<String,String> params,boolean authorized) throws IOException {
        if(closed)throw new IOException("Lidhja STB është mbyllur");
        HttpUrl.Builder url=Objects.requireNonNull(HttpUrl.parse(endpoint)).newBuilder().addQueryParameter("type",type).addQueryParameter("action",action).addQueryParameter("JsHttpRequest","1-xml");
        for(Map.Entry<String,String> p:params.entrySet())url.addQueryParameter(p.getKey(),p.getValue());
        Request.Builder req=new Request.Builder().url(url.build()).header("User-Agent",UA).header("X-User-Agent","Model: MAG250; Link: WiFi").header("Referer",referer());
        if(authorized)req.header("Authorization","Bearer "+token);
        try(Response response=http.newCall(req.build()).execute()) {
            if(response.code()==401||response.code()==403)throw new AuthExpired();
            if(!response.isSuccessful())throw new HttpFailure(response.code());
            String body="";
            if(response.body()!=null){ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=response.body().byteStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>32*1024*1024)throw new IOException("Përgjigjja e portalit është tepër e madhe");bytes.write(buffer,0,n);}}body=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}
            try {
                Object p=StbProtocol.payload(body);
                String error=p instanceof JSONObject?((JSONObject)p).optString("error",((JSONObject)p).optString("message")):p instanceof String?(String)p:"";
                if(error.toLowerCase(Locale.ROOT).matches(".*(invalid token|token expired|session expired|authorization failed|not authorized).*")||Boolean.FALSE.equals(p))throw new AuthExpired();
                return p;
            }catch(JSONException e){throw new IOException("Përgjigje STB jo valide");}
        }
    }
    private void authenticate() throws IOException {
        token="";authenticatedAt=0;
        IOException failure=null;
        List<String> candidates=new ArrayList<>();
        if(endpoint!=null&&!endpoint.isEmpty())candidates.add(endpoint);
        for(String candidate:endpoints)if(!candidates.contains(candidate))candidates.add(candidate);
        String previous=endpoint;
        for(String candidate:candidates){
            endpoint=candidate;
            try{
                String candidateToken=StbProtocol.token(request("stb","handshake",Collections.emptyMap(),false));
                if(candidateToken.isEmpty())throw new IOException("Handshake nuk ktheu token");
                token=candidateToken;
                Object p=request("stb","get_profile",Collections.singletonMap("stb_type","MAG250"),true);
                if(!(p instanceof JSONObject))throw new IOException("STB profile jo valid");
                profile=(JSONObject)p;
                try{Object a=request("account_info","get_main_info",Collections.emptyMap(),true);if(a instanceof JSONObject)account=(JSONObject)a;}catch(AuthExpired e){throw e;}catch(IOException ignored){/* Optional account metadata is not required for LIVE. */}
                authenticatedAt=System.currentTimeMillis();
                return;
            }catch(IOException e){failure=e;token="";authenticatedAt=0;}
        }
        endpoint=previous;
        throw failure==null?new IOException("STB handshake dështoi"):failure;
    }
    private Object api(String type,String action,Map<String,String> params) throws IOException {
        synchronized(this){if(token.isEmpty()||System.currentTimeMillis()-authenticatedAt>15*60*1000)authenticate();}
        try{return request(type,action,params,true);}catch(AuthExpired expired){synchronized(this){authenticate();}try{return request(type,action,params,true);}catch(AuthExpired second){throw new IOException("Sesioni STB u refuzua pas rifreskimit");}}
    }
    public synchronized void connect() throws IOException {if(token.isEmpty())authenticate();}
    public List<StbProtocol.Genre> categories(String type) throws IOException {checkCatalogType(type);return StbProtocol.genres(api(type,"get_categories",Collections.emptyMap()));}
    private static void checkCatalogType(String type){if(!"vod".equals(type)&&!"series".equals(type))throw new IllegalArgumentException("STB catalog type jo valid");}
    public StbProtocol.Page libraryPage(String type,String category,String movie,String season,String episode,int page) throws IOException {
        checkCatalogType(type);if(page<1||page>500)throw new IllegalArgumentException("STB page jashtë kufirit");
        Map<String,String> p=new LinkedHashMap<>();p.put("category",category);p.put("movie_id",movie);p.put("season_id",season);p.put("episode_id",episode);p.put("p",String.valueOf(page));p.put("sortby","added");
        StbProtocol.Kind fallback=type.equals("series")?StbProtocol.Kind.SERIES:StbProtocol.Kind.MOVIE;
        return new StbProtocol.Page(api(type,"get_ordered_list",p),type,fallback);
    }
    public List<StbProtocol.Genre> genres() throws IOException{return StbProtocol.genres(api("itv","get_genres",Collections.emptyMap()));}
    public List<StbProtocol.Channel> channels() throws IOException {
        Object all;try{all=api("itv","get_all_channels",Collections.emptyMap());}catch(HttpFailure e){if(e.status!=404&&e.status!=405)throw e;all=new JSONArray();}
        LinkedHashMap<String,StbProtocol.Channel> result=new LinkedHashMap<>();
        for(StbProtocol.Channel c:StbProtocol.channels(all)){if(c.kind==StbProtocol.Kind.LIVE)result.put(c.id,c);}
        if(!result.isEmpty())return new ArrayList<>(result.values());
        // Paginated fallback only when get_all_channels explicitly yields an empty catalog.
        int pages=1;
        for(int page=1;page<=pages;page++){
            Map<String,String> p=new LinkedHashMap<>();p.put("genre","*");p.put("p",String.valueOf(page));
            Object data=api("itv","get_ordered_list",p);List<StbProtocol.Channel> batch=StbProtocol.channels(data);
            int before=result.size();for(StbProtocol.Channel c:batch){if(c.kind==StbProtocol.Kind.LIVE)result.put(c.id,c);}
            if(data instanceof JSONObject){JSONObject o=(JSONObject)data;int size=o.optInt("max_page_items",batch.size());if(size>0)pages=Math.min(500,(o.optInt("total_items",batch.size())+size-1)/size);}
            if(batch.isEmpty()||result.size()==before)break;
        }
        return new ArrayList<>(result.values());
    }
    public String resolve(StbProtocol.Channel channel) throws IOException {
        if(!channel.playable())throw new IOException("Zgjidh skedarin/episodin përpara Play");
        Map<String,String> p=new LinkedHashMap<>();p.put("cmd",channel.cmd);p.put("series",channel.episodeNumber);p.put("forced_storage","0");p.put("disable_ad","0");p.put("download","0");
        return StbProtocol.link(api(channel.linear()?"itv":"vod","create_link",p),endpoint);
    }
    public Object epg(String channelId) throws IOException {
        IOException failure=null;
        for(String action:new String[]{"get_short_epg","get_epg_info"}){
            for(String key:new String[]{"ch_id","channel_id"}){
                Map<String,String> p=new LinkedHashMap<>();p.put(key,channelId);p.put("size","4");p.put("period","4");
                try{Object data=api("itv",action,p);if(StbProtocol.rows(data).length()>0)return data;}catch(IOException e){failure=e;}
            }
        }
        if(failure!=null)throw failure;
        return new JSONArray();
    }
    public synchronized Map<String,String> playbackHeaders(String streamUrl){
        Map<String,String> h=new HashMap<>();h.put("User-Agent",UA);
        HttpUrl stream=HttpUrl.parse(streamUrl),portal=HttpUrl.parse(endpoint);
        if(stream!=null&&portal!=null&&stream.host().equals(portal.host())&&stream.port()==portal.port()&&stream.scheme().equals(portal.scheme())){
            h.put("Referer",referer());h.put("Authorization","Bearer "+token);
            StringBuilder c=new StringBuilder();for(Cookie cookie:http.cookieJar().loadForRequest(stream)){if(c.length()>0)c.append("; ");c.append(cookie.name()).append('=').append(cookie.value());}h.put("Cookie",c.toString());
        }
        return h;
    }
    @Override public void close(){closed=true;http.dispatcher().cancelAll();http.connectionPool().evictAll();}
}
