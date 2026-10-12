package de.lazytv.pro.catalog;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import de.lazytv.pro.R;
import de.lazytv.pro.activation.ActivationGuard;
import de.lazytv.pro.playlist.*;
import de.lazytv.pro.player.PlayerLauncher;

/** Native movie page. Opening it never resolves a portal link or starts playback. */
public final class MovieDetailActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService images=Executors.newFixedThreadPool(2);
    private final AtomicInteger generation=new AtomicInteger();
    private final CatalogEngine engine=new CatalogEngine();
    private final Map<String,String> metadata=new LinkedHashMap<>();
    private Playlist playlist;
    private StreamItem movie;
    private TextView facts,plot,status;
    private ImageView poster,backdrop;
    private Button play;
    private String category;
    private boolean preparing;

    public static void open(Activity activity,String playlistId,StreamItem item,ResolvedStream unused,String category) {
        if(activity.isFinishing()||activity.isDestroyed())return;
        Intent i=new Intent(activity,MovieDetailActivity.class);
        i.putExtra("playlist_id",playlistId);i.putExtra("movie",item);i.putExtra("category",category);
        // Do not carry expiring create_link URLs into the details page.
        activity.startActivity(i);
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);if(!ActivationGuard.enforce(this))return;
        playlist=new PlaylistStorage(this).get(getIntent().getStringExtra("playlist_id"));
        movie=(StreamItem)getIntent().getSerializableExtra("movie");category=getIntent().getStringExtra("category");
        if(playlist==null||movie==null){finish();return;}
        metadata.putAll(movie.metadata);buildPage();render();loadDetails();
    }
    private void buildPage() {
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(3,13,29));
        backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setAlpha(.18f);
        root.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll);
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(24),dp(20),dp(24),dp(28));scroll.addView(page);
        TextView label=text("MOVIE DETAILS",14);label.setTextColor(Color.rgb(85,184,255));page.addView(label);
        LinearLayout body=new LinearLayout(this);
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        body.setOrientation(landscape?0:1);body.setPadding(0,dp(18),0,0);page.addView(body);
        poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams art=new LinearLayout.LayoutParams(dp(landscape?180:160),dp(landscape?265:230));art.gravity=Gravity.CENTER_HORIZONTAL;art.setMargins(0,0,landscape?dp(26):0,dp(16));body.addView(poster,art);
        LinearLayout detail=new LinearLayout(this);detail.setOrientation(1);
        body.addView(detail,new LinearLayout.LayoutParams(landscape?0:-1,-2,landscape?1:0));
        TextView title=text(movie.name,28);title.setTypeface(null,Typeface.BOLD);detail.addView(title);
        facts=text("",16);facts.setPadding(0,dp(12),0,dp(16));detail.addView(facts);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(0);detail.addView(buttons);
        play=new Button(this);play.setText("▶ PLAY");play.setTextColor(Color.WHITE);play.setTextSize(20);play.setBackgroundResource(R.drawable.bg_player_button);play.setOnClickListener(v->play());
        buttons.addView(play,new LinearLayout.LayoutParams(0,dp(64),1));
        Button favorite=new Button(this);FavoritesStore favorites=new FavoritesStore(this);
        favorite.setText(favorites.contains(playlist.getId(),movie)?"★ Favorite":"☆ Favorite");favorite.setTextColor(Color.WHITE);favorite.setBackgroundResource(R.drawable.bg_player_button);
        favorite.setOnClickListener(v->{favorites.toggle(playlist.getId(),movie);favorite.setText(favorites.contains(playlist.getId(),movie)?"★ Favorite":"☆ Favorite");});
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(64),1);fp.setMargins(dp(12),0,0,0);buttons.addView(favorite,fp);
        plot=text("",17);plot.setPadding(0,dp(20),0,dp(12));detail.addView(plot);
        status=text("Duke marrë detajet nga provideri…",14);status.setTextColor(Color.rgb(155,180,208));detail.addView(status);
        setContentView(root);play.requestFocus();
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setLineSpacing(dp(3),1);return t;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void render(){
        if(first("duration").isEmpty()&&!first("duration_secs").isEmpty())metadata.put("duration",first("duration_secs")+" s");
        StringBuilder b=new StringBuilder();
        for(String key:new String[]{"year","rating","genre","duration","country","director","cast"}){
            String value=clean(metadata.get(key));if(value.isEmpty())continue;
            if(b.length()>0)b.append('\n');b.append(label(key)).append(": ").append(value);
        }
        facts.setText(b);facts.setVisibility(b.length()==0?View.GONE:View.VISIBLE);
        String description=first("plot","description","overview");plot.setText(description);plot.setVisibility(description.isEmpty()?View.GONE:View.VISIBLE);
        image(poster,first("poster").isEmpty()?movie.logo:first("poster"));image(backdrop,first("backdrop"));
    }
    private String label(String key){switch(key){case "year":return "Viti";case "rating":return "Rating";case "genre":return "Zhanri";case "duration":return "Kohëzgjatja";case "country":return "Shteti";case "director":return "Regjia";default:return "Aktorët";}}
    private String clean(String v){return v==null||"null".equalsIgnoreCase(v.trim())?"":v.trim();}
    private String first(String... keys){for(String key:keys){String v=clean(metadata.get(key));if(!v.isEmpty())return v;}return "";}
    private boolean current(int token){return token==generation.get()&&!isFinishing()&&!isDestroyed();}
    private void loadDetails(){final int token=generation.get();worker.execute(()->{
        try{Map<String,String> info=engine.loadMovieInfo(playlist,movie);runOnUiThread(()->{if(!current(token))return;metadata.putAll(info);render();if(!preparing)status.setText(info.isEmpty()?"Provideri nuk ka dhënë detaje shtesë.":"");});}
        catch(Exception e){runOnUiThread(()->{if(current(token)&&!preparing)status.setText("Detajet shtesë nuk u ngarkuan. "+clean(e.getMessage()));});}
    });}
    private void play(){if(preparing)return;preparing=true;play.setEnabled(false);status.setText("Duke përgatitur stream-in…");final int token=generation.get();
        worker.execute(()->{try{
            ResolvedStream r=engine.resolveStream(this,playlist,movie);runOnUiThread(()->{
                if(!current(token))return;preparing=false;play.setEnabled(true);status.setText("");
                Map<String,String> m=new LinkedHashMap<>(r.metadata);m.putAll(metadata);
                String art=first("poster");if(art.isEmpty())art=r.artwork;
                ResolvedStream ready=new ResolvedStream(r.url,r.streamType,r.title,art,r.epgId,r.headers,m);
                PlayerLauncher.playMovie(this,playlist.getId(),movie,ready,category);
            });
        }catch(Exception e){runOnUiThread(()->{if(!current(token))return;preparing=false;play.setEnabled(true);status.setText(clean(e.getMessage()));play.requestFocus();});}});
    }
    private void image(ImageView view,String url){if(url==null||url.trim().isEmpty()){view.setVisibility(View.GONE);return;}view.setVisibility(View.VISIBLE);if(url.equals(view.getTag()))return;view.setTag(url);final int token=generation.get();
        images.execute(()->{HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent","Mozilla/5.0 LazyTV-PRO");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
            Bitmap b;try(java.io.InputStream in=c.getInputStream()){b=BitmapFactory.decodeStream(in,null,options);}
            if(b!=null)runOnUiThread(()->{if(current(token)&&url.equals(view.getTag()))view.setImageBitmap(b);});
        }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});
    }
    @Override public void onBackPressed(){generation.incrementAndGet();super.onBackPressed();}
    @Override protected void onDestroy(){generation.incrementAndGet();worker.shutdownNow();images.shutdownNow();engine.close();super.onDestroy();}
}
