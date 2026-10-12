package de.lazytv.pro.catalog;

import de.lazytv.pro.activation.ActivationGuard;
import android.app.*;
import android.os.*;
import android.view.KeyEvent;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import de.lazytv.pro.R;
import de.lazytv.pro.playlist.*;
import de.lazytv.pro.player.PlayerLauncher;

public class SeriesDetailActivity extends Activity {
    public static final String PLAYLIST="playlist_id",SERIES_ID="series_id",SERIES_NAME="series_name",SERIES_LOGO="series_logo",SERIES_CAT="series_cat";
    private final ExecutorService pool=Executors.newSingleThreadExecutor();
    private final CatalogEngine engine=new CatalogEngine();
    private final AtomicInteger generation=new AtomicInteger();
    private Playlist p;
    private Series series;
    private SeriesDetails details;
    private ArrayAdapter<String> seasonsAdapter,episodesAdapter;
    private ListView seasonsList,episodesList;
    private TextView status,title;
    private Season selectedSeason;
    private List<Episode> visibleEpisodes=Collections.emptyList();

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        if(!ActivationGuard.enforce(this))return;
        setContentView(R.layout.activity_series_detail);
        p=new PlaylistStorage(this).get(getIntent().getStringExtra(PLAYLIST));
        series=(Series)getIntent().getSerializableExtra("series_item");
        if(series==null)series=new Series(getIntent().getStringExtra(SERIES_ID),getIntent().getStringExtra(SERIES_NAME),getIntent().getStringExtra(SERIES_CAT),getIntent().getStringExtra(SERIES_LOGO),"","");
        if(p==null||series.id==null){finish();return;}
        title=findViewById(R.id.series_title);
        status=findViewById(R.id.series_status);
        seasonsList=findViewById(R.id.series_seasons);
        episodesList=findViewById(R.id.series_episodes);
        seasonsAdapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_activated_1,new ArrayList<>());
        episodesAdapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_activated_1,new ArrayList<>());
        seasonsList.setAdapter(seasonsAdapter);
        episodesList.setAdapter(episodesAdapter);
        title.setText(series.name);
        seasonsList.setOnItemClickListener((a,v,pos,id)->selectSeason(pos,true));
        episodesList.setOnItemClickListener((a,v,pos,id)->openEpisode(pos));
        seasonsList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> a,android.view.View v,int pos,long id){selectSeason(pos,false);}
            public void onNothingSelected(AdapterView<?> a){}
        });
        episodesList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> a,android.view.View v,int pos,long id){showEpisodeInfo(pos);}
            public void onNothingSelected(AdapterView<?> a){}
        });
        load();
    }

    private void load(){
        final int token=generation.incrementAndGet();
        status.setText("Duke ngarkuar sezonet…");
        pool.execute(()->{
            try{
                SeriesDetails d=engine.loadSeries(p,series);
                runOnUiThread(()->{if(!isCurrent(token))return;details=d;showSeries();});
            }catch(Exception e){runOnUiThread(()->{if(isCurrent(token))status.setText(msg(e,"Seriali nuk mund të ngarkohet"));});}
        });
    }

    private void showSeries(){
        Map<String,String> metadata=new LinkedHashMap<>(series.metadata);metadata.putAll(details.metadata);
        StringBuilder summary=new StringBuilder();
        for(String key:new String[]{"year","rating","genre","country","director","cast","plot"}){
            String value=clean(metadata.get(key));if(value.isEmpty()||"null".equalsIgnoreCase(value))continue;
            if(summary.length()>0)summary.append("\n");summary.append(key.equals("plot")?value:key+": "+value);
        }
        TextView info=findViewById(R.id.series_details);info.setText(summary);info.setVisibility(summary.length()==0?android.view.View.GONE:android.view.View.VISIBLE);
        info.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(series.name).setMessage(summary.toString()).setPositiveButton("OK",null).show());

        seasonsAdapter.clear();
        for(Season s:details.seasons)seasonsAdapter.add(s.title);
        if(details.seasons.isEmpty()){
            visibleEpisodes=Collections.emptyList();
            episodesAdapter.clear();
            status.setText("Seriali nuk ka sezone");
            return;
        }
        selectSeason(0,false);
        seasonsList.setItemChecked(0,true);
        seasonsList.setSelection(0);
        seasonsList.requestFocus();
    }

    private void selectSeason(int pos,boolean moveToEpisodes){
        if(details==null||pos<0||pos>=details.seasons.size())return;
        selectedSeason=details.seasons.get(pos);
        List<Episode> es=details.episodes.get(selectedSeason.number);
        visibleEpisodes=es==null?Collections.emptyList():es;
        episodesAdapter.clear();
        for(Episode e:visibleEpisodes){
            StringBuilder label=new StringBuilder();
            if(e.episodeNumber>0)label.append(e.episodeNumber).append(". ");
            label.append(e.name);
            String duration=clean(e.duration);
            if(!duration.isEmpty())label.append("  •  ").append(duration);
            episodesAdapter.add(label.toString());
        }
        seasonsList.setItemChecked(pos,true);
        status.setText(selectedSeason.title+" • "+visibleEpisodes.size()+" episode");
        if(!visibleEpisodes.isEmpty()){
            episodesList.setSelection(0);
            if(moveToEpisodes)episodesList.requestFocus();
        }
    }

    private void showEpisodeInfo(int pos){
        if(pos<0||pos>=visibleEpisodes.size())return;
        Episode e=visibleEpisodes.get(pos);
        StringBuilder info=new StringBuilder(selectedSeason==null?"":selectedSeason.title);
        if(e.episodeNumber>0)append(info,"Ep. "+e.episodeNumber);
        append(info,clean(e.duration));
        String rating=clean(e.metadata.get("rating"));
        if(!rating.isEmpty())append(info,"★ "+rating);
        String date=clean(e.metadata.get("releasedate"));
        if(!date.isEmpty())append(info,date);
        String plot=clean(e.metadata.get("plot"));
        if(plot.isEmpty())plot=clean(e.metadata.get("description"));
        if(!plot.isEmpty())info.append("\n").append(plot);
        status.setText(info.toString());
    }

    private void openEpisode(int pos){
        if(pos<0||pos>=visibleEpisodes.size())return;
        final Episode ep=visibleEpisodes.get(pos);
        final int token=generation.incrementAndGet();
        status.setText("Duke përgatitur stream-in…");
        pool.execute(()->{
            try{
                ResolvedStream rs=engine.resolveStream(p,ep);
                runOnUiThread(()->{
                    if(!isCurrent(token))return;
                    status.setText("Stream gati");
                    PlayerLauncher.open(this,p.getId(),ep,rs,series.name);
                });
            }catch(Exception e){runOnUiThread(()->{if(isCurrent(token))status.setText(msg(e,"Stream-i nuk mund të zgjidhet"));});}
        });
    }

    private boolean isCurrent(int token){return token==generation.get()&&!isFinishing()&&!isDestroyed();}

    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_DOWN){
            int k=e.getKeyCode();
            if(k==KeyEvent.KEYCODE_DPAD_RIGHT&&seasonsList.hasFocus()&&!visibleEpisodes.isEmpty()){
                episodesList.requestFocus();return true;
            }
            if(k==KeyEvent.KEYCODE_DPAD_LEFT&&episodesList.hasFocus()){
                seasonsList.requestFocus();return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void onBackPressed(){
        if(episodesList.hasFocus()){
            seasonsList.requestFocus();return;
        }
        super.onBackPressed();
    }

    private void append(StringBuilder out,String value){value=clean(value);if(value.isEmpty())return;if(out.length()>0)out.append(" • ");out.append(value);}
    private String clean(String value){return value==null?"":value.trim();}
    private String msg(Exception e,String d){return e.getMessage()==null?d:e.getMessage();}
    @Override protected void onDestroy(){generation.incrementAndGet();pool.shutdownNow();engine.close();super.onDestroy();}
}
