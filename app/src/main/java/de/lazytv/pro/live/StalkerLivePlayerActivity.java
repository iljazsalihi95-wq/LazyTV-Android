package de.lazytv.pro.live;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.lazytv.pro.R;
import de.lazytv.pro.activation.ActivationGuard;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.Playlist;
import de.lazytv.pro.playlist.PlaylistStorage;
import de.lazytv.pro.playlist.PlaylistType;

/**
 * Dedicated native STB/MAG entry point.
 *
 * Identity is intentionally Portal + MAC only.  LazyTV does not require a
 * user-entered serial number for this mode.  The MAG session, catalog and
 * playback surface live here and are not backed by LivePlaybackSession.
 */
@UnstableApi
public final class StalkerLivePlayerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final StalkerCatalogSource source = new StalkerCatalogSource();
    private Playlist playlist;
    private PlayerView playerView;
    private ExoPlayer player;
    private TextView status, title;
    private Spinner countriesView;
    private ListView categoriesView, channelsView;
    private View leftPanel, channelPanel, topBar, bottomBar, idle, loading;
    private final Map<String,List<StreamItem>> byCategory = new LinkedHashMap<>();
    private final Map<String,Integer> counts = new LinkedHashMap<>();
    private List<CountryGroup> countries = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private List<StreamItem> channels = new ArrayList<>();
    private StreamItem current;
    private long selectionGeneration;
    private boolean fullscreen;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!ActivationGuard.enforce(this)) return;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        String id = getIntent().getStringExtra("playlist_id");
        playlist = id == null ? null : new PlaylistStorage(this).get(id);
        if (playlist == null || playlist.getType() != PlaylistType.STALKER_PORTAL) {
            Toast.makeText(this,"STB kërkon Portal + MAC",Toast.LENGTH_LONG).show();
            finish(); return;
        }
        setContentView(R.layout.activity_live_tv);
        bind();
        createPlayer();
        loadPortal();
    }

    private void bind() {
        playerView=findViewById(R.id.live_player);
        status=findViewById(R.id.live_status); title=findViewById(R.id.live_title);
        countriesView=findViewById(R.id.live_country_spinner);
        categoriesView=findViewById(R.id.live_categories); channelsView=findViewById(R.id.live_channels);
        leftPanel=findViewById(R.id.live_left_panel); channelPanel=findViewById(R.id.live_channel_panel);
        topBar=findViewById(R.id.live_top_bar); bottomBar=findViewById(R.id.live_bottom_bar);
        idle=findViewById(R.id.live_idle); loading=findViewById(R.id.live_loading_logo);
        title.setText("STB / MAG");
        status.setVisibility(View.VISIBLE); status.setText("STB • Duke u lidhur me portalin…");
        findViewById(R.id.live_refresh).setOnClickListener(v->{ source.clear(); loadPortal(); });
        findViewById(R.id.live_prev).setOnClickListener(v->adjacent(-1));
        findViewById(R.id.live_next).setOnClickListener(v->adjacent(1));
        countriesView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){ selectCountry(pos); }
            public void onNothingSelected(AdapterView<?> p){}
        });
        categoriesView.setOnItemClickListener((p,v,pos,id)->selectCategory(pos));
        channelsView.setOnItemClickListener((p,v,pos,id)->selectChannel(pos));
        playerView.setUseController(false); playerView.setFocusable(true);
        playerView.setOnClickListener(v->setFullscreen(!fullscreen));
        findViewById(R.id.live_player_panel).setOnClickListener(v->setFullscreen(!fullscreen));
        // STB owns LIVE first. VOD/SERIES continue through their dedicated catalog/player flows.
        findViewById(R.id.live_nav_live).setOnClickListener(v->countriesView.requestFocus());
    }

    private void createPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_BUFFERING){ loading.setVisibility(View.VISIBLE); status.setText("STB • Loading…"); status.setVisibility(View.VISIBLE); }
                else if(state==Player.STATE_READY){ loading.setVisibility(View.GONE); status.setVisibility(View.GONE); }
            }
        });
    }

    private void loadPortal() {
        final long ticket=++selectionGeneration;
        loading.setVisibility(View.VISIBLE); status.setVisibility(View.VISIBLE);
        status.setText("STB • Handshake / Profile / LIVE…");
        worker.execute(()->{
            try {
                Catalog catalog=source.load(playlist);
                List<Category> live=catalog.categories(CatalogType.LIVE);
                Map<String,List<StreamItem>> items=new LinkedHashMap<>();
                Map<String,Integer> sizes=new LinkedHashMap<>();
                for(Category c:live){ List<StreamItem> x=catalog.items(CatalogType.LIVE,c.id); if(!x.isEmpty()){items.put(c.id,x);sizes.put(c.id,x.size());} }
                List<CountryGroup> grouped=StalkerCountryResolver.groups(live);
                runOnUiThread(()->{
                    if(ticket!=selectionGeneration||isFinishing())return;
                    byCategory.clear();byCategory.putAll(items);counts.clear();counts.putAll(sizes);countries=new ArrayList<>(grouped);
                    List<String> labels=new ArrayList<>();
                    for(CountryGroup g:countries){int n=0;for(Category c:g.categories)n+=counts.containsKey(c.id)?counts.get(c.id):0;labels.add(g.label+"  ("+n+")");}
                    countriesView.setAdapter(adapter(labels)); loading.setVisibility(View.GONE);
                    status.setText(countries.isEmpty()?"STB • Portali u lidh, por LIVE është bosh":"");
                    status.setVisibility(countries.isEmpty()?View.VISIBLE:View.GONE);
                    if(!countries.isEmpty()){countriesView.setSelection(0);selectCountry(0);countriesView.requestFocus();}
                });
            } catch(Exception e){
                runOnUiThread(()->{if(ticket!=selectionGeneration||isFinishing())return;loading.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);status.setText("STB • Lidhja me portalin dështoi");Toast.makeText(this,safe(e),Toast.LENGTH_LONG).show();});
            }
        });
    }

    private void selectCountry(int pos){
        if(pos<0||pos>=countries.size())return; categories=new ArrayList<>(countries.get(pos).categories);
        List<String> labels=new ArrayList<>();for(Category c:categories)labels.add(CountryResolver.categoryLabel(c.name)+"  ("+(counts.containsKey(c.id)?counts.get(c.id):0)+")");
        categoriesView.setAdapter(adapter(labels));channels=new ArrayList<>();channelsView.setAdapter(adapter(Collections.emptyList()));
        if(!categories.isEmpty()){categoriesView.setSelection(0);selectCategory(0);}
    }
    private void selectCategory(int pos){
        if(pos<0||pos>=categories.size())return;channels=new ArrayList<>(byCategory.containsKey(categories.get(pos).id)?byCategory.get(categories.get(pos).id):Collections.emptyList());
        List<String> labels=new ArrayList<>();for(StreamItem s:channels)labels.add(s.name);channelsView.setAdapter(adapter(labels));if(!channels.isEmpty())channelsView.setSelection(0);
    }
    private void selectChannel(int pos){if(pos<0||pos>=channels.size())return;play(channels.get(pos));}
    private void adjacent(int delta){if(channels.isEmpty())return;int i=current==null?-1:channels.indexOf(current);i=(i+delta+channels.size())%channels.size();channelsView.setSelection(i);play(channels.get(i));}

    private void play(StreamItem item){
        final long ticket=++selectionGeneration; loading.setVisibility(View.VISIBLE);status.setVisibility(View.VISIBLE);status.setText("STB • create_link…");
        worker.execute(()->{
            try{
                StalkerCatalogSource.LinkResult link=source.resolve(playlist,item);
                runOnUiThread(()->{if(ticket!=selectionGeneration||isFinishing())return;playResolved(item,link);});
            }catch(Exception e){runOnUiThread(()->{if(ticket!=selectionGeneration||isFinishing())return;loading.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);status.setText("STB • Kanali nuk u hap");Toast.makeText(this,safe(e),Toast.LENGTH_LONG).show();});}
        });
    }

    private void playResolved(StreamItem item,StalkerCatalogSource.LinkResult link){
        current=item;idle.setVisibility(View.GONE);title.setText(item.name==null?"STB / MAG":item.name);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(60000);
        if(link.headers!=null&&!link.headers.isEmpty())http.setDefaultRequestProperties(link.headers);
        DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
        player.setMediaSource(new DefaultMediaSourceFactory(data).createMediaSource(MediaItem.fromUri(link.url)));
        player.prepare();player.play();
    }

    private ArrayAdapter<String> adapter(List<String> data){return new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,data);}
    private String safe(Exception e){String s=e.getMessage();if(s==null||s.trim().isEmpty())return "STB: portal nuk u përgjigj";s=s.replaceAll("(?i)(mac|token|authorization|cookie|password|username)\\s*[=:]\\s*[^\\s,;]+","$1=[redacted]");return s.length()>180?s.substring(0,180):s;}

    private void setFullscreen(boolean value){
        fullscreen=value;int visibility=value?View.GONE:View.VISIBLE;
        leftPanel.setVisibility(visibility);channelPanel.setVisibility(visibility);topBar.setVisibility(visibility);bottomBar.setVisibility(visibility);
        getWindow().getDecorView().setSystemUiVisibility(value?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY):View.SYSTEM_UI_FLAG_VISIBLE);
        playerView.requestFocus();
    }
    @Override public void onBackPressed(){if(fullscreen){setFullscreen(false);return;}super.onBackPressed();}
    @Override protected void onStop(){super.onStop();if(player!=null)player.pause();}
    @Override protected void onDestroy(){selectionGeneration++;worker.shutdownNow();source.clear();if(player!=null){playerView.setPlayer(null);player.release();player=null;}super.onDestroy();}
}
