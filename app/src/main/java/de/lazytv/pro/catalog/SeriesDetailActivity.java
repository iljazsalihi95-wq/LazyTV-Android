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
    private final ExecutorService pool=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final CatalogEngine engine=new CatalogEngine();
    private final AtomicInteger generation=new AtomicInteger();
    private Playlist p;
    private Series series;
    private SeriesDetails details;
    private ArrayAdapter<String> seasonsAdapter,episodesAdapter;
    private ListView seasonsList,episodesList;
    private TextView status,title;
    private Button play,resume;
    private int selectedEpisode=-1;
    private boolean preparing;
    private final ExecutorService images=Executors.newFixedThreadPool(2);
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
        play=findViewById(R.id.episode_play);resume=findViewById(R.id.episode_resume);
        play.setOnClickListener(v->openEpisode(selectedEpisode,false));resume.setOnClickListener(v->openEpisode(selectedEpisode,true));
        play.setEnabled(false);resume.setVisibility(android.view.View.GONE);
        seasonsList.setOnItemClickListener((a,v,pos,id)->selectSeason(pos,true));
        episodesList.setOnItemClickListener((a,v,pos,id)->{showEpisodeInfo(pos);de.lazytv.pro.player.PlaybackResumeStore.Entry saved=resumeEntry(visibleEpisodes.get(pos));if(saved==null)openEpisode(pos,false);else new AlertDialog.Builder(this).setTitle(visibleEpisodes.get(pos).name).setMessage("Vazhdo nga "+time(saved.position)+"?").setPositiveButton("Resume",(d,w)->openEpisode(pos,true)).setNegativeButton("Nga fillimi",(d,w)->openEpisode(pos,false)).show();});
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
        String seriesYear=releaseYear(metadata);
        if(!seriesYear.isEmpty())summary.append("year: ").append(seriesYear);
        for(String key:new String[]{"rating","genre","country","director","cast","plot"}){
            String value=clean(metadata.get(key));if(value.isEmpty()||"null".equalsIgnoreCase(value))continue;
            if(summary.length()>0)summary.append("\n");summary.append(key.equals("plot")?value:key+": "+value);
        }
        TextView info=findViewById(R.id.series_details);info.setText(summary);info.setVisibility(summary.length()==0?android.view.View.GONE:android.view.View.VISIBLE);
        info.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(series.name).setMessage(summary.toString()).setPositiveButton("OK",null).show());

        loadArtwork((ImageView)findViewById(R.id.series_poster),first(metadata,"poster","cover","movie_image", "logo"),series.logo);
        loadArtwork((ImageView)findViewById(R.id.series_backdrop),first(metadata,"backdrop","backdrop_path"),"");
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
        if(preparing&&selectedSeason!=details.seasons.get(pos)){generation.incrementAndGet();preparing=false;}
        selectedSeason=details.seasons.get(pos);
        List<Episode> es=details.episodes.get(selectedSeason.number);
        visibleEpisodes=es==null?Collections.emptyList():es;
        renderEpisodes();
        seasonsList.setItemChecked(pos,true);
        status.setText(selectedSeason.title+" • "+visibleEpisodes.size()+" episode");
        selectedEpisode=visibleEpisodes.isEmpty()?-1:0;updateEpisodeActions();
        if(!visibleEpisodes.isEmpty()){episodesList.setSelection(0);if(moveToEpisodes)episodesList.requestFocus();}
    }

    private void renderEpisodes(){
        episodesAdapter.clear();
        for(Episode e:visibleEpisodes){
            StringBuilder label=new StringBuilder();
            if(e.episodeNumber>0)label.append(e.episodeNumber).append(". ");
            label.append(e.name);
            String duration=clean(e.duration);
            if(!duration.isEmpty())label.append("  •  ").append(duration);
            de.lazytv.pro.player.PlaybackResumeStore.Entry saved=resumeEntry(e);if(saved!=null){label.append("  • Resume ").append(time(saved.position));if(saved.duration>0)label.append(" (").append(Math.min(100,saved.position*100/saved.duration)).append("%)");}
            episodesAdapter.add(label.toString());
        }
    }

    private void showEpisodeInfo(int pos){
        if(pos<0||pos>=visibleEpisodes.size())return;
        selectedEpisode=pos;updateEpisodeActions();
        Episode e=visibleEpisodes.get(pos);
        StringBuilder info=new StringBuilder(selectedSeason==null?"":selectedSeason.title);
        if(e.episodeNumber>0)append(info,"Ep. "+e.episodeNumber);
        append(info,clean(e.duration));
        String rating=clean(e.metadata.get("rating"));
        if(!rating.isEmpty())append(info,"★ "+rating);
        append(info,releaseYear(e.metadata));
        String plot=first(e.metadata,"plot","description","overview","storyline");
        if(!plot.isEmpty())info.append("\n").append(plot);
        status.setText(info.toString());
    }

    private void openEpisode(int pos,boolean resumeRequested){
        if(preparing||pos<0||pos>=visibleEpisodes.size())return;preparing=true;updateEpisodeActions();
        final Episode ep=visibleEpisodes.get(pos);
        final int token=generation.incrementAndGet();
        status.setText("Duke përgatitur stream-in…");
        pool.execute(()->{
            try{
                ResolvedStream rs=engine.resolveStream(p,ep);
                runOnUiThread(()->{
                    if(!isCurrent(token))return;
                    preparing=false;updateEpisodeActions();status.setText("Stream gati");
                    PlayerLauncher.playEpisode(this,p.getId(),ep,rs,series.name,resumeRequested);
                });
            }catch(Exception e){runOnUiThread(()->{if(isCurrent(token)){preparing=false;updateEpisodeActions();status.setText(msg(e,"Stream-i nuk mund të zgjidhet"));}});}
        });
    }

    @Override protected void onResume(){super.onResume();if(episodesAdapter!=null){renderEpisodes();updateEpisodeActions();}}
    private de.lazytv.pro.player.PlaybackResumeStore.Entry resumeEntry(Episode episode){return new de.lazytv.pro.player.PlaybackResumeStore(this).get(de.lazytv.pro.player.PlaybackContentKey.of(p.getId(),episode));}
    private void updateEpisodeActions(){if(play==null)return;boolean valid=selectedEpisode>=0&&selectedEpisode<visibleEpisodes.size();play.setEnabled(valid&&!preparing);resume.setEnabled(valid&&!preparing);de.lazytv.pro.player.PlaybackResumeStore.Entry saved=valid?resumeEntry(visibleEpisodes.get(selectedEpisode)):null;resume.setVisibility(saved==null?android.view.View.GONE:android.view.View.VISIBLE);if(saved!=null)resume.setText("RESUME "+time(saved.position));}
    private String time(long ms){long seconds=ms/1000;return String.format(Locale.getDefault(),"%02d:%02d",seconds/60,seconds%60);}
    private String first(Map<String,String> metadata,String... keys){for(String key:keys){String value=clean(metadata.get(key));if(!value.isEmpty()&&!"null".equalsIgnoreCase(value))return value;}return "";}
    private String releaseYear(Map<String,String> metadata){String value=first(metadata,"year","release_date","releasedate","releaseDate");if(value.isEmpty())return "";java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?:19|20)\\d{2}").matcher(value);return matcher.find()?matcher.group():value;}
    private void loadArtwork(ImageView view,String url,String fallback){if(url.isEmpty())url=clean(fallback);if(url.isEmpty()){view.setVisibility(android.view.View.GONE);return;}final String source=url;view.setTag(source);images.execute(()->{java.net.HttpURLConnection connection=null;try{connection=(java.net.HttpURLConnection)new java.net.URL(source).openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(8000);android.graphics.Bitmap bitmap;try(java.io.InputStream in=connection.getInputStream()){android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=2;bitmap=android.graphics.BitmapFactory.decodeStream(in,null,options);}runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&source.equals(view.getTag())&&bitmap!=null){view.setVisibility(android.view.View.VISIBLE);view.setImageBitmap(bitmap);}});}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}});}
    private boolean isCurrent(int token){return token==generation.get()&&!isFinishing()&&!isDestroyed();}

    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_DOWN){
            int k=e.getKeyCode();
            if(seasonsList!=null&&k==KeyEvent.KEYCODE_DPAD_RIGHT&&seasonsList.hasFocus()&&!visibleEpisodes.isEmpty()){
                episodesList.requestFocus();return true;
            }
            if(episodesList!=null&&k==KeyEvent.KEYCODE_DPAD_LEFT&&episodesList.hasFocus()){
                seasonsList.requestFocus();return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void onBackPressed(){
        if(episodesList!=null&&episodesList.hasFocus()){
            seasonsList.requestFocus();return;
        }
        super.onBackPressed();
    }

    private void append(StringBuilder out,String value){value=clean(value);if(value.isEmpty())return;if(out.length()>0)out.append(" • ");out.append(value);}
    private String clean(String value){return value==null?"":value.trim();}
    private String msg(Exception e,String d){return e.getMessage()==null?d:e.getMessage();}
    @Override protected void onDestroy(){generation.incrementAndGet();pool.shutdownNow();images.shutdownNow();engine.close();super.onDestroy();}
}
