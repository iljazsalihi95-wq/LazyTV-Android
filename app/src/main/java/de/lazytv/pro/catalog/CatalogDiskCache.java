package de.lazytv.pro.catalog;
import android.content.Context;
import java.io.*;
public final class CatalogDiskCache {
 private static final long TTL_MS=30L*60L*1000L;
 private CatalogDiskCache(){}
 private static File file(Context c,String key){return new File(c.getCacheDir(),"catalog_"+Integer.toHexString(key.hashCode())+".bin");}
 public static Catalog get(Context c,String key){
  File f=file(c,key);if(!f.isFile()||System.currentTimeMillis()-f.lastModified()>TTL_MS)return null;
  try(ObjectInputStream in=new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))){Object x=in.readObject();return x instanceof Catalog?(Catalog)x:null;}catch(Exception e){f.delete();return null;}
 }
 public static void put(Context c,String key,Catalog catalog){
  if(catalog==null)return;File f=file(c,key),tmp=new File(f.getPath()+".tmp");
  try(ObjectOutputStream out=new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))){out.writeObject(catalog);out.flush();if(f.exists())f.delete();tmp.renameTo(f);}catch(Exception e){tmp.delete();}
 }
 public static String key(String playlistId,CatalogType type){return (playlistId==null?"":playlistId)+"|"+type.name();}
}