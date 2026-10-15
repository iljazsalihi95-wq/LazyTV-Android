package de.lazytv.pro.live;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.media3.common.util.UnstableApi;
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

/** Dedicated native Lazy STB entry point. Portal + MAC own the provider session. */
@UnstableApi
public final class StalkerLivePlayerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final StalkerCatalogSource source = new StalkerCatalogSource();
    private Playlist playlist;
    private PlayerView playerView;
    private LivePlaybackSession playbackSession;
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
            Toast.makeText(this,"Lazy STB kërkon Portal + MAC",Toast.LENGTH_LONG).show();
            finish(); return;
        }
        setContentView(R.layout.activity_live_tv);
        bind(); createPlayer(); loadPortal();
    }

    private void bind() {
        playerView=findViewById(R.id.live_player); status=findViewById(R.id.live_status); title=findViewById(R.id.live_title);
        countriesView=findViewById(R.id.live_country_spinner); categoriesView=findViewById(R.id.live_categories); channelsView=findViewById(R.id.live_channels);
        leftPanel=findViewById(R.id.live_left_panel); channelPanel=findViewById(R.id.live_channel_panel); topBar=findViewById(R.id.live_top_bar); bottomBar=findViewById(R.id.live_bottom_bar);
        idle=findViewById(R.id.live_idle); loading=findViewById(R.id.live_loading_logo);
        title.setText("LAZY STB"); status.setVisibility(View.VISIBLE); status.setText("LAZY STB • Loading portal…");
        findViewById(R.id.live_refresh).setOnClickListener(v->{ source.clear(); loadPortal(); });
        findViewById(R.id.live_prev).setOnClickListener(v->adjacent(-1)); findViewById(R.id.live_next).setOnClickListener(v->adjacent(1));
        countriesView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){selectCountry(pos);}public void onNothingSelected(AdapterView<?> p){}});
        categoriesView.setOnItemClickListener((p,v,pos,id)->selectCategory(pos)); channelsView.setOnItemClickListener((p,v,pos,id)->selectChannel(pos));
        playerView.setUseController(false); playerView.setFocusable(true); playerView.setOnClickListener(v->setFullscreen(!fullscreen));
        findViewById(R.id.live_player_panel).setOnClickListener(v->setFullscreen(!fullscreen)); findViewById(R.id.live_nav_live).setOnClickListener(v->countriesView.requestFocus());
        findViewById(R.id.live_nav_movies).setOnClickListener(v->openCatalog(CatalogType.MOVIES)); findViewById(R.id.live_nav_series).setOnClickListener(v->openCatalog(CatalogType.SERIES));
    }

    private void openCatalog(CatalogType type){Intent intent=new Intent(this,CatalogActivity.class);intent.putExtra(CatalogActivity.EXTRA_PLAYLIST_ID,playlist.getId());intent.putExtra(CatalogActivity.EXTRA_TYPE,type==CatalogType.SERIES?"SERIES":"MOVIES");startActivity(intent);}

    private void createPlayer(){
        playbackSession=new LivePlaybackSession(this,playerView,item->{
            StalkerCatalogSource.LinkResult link=source.resolve(playlist,item);
            return resolved(item,link);
        },new LivePlaybackSession.Listener(){
            public void onBuffering(boolean buffering){loading.setVisibility(buffering?View.VISIBLE:View.GONE);if(buffering){status.setText("LAZY STB • Loading stream…");status.setVisibility(View.VISIBLE);}}
            public void onReady(){loading.setVisibility(View.GONE);status.setVisibility(View.GONE);}
            public void onFatalPlaybackError(String message){loading.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);status.setText("LAZY STB • Kanali nuk u hap");Toast.makeText(StalkerLivePlayerActivity.this,message,Toast.LENGTH_LONG).show();}
        });
    }

    private ResolvedStream resolved(StreamItem item,StalkerCatalogSource.LinkResult link){
        Map<String,String> meta=new LinkedHashMap<>();meta.put("stalker_session","true");
        return new ResolvedStream(link.url,"",item.name,item.logo,item.tvgId,link.headers,meta);
    }

    private void loadPortal(){final long ticket=++selectionGeneration;loading.setVisibility(View.VISIBLE);status.setVisibility(View.VISIBLE);status.setText("LAZY STB • Handshake / Token / Profile / LIVE…");worker.execute(()->{try{Catalog catalog=source.load(playlist);List<Category> live=catalog.categories(CatalogType.LIVE);Map<String,List<StreamItem>> items=new LinkedHashMap<>();Map<String,Integer> sizes=new LinkedHashMap<>();for(Category c:live){List<StreamItem>x=catalog.items(CatalogType.LIVE,c.id);if(!x.isEmpty()){items.put(c.id,x);sizes.put(c.id,x.size());}}List<CountryGroup> grouped=StalkerCountryResolver.groups(live);runOnUiThread(()->{if(ticket!=selectionGeneration||isFinishing())return;byCategory.clear();byCategory.putAll(items);counts.clear();counts.putAll(sizes);countries=new ArrayList<>(grouped);List<String> labels=new ArrayList<>();for(CountryGroup g:countries){int n=0;for(Category c:g.categories)n+=counts.containsKey(c.id)?counts.get(c.id):0;labels.add(g.label+"  ("+n+")");}countriesView.setAdapter(adapter(labels));loading.setVisibility(View.GONE);status.setText(countries.isEmpty()?"LAZY STB • Portali u lidh, por LIVE është bosh":"LAZY STB • Portal connected");status.setVisibility(countries.isEmpty()?View.VISIBLE:View.GONE);if(!countries.isEmpty()){countriesView.setSelection(0);selectCountry(0);countriesView.requestFocus();}});}catch(Exception e){runOnUiThread(()->{if(ticket!=selectionGeneration||isFinishing())return;loading.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);status.setText("LAZY STB • Lidhja me portalin dështoi");Toast.makeText(this,safe(e),Toast.LENGTH_LONG).show();});}});}

    private void selectCountry(int pos){if(pos<0||pos>=countries.size())return;categories=new ArrayList<>(countries.get(pos).categories);List<String> labels=new ArrayList<>();for(Category c:categories)labels.add(CountryResolver.categoryLabel(c.name)+"  ("+(counts.containsKey(c.id)?counts.get(c.id):0)+")");categoriesView.setAdapter(adapter(labels));channels=new ArrayList<>();channelsView.setAdapter(adapter(Collections.emptyList()));if(!categories.isEmpty()){categoriesView.setSelection(0);selectCategory(0);}}
    private void selectCategory(int pos){if(pos<0||pos>=categories.size())return;channels=new ArrayList<>(byCategory.containsKey(categories.get(pos).id)?byCategory.get(categories.get(pos).id):Collections.emptyList());List<String> labels=new ArrayList<>();for(StreamItem s:channels)labels.add(s.name);channelsView.setAdapter(adapter(labels));if(!channels.isEmpty())channelsView.setSelection(0);}
    private void selectChannel(int pos){if(pos<0||pos>=channels.size())return;play(channels.get(pos));}
    private void adjacent(int delta){if(channels.isEmpty())return;int i=current==null?-1:channels.indexOf(current);i=(i+delta+channels.size())%channels.size();channelsView.setSelection(i);play(channels.get(i));}

    /** One selection = one resolver/create_link path. LivePlaybackSession owns resolve, retry and fallback. */
    private void play(StreamItem item){
        final long ticket=++selectionGeneration;
        if(playbackSession==null||ticket!=selectionGeneration)return;
        current=item;
        idle.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText("LAZY STB • create_link…");
        title.setText(item.name==null?"LAZY STB":item.name);
        playbackSession.play(item);
    }

    private ArrayAdapter<String> adapter(List<String> data){return new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,data);}
    private String safe(Exception e){String s=e.getMessage();if(s==null||s.trim().isEmpty())return "Lazy STB: portal nuk u përgjigj";s=s.replaceAll("(?i)(mac|token|authorization|cookie|password|username)\\s*[=:]\\s*[^\\s,;]+","$1=[redacted]");return s.length()>180?s.substring(0,180):s;}
    private void setFullscreen(boolean value){fullscreen=value;int visibility=value?View.GONE:View.VISIBLE;leftPanel.setVisibility(visibility);channelPanel.setVisibility(visibility);topBar.setVisibility(visibility);bottomBar.setVisibility(visibility);getWindow().getDecorView().setSystemUiVisibility(value?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY):View.SYSTEM_UI_FLAG_VISIBLE);playerView.requestFocus();}
    @Override public void onBackPressed(){if(fullscreen){setFullscreen(false);return;}super.onBackPressed();}
    @Override protected void onStop(){if(playbackSession!=null)playbackSession.pauseForBackground();super.onStop();}
    @Override protected void onStart(){super.onStart();if(playbackSession!=null)playbackSession.resumeFromBackground();}
    @Override protected void onDestroy(){selectionGeneration++;worker.shutdownNow();source.clear();if(playbackSession!=null){playbackSession.release();playbackSession=null;}super.onDestroy();}
}
