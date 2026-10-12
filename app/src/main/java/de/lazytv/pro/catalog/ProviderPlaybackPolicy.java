package de.lazytv.pro.catalog;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import java.util.*;

/** Limited transport recovery; never treats an account refusal as a codec error. */
@UnstableApi
public final class ProviderPlaybackPolicy {
    private ProviderPlaybackPolicy(){}
    public static int status(Throwable error){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        while(error!=null&&seen.add(error)){
            if(error instanceof ProviderHttpException)return ((ProviderHttpException)error).status;
            if(error instanceof HttpDataSource.InvalidResponseCodeException)return ((HttpDataSource.InvalidResponseCodeException)error).responseCode;
            error=error.getCause();
        }return 0;
    }
    public static String message(int code){return new ProviderHttpException(code).getMessage();}
    public static ResolvedStream retryHeaders(ResolvedStream stream,StreamItem item,int code,int attempt){
        if(stream==null||attempt!=1||(code!=403&&code!=409)||stream.headers.containsKey("X-User-Agent"))return stream;
        if(item instanceof M3uHeaders)for(String key:((M3uHeaders)item).requestHeaders().keySet())if(key.equalsIgnoreCase("User-Agent"))return stream;
        String ua=stream.headers.get("User-Agent");
        // Change only the app's default UA; preserve every provider-specific header.
        if(ua==null||!ua.contains("Android TV")||!ua.contains("Chrome/120.0"))return stream;
        Map<String,String> headers=new LinkedHashMap<>(stream.headers);
        headers.put("User-Agent",code==409?"IPTVSmartersPro":"Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        return new ResolvedStream(stream.url,stream.streamType,stream.title,stream.artwork,stream.epgId,headers,stream.metadata);
    }
}
