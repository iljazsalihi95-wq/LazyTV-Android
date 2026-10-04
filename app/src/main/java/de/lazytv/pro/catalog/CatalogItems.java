package de.lazytv.pro.catalog;
public final class CatalogItems {
 private CatalogItems(){}
 public static StreamItem create(String id,String name,String category,String logo,String url,String tvg,CatalogType type){
  return create(id,name,category,logo,url,tvg,type,java.util.Collections.emptyMap());
 }
 public static StreamItem create(String id,String name,String category,String logo,String url,String tvg,CatalogType type,java.util.Map<String,String> metadata){
  switch(type){
   case LIVE:return new Channel(id,name,category,logo,url,tvg);
   case MOVIES:return new Movie(id,name,category,logo,url,tvg,metadata);
   case SERIES:return new Series(id,name,category,logo,url,tvg,metadata);
   default:return new StreamItem(id,name,category,logo,url,tvg,type,java.util.Collections.emptyList(),metadata);
  }
 }
}