package de.lazytv.pro.catalog;
import android.content.*;import org.json.*;import java.util.*;
public final class FavoritesStore {
 private static final String PREF="lazytv_favorites",KEY="items";private final SharedPreferences p;
 public FavoritesStore(Context c){p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
 private String key(String playlist,StreamItem i){return playlist+"|"+i.type.name()+"|"+i.id;}
 public boolean contains(String playlist,StreamItem i){return all().contains(key(playlist,i));}
 public boolean toggle(String playlist,StreamItem i){Set<String>s=all();String k=key(playlist,i);boolean added;if(s.contains(k)){s.remove(k);added=false;}else{s.add(k);added=true;}p.edit().putStringSet(KEY,new HashSet<>(s)).apply();return added;}
 public List<StreamItem> filter(String playlist,List<StreamItem> items){Set<String>s=all();List<StreamItem>r=new ArrayList<>();for(StreamItem i:items)if(s.contains(key(playlist,i)))r.add(i);return r;}
 private Set<String> all(){return new HashSet<>(p.getStringSet(KEY,Collections.emptySet()));}
}