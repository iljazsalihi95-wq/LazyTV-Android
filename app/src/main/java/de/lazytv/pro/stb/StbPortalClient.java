package de.lazytv.pro.stb;

import okhttp3.*;
import java.io.IOException;
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
    private String endpoint,token="";
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
                    out.add(new Cookie.Builder().name("mac").value(mac.replace(":","%3A")).hostOnlyDomain(u.host()).path("/").build());return out;}
            }).build();
    }
    private static final class AuthExpired extends IOException {}
    private Object request(String type,String action,Map<String,String> params,boolean authorized) throws IOException {
        if(closed)throw new IOException("Lidhja STB është mbyllur");
        HttpUrl.Builder url=Objects.requireNonNull(HttpUrl.parse(endpoint)).newBuilder().addQueryParameter("type",type).addQueryParameter("action",action).addQueryParameter("JsHttpRequest","1-xml");
        for(Map.Entry<String,String> p:params.entrySet())url.addQueryParameter(p.getKey(),p.getValue());
        Request.Builder req=new Request.Builder().url(url.build()).header("User-Agent",UA).header("X-User-Agent","Model: MAG250; Link: WiFi").header("Referer",HttpUrl.parse(endpoint).resolve("./c/").toString());
        if(authorized)req.header("Authorization","Bearer "+token);
        try(Response response=http.newCall(req.build()).execute()) {
            if(response.code()==401||response.code()==403)throw new AuthExpired();
            if(!response.isSuccessful())throw new IOException("Portali ktheu HTTP "+response.code());
            String body=response.body()==null?"":response.body().string();
            if(body.length()>32*1024*1024)throw new IOException("Përgjigjja e portalit është tepër e madhe");
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
        if(endpoint==null) {
            IOException failure=null;
            for(String candidate:endpoints){endpoint=candidate;try{token=StbProtocol.token(request("stb","handshake",Collections.emptyMap(),false));if(!token.isEmpty())break;failure=new IOException("Handshake nuk ktheu token");}catch(IOException e){failure=e;}token="";}
            if(token.isEmpty()){endpoint=null;throw failure==null?new IOException("STB handshake dështoi"):failure;}
        } else {token=StbProtocol.token(request("stb","handshake",Collections.emptyMap(),false));if(token.isEmpty())throw new IOException("Handshake nuk ktheu token");}
        Object p=request("stb","get_profile",Collections.singletonMap("stb_type","MAG250"),true);
        if(!(p instanceof JSONObject))throw new IOException("STB profile jo valid");
        profile=(JSONObject)p;
        try{Object a=request("account_info","get_main_info",Collections.emptyMap(),true);if(a instanceof JSONObject)account=(JSONObject)a;}catch(AuthExpired e){throw e;}catch(IOException ignored){/* Optional account metadata is not required for LIVE. */}
        authenticatedAt=System.currentTimeMillis();
    }
    private synchronized Object api(String type,String action,Map<String,String> params) throws IOException {
        if(token.isEmpty()||System.currentTimeMillis()-authenticatedAt>15*60*1000)authenticate();
        try{return request(type,action,params,true);}catch(AuthExpired expired){authenticate();try{return request(type,action,params,true);}catch(AuthExpired second){throw new IOException("Sesioni STB u refuzua pas rifreskimit");}}
    }
    public List<StbProtocol.Genre> genres() throws IOException{return StbProtocol.genres(api("itv","get_genres",Collections.emptyMap()));}
    public List<StbProtocol.Channel> channels() throws IOException {
        Object all=api("itv","get_all_channels",Collections.emptyMap());
        LinkedHashMap<String,StbProtocol.Channel> result=new LinkedHashMap<>();
        for(StbProtocol.Channel c:StbProtocol.channels(all))result.put(c.id,c);
        if(!result.isEmpty())return new ArrayList<>(result.values());
        // Paginated fallback only when get_all_channels explicitly yields an empty catalog.
        int pages=1;
        for(int page=1;page<=pages;page++){
            Map<String,String> p=new LinkedHashMap<>();p.put("genre","*");p.put("p",String.valueOf(page));
            Object data=api("itv","get_ordered_list",p);List<StbProtocol.Channel> batch=StbProtocol.channels(data);
            int before=result.size();for(StbProtocol.Channel c:batch)result.put(c.id,c);
            if(data instanceof JSONObject){JSONObject o=(JSONObject)data;int size=o.optInt("max_page_items",batch.size());if(size>0)pages=Math.min(500,(o.optInt("total_items",batch.size())+size-1)/size);}
            if(batch.isEmpty()||result.size()==before)break;
        }
        return new ArrayList<>(result.values());
    }
    public synchronized String resolve(StbProtocol.Channel channel) throws IOException {
        Map<String,String> p=new LinkedHashMap<>();p.put("cmd",channel.cmd);p.put("series","");p.put("forced_storage","0");p.put("disable_ad","0");p.put("download","0");
        return StbProtocol.link(api("itv","create_link",p),endpoint);
    }
    public Object epg(String channelId) throws IOException {Map<String,String> p=new LinkedHashMap<>();p.put("ch_id",channelId);p.put("size","2");return api("itv","get_short_epg",p);}
    public synchronized Map<String,String> playbackHeaders(String streamUrl){
        Map<String,String> h=new HashMap<>();h.put("User-Agent",UA);
        HttpUrl stream=HttpUrl.parse(streamUrl),portal=HttpUrl.parse(endpoint);
        if(stream!=null&&portal!=null&&stream.host().equals(portal.host())&&stream.port()==portal.port()&&stream.scheme().equals(portal.scheme())){
            h.put("Referer",portal.resolve("./c/").toString());h.put("Authorization","Bearer "+token);
            StringBuilder c=new StringBuilder();for(Cookie cookie:http.cookieJar().loadForRequest(stream)){if(c.length()>0)c.append("; ");c.append(cookie.name()).append('=').append(cookie.value());}h.put("Cookie",c.toString());
        }
        return h;
    }
    @Override public void close(){closed=true;http.dispatcher().cancelAll();http.connectionPool().evictAll();}
}
