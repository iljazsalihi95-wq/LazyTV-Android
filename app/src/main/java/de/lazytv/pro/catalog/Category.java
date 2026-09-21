package de.lazytv.pro.catalog;
public class Category {
 public final String id,name,country; public final CatalogType type;
 public Category(String id,String name,CatalogType type){this(id,name,type,"");}
 public Category(String id,String name,CatalogType type,String country){this.id=id;this.name=name;this.type=type;this.country=country==null?"":country.trim();}
}