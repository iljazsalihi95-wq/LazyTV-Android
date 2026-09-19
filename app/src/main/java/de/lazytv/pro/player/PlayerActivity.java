package de.lazytv.pro.player;
import de.lazytv.pro.activation.ActivationGuard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.lazytv.pro.R;
import de.lazytv.pro.catalog.Catalog;
import de.lazytv.pro.catalog.CatalogEngine;
import de.lazytv.pro.catalog.CatalogType;
import de.lazytv.pro.catalog.Episode;
import de.lazytv.pro.catalog.ResolvedStream;
import de.lazytv.pro.catalog.StreamItem;
import de.lazytv.pro.playlist.Playlist;
import de.lazytv.pro.playlist.PlaylistStorage;

@UnstableApi
public class PlayerActivity extends Activity {
    public static final String EXTRA_PLAYLIST_ID = "playlist_id";
    public static final String EXTRA_ITEM = "stream_item";
    public static final String EXTRA_RESOLVED = "resolved_stream";
    public static final String EXTRA_CATEGORY_NAME = "category_name";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final CatalogEngine engine = new CatalogEngine();
    private final List<StreamItem> liveItems = new ArrayList<>();

    private ExoPlayer player;
    private DefaultHttpDataSource.Factory httpFactory;
    private PlayerView playerView;
    private Playlist playlist;
    private StreamItem currentItem;
    private ResolvedStream currentResolved;
    private String playlistId;
    private String categoryName = "";
    private String contentKey = "";
    private boolean live;
    private boolean resumeAfterPause;
    private boolean destroyed;
    private int liveIndex = -1;
    private int aspectMode;

    private View overlay;
    private View actions;
    private TextView title;
    private TextView meta;
    private TextView error;
    private ImageView logo;
    private ProgressBar loading;
    private Button prev;
    private Button next;
    private Button retry;
    private Button aspectButton;

    private final Runnable hideOverlay = () -> {
        if (!destroyed && !isFinishing()) {
            overlay.setVisibility(View.GONE);
            actions.setVisibility(View.GONE);
            playerView.hideController();
            immersive();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!ActivationGuard.enforce(this)) return;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        immersive();
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bindViews();
        playlistId = getIntent().getStringExtra(EXTRA_PLAYLIST_ID);
        playlist = new PlaylistStorage(this).get(playlistId);
        currentItem = (StreamItem) getIntent().getSerializableExtra(EXTRA_ITEM);
        currentResolved = (ResolvedStream) getIntent().getSerializableExtra(EXTRA_RESOLVED);
        categoryName = safe(getIntent().getStringExtra(EXTRA_CATEGORY_NAME));
        if (playlist == null || currentItem == null || currentResolved == null || isBlank(currentResolved.url)) { showFatal("Stream-i nuk mund të hapet"); return; }
        live = currentItem.type == CatalogType.LIVE;
        contentKey = stableContentKey(currentItem);
        setupButtons(); initializePlayer(); playResolved(currentResolved, true);
        if (live) loadLiveContext(); else offerResume();
    }
    private void bindViews(){playerView=findViewById(R.id.player_view);overlay=findViewById(R.id.info_overlay);actions=findViewById(R.id.player_actions);title=findViewById(R.id.player_title);meta=findViewById(R.id.player_meta);error=findViewById(R.id.player_error);logo=findViewById(R.id.player_logo);loading=findViewById(R.id.player_loading);prev=findViewById(R.id.btn_prev);next=findViewById(R.id.btn_next);retry=findViewById(R.id.btn_retry);aspectButton=findViewById(R.id.btn_aspect);}
    private void setupButtons(){prev.setVisibility(live?View.VISIBLE:View.GONE);next.setVisibility(live?View.VISIBLE:View.GONE);prev.setOnClickListener(v->switchLive(-1));next.setOnClickListener(v->switchLive(1));retry.setOnClickListener(v->reresolve(true));aspectButton.setOnClickListener(v->cycleAspect());findViewById(R.id.btn_audio).setOnClickListener(v->showTracks(C.TRACK_TYPE_AUDIO,false));findViewById(R.id.btn_subtitles).setOnClickListener(v->showTracks(C.TRACK_TYPE_TEXT,true));}
    private void initializePlayer(){if(player!=null)return;httpFactory=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(12_000).setReadTimeoutMs(20_000).setAllowCrossProtocolRedirects(true);DefaultDataSource.Factory dataSourceFactory=new DefaultDataSource.Factory(this,httpFactory);player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory)).build();playerView.setPlayer(player);player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(destroyed)return;loading.setVisibility(state==Player.STATE_BUFFERING?View.VISIBLE:View.GONE);if(state==Player.STATE_ENDED&&!live)new PlaybackResumeStore(PlayerActivity.this).clear(contentKey);}@Override public void onPlayerError(PlaybackException e){if(destroyed)return;loading.setVisibility(View.GONE);showPlaybackError(e);}});}
    private void playResolved(ResolvedStream resolved,boolean autoPlay){if(destroyed||player==null||resolved==null||isBlank(resolved.url)){showSafeError("Stream-i nuk mund të përgatitet");return;}currentResolved=resolved;error.setVisibility(View.GONE);retry.setVisibility(View.GONE);loading.setVisibility(View.VISIBLE);httpFactory.setDefaultRequestProperties(resolved.headers);MediaItem.Builder media=new MediaItem.Builder().setUri(Uri.parse(resolved.url));String mime=mimeHint(resolved);if(mime!=null)media.setMimeType(mime);player.setMediaItem(media.build(),true);player.prepare();player.setPlayWhenReady(autoPlay);showInfoOverlay();}
    private String mimeHint(ResolvedStream resolved){String metadataMime=resolved.metadata==null?null:resolved.metadata.get("mime");if(!isBlank(metadataMime))return metadataMime;String u=safe(resolved.url).toLowerCase(Locale.US);int query=u.indexOf('?');if(query>=0)u=u.substring(0,query);if(u.endsWith(".m3u8"))return MimeTypes.APPLICATION_M3U8;if(u.endsWith(".mp4")||u.endsWith(".m4v"))return MimeTypes.VIDEO_MP4;if(u.endsWith(".ts")||u.endsWith(".mpegts"))return MimeTypes.VIDEO_MP2T;return null;}
    private void offerResume(){PlaybackResumeStore.Entry entry=new PlaybackResumeStore(this).get(contentKey);if(entry==null||entry.position<PlaybackResumeStore.MIN_RESUME_MS)return;player.setPlayWhenReady(false);new AlertDialog.Builder(this).setTitle("Vazhdo shikimin?").setMessage(formatTime(entry.position)+" / "+formatTime(entry.duration)).setPositiveButton("Resume",(d,w)->{if(player!=null){player.seekTo(Math.max(0,entry.position));player.play();}}).setNegativeButton("Nga fillimi",(d,w)->{new PlaybackResumeStore(this).clear(contentKey);if(player!=null){player.seekTo(0);player.play();}}).setOnCancelListener(d->{if(player!=null)player.play();}).show();}
    private void loadLiveContext(){worker.execute(()->{try{Catalog catalog=engine.load(this,playlist);List<StreamItem> items=catalog.items(CatalogType.LIVE,currentItem.categoryId);synchronized(liveItems){liveItems.clear();liveItems.addAll(items);liveIndex=-1;for(int i=0;i<items.size();i++)if(safe(items.get(i).id).equals(safe(currentItem.id))){liveIndex=i;break;}}}catch(Exception ignored){}});}
    private void switchLive(int delta){StreamItem target;synchronized(liveItems){if(liveItems.isEmpty())return;int base=liveIndex>=0&&liveIndex<liveItems.size()?liveIndex:0;int nextIndex=(base+delta+liveItems.size())%liveItems.size();target=liveItems.get(nextIndex);liveIndex=nextIndex;}resolveAndPlay(target,false);}
    private void resolveAndPlay(StreamItem item,boolean forceRefresh){if(item==null||destroyed)return;loading.setVisibility(View.VISIBLE);worker.execute(()->{try{ResolvedStream resolved=forceRefresh?engine.refreshStream(playlist,item):engine.resolveStream(playlist,item);ui.post(()->{if(destroyed||isFinishing())return;currentItem=item;live=item.type==CatalogType.LIVE;contentKey=stableContentKey(item);prev.setVisibility(live?View.VISIBLE:View.GONE);next.setVisibility(live?View.VISIBLE:View.GONE);playResolved(resolved,true);});}catch(Exception ignored){ui.post(()->{if(!destroyed&&!isFinishing())showSafeError("Stream-i nuk mund të përgatitet");});}});}
    private void reresolve(boolean forceRefresh){resolveAndPlay(currentItem,forceRefresh);}
    private void showPlaybackError(PlaybackException e){String message="Playback dështoi";Throwable cause=e.getCause();if(cause instanceof HttpDataSource.InvalidResponseCodeException){int code=((HttpDataSource.InvalidResponseCodeException)cause).responseCode;if(code==401||code==403)message="Serveri refuzoi stream-in. Provo Retry.";else message="Gabim HTTP "+code+" gjatë playback.";}else if(e.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED)message="Codec ose formati nuk mbështetet nga kjo pajisje.";else if(e.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)message="Serveri nuk u përgjigj brenda afatit.";else if(e.errorCode==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)message="Serveri ktheu një gabim gjatë playback.";else if(e.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)message="Nuk mund të lidhet me stream-in.";showSafeError(message);}
    private void showSafeError(String message){if(destroyed)return;loading.setVisibility(View.GONE);error.setText(message);error.setVisibility(View.VISIBLE);retry.setVisibility(View.VISIBLE);showControls();}
    private void showFatal(String message){showSafeError(message);retry.setVisibility(View.GONE);}
    private void showInfoOverlay(){if(currentResolved==null)return;title.setText(safe(currentResolved.title));StringBuilder info=new StringBuilder();if(!categoryName.isEmpty())info.append(categoryName);String season=currentResolved.metadata.get("season"),episode=currentResolved.metadata.get("episode");if(!isBlank(season)){if(info.length()>0)info.append(" • ");info.append("S").append(season);if(!isBlank(episode))info.append(" E").append(episode);}String now=currentResolved.metadata.get("epg_now"),nextProgram=currentResolved.metadata.get("epg_next");if(!isBlank(now))info.append("\nNow: ").append(now);if(!isBlank(nextProgram))info.append("\nNext: ").append(nextProgram);meta.setText(info.toString());overlay.setVisibility(View.VISIBLE);actions.setVisibility(View.VISIBLE);loadArtwork(currentResolved.artwork);ui.removeCallbacks(hideOverlay);ui.postDelayed(hideOverlay,4500);}
    private void loadArtwork(String artworkUrl){logo.setImageDrawable(null);if(isBlank(artworkUrl)||!(artworkUrl.startsWith("http://")||artworkUrl.startsWith("https://")))return;final String expectedUrl=artworkUrl;worker.execute(()->{HttpURLConnection connection=null;try{connection=(HttpURLConnection)new URL(expectedUrl).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(7000);Bitmap bitmap=BitmapFactory.decodeStream(connection.getInputStream());if(bitmap!=null)ui.post(()->{if(!destroyed&&currentResolved!=null&&expectedUrl.equals(currentResolved.artwork))logo.setImageBitmap(bitmap);});}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}});}
    private void cycleAspect(){aspectMode=(aspectMode+1)%3;if(aspectMode==0){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);aspectButton.setText("FIT");}else if(aspectMode==1){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);aspectButton.setText("FILL");}else{playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);aspectButton.setText("ZOOM");}}
    private void showTracks(int trackType,boolean allowOff){if(player==null)return;List<Tracks.Group> groups=new ArrayList<>();List<Integer> trackIndices=new ArrayList<>();List<String> labels=new ArrayList<>();if(allowOff)labels.add("Off");for(Tracks.Group group:player.getCurrentTracks().getGroups()){if(group.getType()!=trackType)continue;for(int i=0;i<group.length;i++){if(!group.isTrackSupported(i))continue;groups.add(group);trackIndices.add(i);Format format=group.getTrackFormat(i);String label=format.label;if(isBlank(label))label=format.language;if(isBlank(label)){int ordinal=labels.size()+(allowOff?0:1);label=(trackType==C.TRACK_TYPE_AUDIO?"Audio ":"Subtitle ")+ordinal;}labels.add(label);}}if(groups.isEmpty()){if(allowOff){labels.clear();labels.add("Off");}else{Toast.makeText(this,"Nuk ka audio tracks të tjera",Toast.LENGTH_SHORT).show();return;}}new AlertDialog.Builder(this).setTitle(trackType==C.TRACK_TYPE_AUDIO?"Audio":"Subtitles").setItems(labels.toArray(new String[0]),(dialog,which)->{if(player==null)return;TrackSelectionParameters.Builder parameters=player.getTrackSelectionParameters().buildUpon();if(allowOff&&which==0)parameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true);else{int index=allowOff?which-1:which;if(index<0||index>=groups.size())return;Tracks.Group group=groups.get(index);parameters.setTrackTypeDisabled(trackType,false);parameters.setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),trackIndices.get(index).intValue()));}player.setTrackSelectionParameters(parameters.build());}).show();}
    private void showControls(){if(destroyed)return;playerView.showController();overlay.setVisibility(View.VISIBLE);actions.setVisibility(View.VISIBLE);View focus=live&&prev.getVisibility()==View.VISIBLE?prev:aspectButton;focus.requestFocus();ui.removeCallbacks(hideOverlay);ui.postDelayed(hideOverlay,5000);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getAction()!=KeyEvent.ACTION_DOWN||event.getRepeatCount()>0)return super.dispatchKeyEvent(event);switch(event.getKeyCode()){case KeyEvent.KEYCODE_BACK:handleBack();return true;case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:if(actions.getVisibility()==View.VISIBLE||playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);showControls();return true;case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:if(player!=null){if(player.isPlaying())player.pause();else player.play();}return true;case KeyEvent.KEYCODE_MEDIA_PLAY:if(player!=null)player.play();return true;case KeyEvent.KEYCODE_MEDIA_PAUSE:if(player!=null)player.pause();return true;case KeyEvent.KEYCODE_DPAD_LEFT:if(actions.getVisibility()==View.VISIBLE||playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);if(!live&&player!=null)player.seekTo(Math.max(0,player.getCurrentPosition()-10_000));showControls();return true;case KeyEvent.KEYCODE_MEDIA_REWIND:if(!live&&player!=null)player.seekTo(Math.max(0,player.getCurrentPosition()-10_000));return true;case KeyEvent.KEYCODE_DPAD_RIGHT:if(actions.getVisibility()==View.VISIBLE||playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);if(!live&&player!=null){long duration=player.getDuration(),target=player.getCurrentPosition()+10_000;if(duration>0)target=Math.min(duration,target);player.seekTo(Math.max(0,target));}showControls();return true;case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:if(!live&&player!=null){long duration=player.getDuration(),target=player.getCurrentPosition()+10_000;if(duration>0)target=Math.min(duration,target);player.seekTo(Math.max(0,target));}return true;case KeyEvent.KEYCODE_DPAD_UP:if(actions.getVisibility()==View.VISIBLE||playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);if(live)switchLive(-1);else showControls();return true;case KeyEvent.KEYCODE_DPAD_DOWN:if(actions.getVisibility()==View.VISIBLE||playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);if(live)switchLive(1);else showControls();return true;default:return super.dispatchKeyEvent(event);}}
    @Override public void onBackPressed(){handleBack();}
    private void handleBack(){if(actions.getVisibility()==View.VISIBLE||playerView.isControllerFullyVisible()){overlay.setVisibility(View.GONE);actions.setVisibility(View.GONE);playerView.hideController();immersive();}else super.onBackPressed();}
    @Override protected void onStart(){super.onStart();if(Build.VERSION.SDK_INT>23)playerView.onResume();}
    @Override protected void onResume(){super.onResume();immersive();if(Build.VERSION.SDK_INT<=23)playerView.onResume();if(resumeAfterPause&&player!=null){player.play();resumeAfterPause=false;}}
    @Override protected void onPause(){saveResume();if(player!=null){resumeAfterPause=player.isPlaying();player.pause();}if(Build.VERSION.SDK_INT<=23)playerView.onPause();super.onPause();}
    @Override protected void onStop(){saveResume();if(Build.VERSION.SDK_INT>23)playerView.onPause();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;ui.removeCallbacksAndMessages(null);saveResume();releasePlayer();worker.shutdownNow();engine.close();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy();}
    private void saveResume(){if(live||player==null||isBlank(contentKey))return;long position=player.getCurrentPosition(),duration=player.getDuration();if(position<0)return;new PlaybackResumeStore(this).save(contentKey,position,duration);}
    private void releasePlayer(){if(player==null)return;playerView.setPlayer(null);player.release();player=null;httpFactory=null;}
    private String stableContentKey(StreamItem item){StringBuilder raw=new StringBuilder("lazytv|").append(safe(playlistId)).append('|').append(safe(item.type==null?null:item.type.name())).append('|').append(safe(item.id));if(item instanceof Episode){Episode e=(Episode)item;raw.append('|').append(safe(e.seriesId)).append('|').append(e.seasonNumber).append('|').append(e.episodeNumber);}return sha256(raw.toString());}
    private String sha256(String value){try{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=digest.digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder(bytes.length*2);for(byte b:bytes)result.append(String.format(Locale.US,"%02x",b&0xff));return result.toString();}catch(Exception ignored){return Integer.toHexString(value.hashCode());}}
    private void immersive(){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private boolean isBlank(String value){return value==null||value.trim().isEmpty();}
    private String safe(String value){return value==null?"":value;}
    private String formatTime(long ms){if(ms<=0)return"--:--";long seconds=ms/1000;return String.format(Locale.US,"%d:%02d:%02d",seconds/3600,(seconds%3600)/60,seconds%60);}
}
