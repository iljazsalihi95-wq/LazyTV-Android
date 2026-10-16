package de.lazytv.pro.stb;
import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
final class StbFavorites {
 private final SharedPreferences prefs;private final String key;
 StbFavorites(Context c,StbProfileStore.Profile p){prefs=c.getSharedPreferences("lazy_stb_favorites_v1",Context.MODE_PRIVATE);key=identity(p.id,p.portal);}
 static String identity(String profile,String portal){try{String canonical=StbProtocol.endpoints(portal).get(0);byte[] bytes=MessageDigest.getInstance("SHA-256").digest((profile+"\n"+canonical).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalArgumentException("STB favorite identity jo valide");}}
 boolean contains(String id){return prefs.getStringSet(key,Collections.emptySet()).contains(id);}
 boolean toggle(String id){Set<String> values=new HashSet<>(prefs.getStringSet(key,Collections.emptySet()));boolean added=values.add(id);if(!added)values.remove(id);prefs.edit().putStringSet(key,values).apply();return added;}
}
