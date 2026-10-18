package de.lazytv.pro.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Validates timed subtitle cues returned by the owner AI backend before playback uses them. */
public final class AiSubtitleDocument {
    public static final int MAX_CUES = 5000;
    public static final long MAX_CUE_MS = 60_000L;

    public static final class Cue {
        public final long startMs;
        public final long endMs;
        public final String text;
        Cue(long startMs,long endMs,String text){this.startMs=startMs;this.endMs=endMs;this.text=text;}
    }

    public final String language;
    public final List<Cue> cues;

    private AiSubtitleDocument(String language,List<Cue> cues){
        this.language=language;
        this.cues=Collections.unmodifiableList(cues);
    }

    public static AiSubtitleDocument parse(String json) throws Exception {
        if(json==null||json.trim().isEmpty())throw new IllegalArgumentException("Subtitle document missing");
        JSONObject root=new JSONObject(json);
        JSONObject payload=root.optJSONObject("result");
        if(payload==null)payload=root;
        String language=payload.optString("language",payload.optString("target_language","sq")).trim().toLowerCase(java.util.Locale.ROOT);
        JSONArray rows=payload.optJSONArray("cues");
        if(rows==null)rows=payload.optJSONArray("subtitles");
        if(rows==null)throw new IllegalArgumentException("Subtitle cues missing");
        if(rows.length()>MAX_CUES)throw new IllegalArgumentException("Too many subtitle cues");
        List<Cue> cues=new ArrayList<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;
            long start=time(row,"start_ms","start");
            long end=time(row,"end_ms","end");
            String text=row.optString("text",row.optString("translation","")).trim();
            if(text.isEmpty()||start<0||end<=start)continue;
            if(end-start>MAX_CUE_MS)end=start+MAX_CUE_MS;
            cues.add(new Cue(start,end,text));
        }
        if(cues.isEmpty())throw new IllegalArgumentException("No valid subtitle cues");
        Collections.sort(cues,Comparator.comparingLong(c->c.startMs));
        return new AiSubtitleDocument(language,cues);
    }

    public Cue cueAt(long positionMs){
        int low=0,high=cues.size()-1;
        while(low<=high){int mid=(low+high)>>>1;Cue cue=cues.get(mid);if(positionMs<cue.startMs)high=mid-1;else if(positionMs>=cue.endMs)low=mid+1;else return cue;}
        return null;
    }

    private static long time(JSONObject row,String millisKey,String genericKey){
        if(row.has(millisKey))return Math.max(-1,row.optLong(millisKey,-1));
        Object value=row.opt(genericKey);if(value==null)return -1;
        if(value instanceof Number){double number=((Number)value).doubleValue();return number>100000?Math.round(number):Math.round(number*1000d);}
        String raw=String.valueOf(value).trim();
        try{if(raw.indexOf(':')<0){double number=Double.parseDouble(raw);return number>100000?Math.round(number):Math.round(number*1000d);}String[] p=raw.replace(',','.').split(":");double seconds=0;for(String part:p)seconds=seconds*60d+Double.parseDouble(part);return Math.round(seconds*1000d);}catch(Exception ignored){return -1;}
    }
}
