package de.lazytv.pro.stb;

import android.content.Context;
import android.content.SharedPreferences;

/** Local video-club progress, isolated by STB profile, portal and provider item identity. */
final class StbVodProgress {
    private final SharedPreferences prefs;
    private final String profile;
    StbVodProgress(Context context,StbProfileStore.Profile value){
        prefs=context.getSharedPreferences("lazy_stb_vod_progress_v1",Context.MODE_PRIVATE);
        profile=StbFavorites.identity(value.id,value.portal);
    }
    static String key(String profile,StbProtocol.Channel item){
        return profile+":"+item.kind+":"+item.id.length()+":"+item.id+":"+item.episodeNumber;
    }
    static long resumable(long position,long duration){
        if(position<5000||duration<=0||position>=duration-10000)return 0;
        return position;
    }
    long get(StbProtocol.Channel item){return prefs.getLong(key(profile,item),0);}
    void save(StbProtocol.Channel item,long position,long duration){
        if(item==null||item.linear()||duration<=0)return;
        long value=resumable(position,duration);
        SharedPreferences.Editor edit=prefs.edit();
        if(value==0)edit.remove(key(profile,item));else edit.putLong(key(profile,item),value);
        edit.apply();
    }
    void clear(StbProtocol.Channel item){if(item!=null)prefs.edit().remove(key(profile,item)).apply();}
    static long clamp(long position,long duration){return duration>0?Math.max(0,Math.min(position,Math.max(0,duration-1))):0;}
    static String time(long millis){long seconds=Math.max(0,millis)/1000;return String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
