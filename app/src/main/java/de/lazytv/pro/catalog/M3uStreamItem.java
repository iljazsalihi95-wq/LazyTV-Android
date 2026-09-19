package de.lazytv.pro.catalog;
import java.util.*;
public final class M3uStreamItem extends StreamItem{
 public final Map<String,String> requestHeaders;
 public M3uStreamItem(String id,String name,String categoryId,String logo,String streamUrl,String tvgId,CatalogType type,Map<String,String> h){super(id,name,categoryId,logo,streamUrl,tvgId,type);requestHeaders=h==null?Collections.emptyMap():Collections.unmodifiableMap(new LinkedHashMap<>(h));}
}