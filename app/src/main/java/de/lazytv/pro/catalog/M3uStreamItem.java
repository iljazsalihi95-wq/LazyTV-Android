package de.lazytv.pro.catalog;
import java.util.*;
public final class M3uStreamItem extends StreamItem implements M3uHeaders {
 public final Map<String,String> requestHeaders;
 public M3uStreamItem(String id,String name,String categoryId,String logo,String streamUrl,String tvgId,CatalogType type,Map<String,String> headers){this(id,name,categoryId,logo,streamUrl,tvgId,type,headers,Collections.emptyMap());}
 public M3uStreamItem(String id,String name,String categoryId,String logo,String streamUrl,String tvgId,CatalogType type,Map<String,String> headers,Map<String,String> metadata){super(id,name,categoryId,logo,streamUrl,tvgId,type,Collections.emptyList(),metadata);requestHeaders=headers==null?Collections.emptyMap():Collections.unmodifiableMap(new LinkedHashMap<>(headers));}
 public Map<String,String> requestHeaders(){return requestHeaders;}
}
