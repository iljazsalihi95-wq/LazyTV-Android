package de.lazytv.pro.catalog;
public class StreamItem implements java.io.Serializable {
 public final String id,name,categoryId,logo,streamUrl,tvgId;
 public final java.util.List<String> fallbackUrls;
 public final CatalogType type;
 public final java.util.Map<String,String> metadata;
 public StreamItem(String id,String name,String categoryId,String logo,String streamUrl,String tvgId,CatalogType type){this(id,name,categoryId,logo,streamUrl,tvgId,type,java.util.Collections.emptyList(),java.util.Collections.emptyMap());}
 public StreamItem(String id,String name,String categoryId,String logo,String streamUrl,String tvgId,CatalogType type,java.util.List<String> fallbackUrls){this(id,name,categoryId,logo,streamUrl,tvgId,type,fallbackUrls,java.util.Collections.emptyMap());}
 public StreamItem(String id,String name,String categoryId,String logo,String streamUrl,String tvgId,CatalogType type,java.util.List<String> fallbackUrls,java.util.Map<String,String> metadata){
  this.id=id;this.name=name;this.categoryId=categoryId;this.logo=logo;this.streamUrl=streamUrl;this.tvgId=tvgId;this.type=type;
  this.fallbackUrls=fallbackUrls==null?java.util.Collections.emptyList():java.util.Collections.unmodifiableList(new java.util.ArrayList<>(fallbackUrls));
  this.metadata=metadata==null?java.util.Collections.emptyMap():java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata));
 }
 public String meta(String key){String v=metadata.get(key);return v==null?"":v;}
}