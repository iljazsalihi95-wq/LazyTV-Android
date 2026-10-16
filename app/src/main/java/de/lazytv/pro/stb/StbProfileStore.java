package de.lazytv.pro.stb;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Private STB namespace; never reads or writes LazyTV playlist/activation/favorite storage. */
final class StbProfileStore {
    static final class Profile {
        final String id,name,portal,mac;
        Profile(String id,String name,String portal,String mac){this.id=id;this.name=name;this.portal=portal;this.mac=mac;}
    }
    private final SharedPreferences prefs;
    StbProfileStore(Context context){prefs=context.getSharedPreferences("lazy_stb_profiles",Context.MODE_PRIVATE);}
    List<Profile> all(){
        List<Profile> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(prefs.getString("profiles_v2","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);out.add(new Profile(o.getString("id"),o.getString("name"),o.getString("portal"),o.getString("mac")));}}catch(JSONException ignored){}
        // Preserve the previous STB shell's single saved profile, without touching ordinary playlists.
        if(out.isEmpty()&&!prefs.contains("profiles_v2")){String portal=prefs.getString("portal_url","");String mac=prefs.getString("mac_address","");if(!portal.isEmpty()&&!mac.isEmpty())out.add(new Profile(UUID.randomUUID().toString(),prefs.getString("profile_name","LAZY STB"),portal,mac));if(!out.isEmpty())saveAll(out);}
        return out;
    }
    Profile get(String id){for(Profile p:all())if(p.id.equals(id))return p;return null;}
    void put(Profile p){List<Profile> values=all();values.removeIf(v->v.id.equals(p.id));values.add(p);saveAll(values);}
    void delete(Profile p){List<Profile> values=all();values.removeIf(v->v.id.equals(p.id));saveAll(values);}
    private void saveAll(List<Profile> values){JSONArray a=new JSONArray();try{for(Profile p:values)a.put(new JSONObject().put("id",p.id).put("name",p.name).put("portal",p.portal).put("mac",p.mac));prefs.edit().putString("profiles_v2",a.toString()).apply();}catch(JSONException e){throw new IllegalStateException("Profili STB nuk u ruajt");}}
}
