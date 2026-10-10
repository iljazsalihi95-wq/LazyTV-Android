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
        series=new Series(getIntent().getStringExtra(SERIES_ID),getIntent().getStringExtra(SERIES_NAME),getIntent().getStringExtra(SERIES_CAT),getIntent().getStringExtra(SERIES_LOGO),"","");
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
        for(Episode e:visibleEpisodes)episodesAdapter.add((e.episodeNumber>0?e.episodeNumber+". ":"")+e.name);
        seasonsList.setItemChecked(pos,true);
        status.setText(selectedSeason.title+" • "+visibleEpisodes.size()+" episode");
        if(!visibleEpisodes.isEmpty()){
            episodesList.setSelection(0);
            if(moveToEpisodes)episodesList.requestFocus();
        }
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
            }catch(Exception e){runOnUiThread(()->{if(isCurrent(token))status.setText("Stream-i nuk mund të zgjidhet");});}
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

    private String msg(Exception e,String d){return e.getMessage()==null?d:e.getMessage();}
    @Override protected void onDestroy(){generation.incrementAndGet();pool.shutdownNow();engine.close();super.onDestroy();}
}
