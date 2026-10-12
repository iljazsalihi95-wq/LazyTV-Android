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
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.lazytv.pro.R;
import de.lazytv.pro.catalog.Catalog;
import de.lazytv.pro.catalog.ProviderPlaybackPolicy;
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
    public static final String EXTRA_RESUME_MODE="resume_mode";
    private long pendingSeekMs=-1;
    private boolean desiredPlayback=true,foreground=true;
    private final Runnable saveProgress=new Runnable(){public void run(){if(destroyed)return;if(!live)saveResume();ui.postDelayed(this,15000);}};
    private boolean resumeAfterPause;
    private boolean destroyed;
    private long selectionGeneration=1;
    private int providerRetries;
    private boolean providerRetryRunning;
    private int liveIndex = -1;
    private int aspectMode=0;
    private org.videolan.libvlc.LibVLC vlc;
    private org.videolan.libvlc.MediaPlayer vlcPlayer;
    private android.view.SurfaceView vlcSurface;
    private boolean usingVlc=false,vlcFallbackTried=false,usingIjk=false,ijkFallbackTried=false;
    private tv.danmaku.ijk.media.player.IjkMediaPlayer ijkPlayer;
    private android.view.SurfaceView ijkSurface;

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
        if(!PlayerContentPolicy.playable(currentItem)){showFatal("Zgjidhni sezonin dhe episodin");return;}
        live = currentItem.type == CatalogType.LIVE;
        contentKey = stableContentKey(currentItem);
        setupButtons(); initializePlayer(); playResolved(currentResolved, true);
        if (live) loadLiveContext(); else offerResume();
    }
    private void bindViews(){playerView=findViewById(R.id.player_view);overlay=findViewById(R.id.info_overlay);actions=findViewById(R.id.player_actions);title=findViewById(R.id.player_title);meta=findViewById(R.id.player_meta);error=findViewById(R.id.player_error);logo=findViewById(R.id.player_logo);loading=findViewById(R.id.player_loading);prev=findViewById(R.id.btn_prev);next=findViewById(R.id.btn_next);retry=findViewById(R.id.btn_retry);aspectButton=findViewById(R.id.btn_aspect);}
    private void setupButtons(){prev.setVisibility(live?View.VISIBLE:View.GONE);next.setVisibility(live?View.VISIBLE:View.GONE);prev.setOnClickListener(v->switchLive(-1));next.setOnClickListener(v->switchLive(1));retry.setOnClickListener(v->reresolve(true));aspectButton.setOnClickListener(v->cycleAspect());findViewById(R.id.btn_audio).setOnClickListener(v->showTracks(C.TRACK_TYPE_AUDIO,false));findViewById(R.id.btn_subtitles).setOnClickListener(v->showTracks(C.TRACK_TYPE_TEXT,true));}
    private void initializePlayer(){if(player!=null)return;int savedAspect=getSharedPreferences("lazytv_player_settings",MODE_PRIVATE).getInt("aspect_mode",0);aspectMode=Math.max(0,Math.min(2,savedAspect));int resize=aspectMode==1?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:(aspectMode==2?AspectRatioFrameLayout.RESIZE_MODE_FILL:AspectRatioFrameLayout.RESIZE_MODE_FIT);playerView.setResizeMode(resize);aspectButton.setText(aspectMode==1?"ZOOM":(aspectMode==2?"FILL":"FIT"));httpFactory=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(15_000).setReadTimeoutMs(60_000).setAllowCrossProtocolRedirects(true).setKeepPostFor302Redirects(true);DefaultDataSource.Factory dataSourceFactory=new DefaultDataSource.Factory(this,httpFactory);DefaultLoadControl loadControl=new DefaultLoadControl.Builder().setBufferDurationsMs(15_000,60_000,2_500,5_000).setPrioritizeTimeOverSizeThresholds(true).build();player=new ExoPlayer.Builder(this).setLoadControl(loadControl).setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory)).build();playerView.setPlayer(player);player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(destroyed||!currentPlayback())return;loading.setVisibility(state==Player.STATE_BUFFERING?View.VISIBLE:View.GONE);if(state==Player.STATE_READY&&!usingVlc&&!usingIjk)applyPendingSeek();if(state==Player.STATE_ENDED&&!live)new PlaybackResumeStore(PlayerActivity.this).clear(contentKey);}@Override public void onPlayerError(PlaybackException e){if(destroyed||!currentPlayback()||providerRetryRunning)return;loading.setVisibility(View.GONE);if(recoverProviderFailure(e))return;if(!usingVlc&&!vlcFallbackTried&&currentResolved!=null){vlcFallbackTried=true;playWithVlc(currentResolved);return;}showPlaybackError(e);}});}
    private boolean preferVlc(ResolvedStream r){String m=mimeHint(r),u=safe(r.url).toLowerCase(Locale.US);return MimeTypes.VIDEO_MP2T.equals(m)||u.contains("extension=ts")||u.contains("output=ts");}
    private void playWithVlc(ResolvedStream r){try{if(!live)pendingSeekMs=Math.max(pendingSeekMs,playbackPosition());usingVlc=true;usingIjk=false;if(player!=null)player.stop();playerView.setVisibility(View.GONE);if(vlcSurface==null){vlcSurface=new android.view.SurfaceView(this);android.view.ViewGroup parent=(android.view.ViewGroup)playerView.getParent();int idx=parent.indexOfChild(playerView);parent.addView(vlcSurface,idx,playerView.getLayoutParams());}vlcSurface.setVisibility(View.VISIBLE);if(vlc==null)vlc=new org.videolan.libvlc.LibVLC(this,new java.util.ArrayList<String>());if(vlcPlayer==null)vlcPlayer=new org.videolan.libvlc.MediaPlayer(vlc);vlcPlayer.getVLCVout().setVideoView(vlcSurface);vlcPlayer.getVLCVout().attachViews();org.videolan.libvlc.Media media=new org.videolan.libvlc.Media(vlc,Uri.parse(r.url));media.addOption(":network-caching=1500");String ua=r.headers.get("User-Agent"),ref=r.headers.get("Referer");if(!isBlank(ua))media.addOption(":http-user-agent="+ua);if(!isBlank(ref))media.addOption(":http-referrer="+ref);vlcPlayer.setMedia(media);media.release();vlcSurface.setOnClickListener(v->showControls());vlcPlayer.setEventListener(event->{if(event.type==org.videolan.libvlc.MediaPlayer.Event.Playing||event.type==org.videolan.libvlc.MediaPlayer.Event.TimeChanged)ui.post(()->{if(destroyed||currentResolved!=r||!usingVlc)return;applyPendingSeek();if(!desiredPlayback)vlcPlayer.pause();});if(event.type==org.videolan.libvlc.MediaPlayer.Event.EndReached)ui.post(()->{if(!destroyed&&currentResolved==r&&!live)new PlaybackResumeStore(this).clear(contentKey);});if(event.type==org.videolan.libvlc.MediaPlayer.Event.EncounteredError){ui.post(()->{if(destroyed||currentResolved!=r||!usingVlc)return;if(!ijkFallbackTried&&currentResolved!=null){ijkFallbackTried=true;playWithIjk(currentResolved);}else showSafeError("Playback dështoi me ExoPlayer, VLC dhe IJK");});}});vlcPlayer.play();loading.setVisibility(View.GONE);showInfoOverlay();}catch(Exception e){usingVlc=false;if(!ijkFallbackTried&&currentResolved!=null){ijkFallbackTried=true;playWithIjk(currentResolved);}else showSafeError("Playback dështoi me ExoPlayer, VLC dhe IJK");}}
    private void playWithIjk(ResolvedStream r){try{if(!live)pendingSeekMs=Math.max(pendingSeekMs,playbackPosition());usingIjk=true;usingVlc=false;if(vlcPlayer!=null)vlcPlayer.stop();if(vlcSurface!=null)vlcSurface.setVisibility(View.GONE);playerView.setVisibility(View.GONE);if(ijkSurface==null){ijkSurface=new android.view.SurfaceView(this);android.view.ViewGroup parent=(android.view.ViewGroup)playerView.getParent();int idx=parent.indexOfChild(playerView);parent.addView(ijkSurface,idx,playerView.getLayoutParams());}ijkSurface.setVisibility(View.VISIBLE);if(ijkPlayer!=null){try{ijkPlayer.stop();ijkPlayer.release();}catch(Exception ignored){}}ijkPlayer=new tv.danmaku.ijk.media.player.IjkMediaPlayer();ijkPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER,"mediacodec",1);ijkPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT,"timeout",15000000L);ijkPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT,"reconnect",1);StringBuilder headerBlock=new StringBuilder();for(java.util.Map.Entry<String,String> h:r.headers.entrySet())headerBlock.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");ijkPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT,"headers",headerBlock.toString());ijkPlayer.setDisplay(ijkSurface.getHolder());ijkPlayer.setDataSource(r.url);ijkPlayer.setOnPreparedListener(mp->{if(destroyed||currentResolved!=r||mp!=ijkPlayer)return;loading.setVisibility(View.GONE);applyPendingSeek();if(desiredPlayback)mp.start();showInfoOverlay();});ijkSurface.setOnClickListener(v->showControls());ijkPlayer.setOnCompletionListener(mp->{if(!destroyed&&!live&&mp==ijkPlayer)new PlaybackResumeStore(this).clear(contentKey);});ijkPlayer.setOnErrorListener((mp,what,extra)->{ui.post(()->{if(!destroyed&&currentResolved==r&&mp==ijkPlayer)showSafeError("Playback dështoi me ExoPlayer, VLC dhe IJK");});return true;});ijkPlayer.prepareAsync();}catch(Exception e){usingIjk=false;showSafeError("Playback dështoi me ExoPlayer, VLC dhe IJK");}}
    private void playResolved(ResolvedStream resolved,boolean autoPlay){if(destroyed||player==null||resolved==null||isBlank(resolved.url)){showSafeError("Stream-i nuk mund të përgatitet");return;}currentResolved=resolved;desiredPlayback=autoPlay&&foreground;if(autoPlay&&!foreground)resumeAfterPause=true;vlcFallbackTried=false;ijkFallbackTried=false;error.setVisibility(View.GONE);retry.setVisibility(View.GONE);loading.setVisibility(View.VISIBLE);
        // Start every stream with Media3/ExoPlayer. If it cannot play the provider stream, onPlayerError falls back to LibVLC.
        // Do not force MPEG-TS directly to VLC: many Xtream servers behave better through the primary HTTP engine first.
        usingVlc=false;usingIjk=false;if(vlcPlayer!=null)vlcPlayer.stop();if(vlcSurface!=null)vlcSurface.setVisibility(View.GONE);if(ijkPlayer!=null){try{ijkPlayer.stop();}catch(Exception ignored){}}if(ijkSurface!=null)ijkSurface.setVisibility(View.GONE);playerView.setVisibility(View.VISIBLE);httpFactory.setDefaultRequestProperties(resolved.headers);MediaItem.Builder media=new MediaItem.Builder().setUri(Uri.parse(resolved.url)).setTag(Long.valueOf(selectionGeneration));String mime=mimeHint(resolved);if(mime!=null)media.setMimeType(mime);player.setMediaItem(media.build(),true);player.prepare();player.setPlayWhenReady(desiredPlayback);showInfoOverlay();}
    private String mimeHint(ResolvedStream resolved){String metadataMime=resolved.metadata==null?null:resolved.metadata.get("mime");if(!isBlank(metadataMime))return metadataMime;String u=safe(resolved.url).toLowerCase(Locale.US);int query=u.indexOf('?');if(query>=0)u=u.substring(0,query);if(u.endsWith(".m3u8"))return MimeTypes.APPLICATION_M3U8;if(u.endsWith(".mp4")||u.endsWith(".m4v"))return MimeTypes.VIDEO_MP4;if(u.endsWith(".ts")||u.endsWith(".mpegts"))return MimeTypes.VIDEO_MP2T;String full=safe(resolved.url).toLowerCase(Locale.US);if(full.contains("extension=ts")||full.contains("extension=mpegts")||full.contains("output=ts"))return MimeTypes.VIDEO_MP2T;return null;}
    private void offerResume(){
        String mode=getIntent().getStringExtra(EXTRA_RESUME_MODE);PlaybackResumeStore store=new PlaybackResumeStore(this);PlaybackResumeStore.Entry entry=store.get(contentKey);
        if("start".equals(mode)){store.clear(contentKey);seekPlayback(0);setPlaybackRunning(true);return;}
        if(entry==null)return;
        if("resume".equals(mode)){seekPlayback(entry.position);setPlaybackRunning(true);return;}
        setPlaybackRunning(false);
        new AlertDialog.Builder(this).setTitle("Vazhdo shikimin?").setMessage(formatTime(entry.position)+" / "+formatTime(entry.duration))
            .setPositiveButton("Resume",(dialog,which)->{seekPlayback(entry.position);setPlaybackRunning(true);})
            .setNegativeButton("Nga fillimi",(dialog,which)->{store.clear(contentKey);seekPlayback(0);setPlaybackRunning(true);})
            .setOnCancelListener(dialog->setPlaybackRunning(true)).show();
    }
    private long playbackPosition(){try{if(usingIjk&&ijkPlayer!=null)return Math.max(0,ijkPlayer.getCurrentPosition());if(usingVlc&&vlcPlayer!=null)return Math.max(0,vlcPlayer.getTime());return player==null?0:Math.max(0,player.getCurrentPosition());}catch(Exception unavailable){return 0;}}
    private long playbackDuration(){try{if(usingIjk&&ijkPlayer!=null)return Math.max(0,ijkPlayer.getDuration());if(usingVlc&&vlcPlayer!=null)return Math.max(0,vlcPlayer.getLength());return player==null?0:Math.max(0,player.getDuration());}catch(Exception unavailable){return 0;}}
    private boolean playbackRunning(){try{if(usingIjk&&ijkPlayer!=null)return ijkPlayer.isPlaying();if(usingVlc&&vlcPlayer!=null)return vlcPlayer.isPlaying();return player!=null&&player.isPlaying();}catch(Exception unavailable){return false;}}
    private void setPlaybackRunning(boolean play){play=play&&foreground;desiredPlayback=play;try{if(usingIjk&&ijkPlayer!=null){if(play)ijkPlayer.start();else ijkPlayer.pause();}else if(usingVlc&&vlcPlayer!=null){if(play)vlcPlayer.play();else vlcPlayer.pause();}else if(player!=null)player.setPlayWhenReady(play);}catch(Exception preparing){/* Prepared callbacks apply desiredPlayback. */}}
    private void seekPlayback(long position){pendingSeekMs=Math.max(0,position);applyPendingSeek();}
    private void applyPendingSeek(){if(pendingSeekMs<0||live)return;try{
        long target=pendingSeekMs;
        if(usingIjk&&ijkPlayer!=null){ijkPlayer.seekTo(target);pendingSeekMs=-1;}
        else if(usingVlc&&vlcPlayer!=null){if(vlcPlayer.isSeekable()){pendingSeekMs=-1;vlcPlayer.setTime(target);}}
        else if(player!=null&&player.getPlaybackState()==Player.STATE_READY){pendingSeekMs=-1;player.seekTo(target);}
    }catch(Exception preparing){/* Keep the target until this engine is ready. */}}
    private void loadLiveContext(){worker.execute(()->{try{Catalog catalog=engine.load(this,playlist);List<StreamItem> items=catalog.items(CatalogType.LIVE,currentItem.categoryId);synchronized(liveItems){liveItems.clear();liveItems.addAll(items);liveIndex=-1;for(int i=0;i<items.size();i++)if(safe(items.get(i).id).equals(safe(currentItem.id))){liveIndex=i;break;}}}catch(Exception ignored){}});}
    private void switchLive(int delta){StreamItem target;synchronized(liveItems){if(liveItems.isEmpty())return;int base=liveIndex>=0&&liveIndex<liveItems.size()?liveIndex:0;int nextIndex=(base+delta+liveItems.size())%liveItems.size();target=liveItems.get(nextIndex);liveIndex=nextIndex;}resolveAndPlay(target,false);}
    private boolean currentPlayback(){MediaItem media=player==null?null:player.getCurrentMediaItem();return media!=null&&media.localConfiguration!=null&&Long.valueOf(selectionGeneration).equals(media.localConfiguration.tag);}
    private void resolveAndPlay(StreamItem item,boolean forceRefresh){
        if(item==null||destroyed)return;final long token=++selectionGeneration;providerRetries=0;providerRetryRunning=false;loading.setVisibility(View.VISIBLE);
        worker.execute(()->{try{ResolvedStream resolved=forceRefresh?engine.refreshStream(playlist,item):engine.resolveStream(playlist,item);
            ui.post(()->{if(destroyed||isFinishing()||token!=selectionGeneration)return;currentItem=item;live=item.type==CatalogType.LIVE;contentKey=stableContentKey(item);prev.setVisibility(live?View.VISIBLE:View.GONE);next.setVisibility(live?View.VISIBLE:View.GONE);playResolved(resolved,true);});
        }catch(Exception e){ui.post(()->{if(!destroyed&&!isFinishing()&&token==selectionGeneration)showSafeError(e.getMessage()==null?"Stream-i nuk mund të përgatitet":e.getMessage());});}});
    }
    private void reresolve(boolean forceRefresh){resolveAndPlay(currentItem,forceRefresh);}
    private boolean recoverProviderFailure(PlaybackException error){
        final int code=ProviderPlaybackPolicy.status(error);
        if(code!=401&&code!=402&&code!=403&&code!=409)return false;
        if(code==402||providerRetries>=2){showSafeError(ProviderPlaybackPolicy.message(code));return true;}
        if(providerRetryRunning)return true;
        providerRetryRunning=true;final int attempt=++providerRetries;final long token=++selectionGeneration;final StreamItem expected=currentItem;
        final long position=playbackPosition();loading.setVisibility(View.VISIBLE);
        worker.execute(()->{try{Thread.sleep(350L*attempt);ResolvedStream resolved=engine.refreshStream(playlist,expected);
            final ResolvedStream ready=ProviderPlaybackPolicy.retryHeaders(resolved,expected,code,attempt);
            ui.post(()->{if(destroyed||isFinishing()||token!=selectionGeneration||expected!=currentItem)return;providerRetryRunning=false;playResolved(ready,true);if(!live&&position>0)seekPlayback(position);});
        }catch(Exception e){ui.post(()->{if(destroyed||isFinishing()||token!=selectionGeneration)return;providerRetryRunning=false;showSafeError(e.getMessage()==null?ProviderPlaybackPolicy.message(code):e.getMessage());});}});
        return true;
    }
    private void showPlaybackError(PlaybackException e){String message="Playback dështoi";Throwable cause=e.getCause();if(cause instanceof HttpDataSource.InvalidResponseCodeException){int code=((HttpDataSource.InvalidResponseCodeException)cause).responseCode;if(code==401||code==403)message="Serveri refuzoi stream-in. Provo Retry.";else message="Gabim HTTP "+code+" gjatë playback.";}else if(e.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED)message="Codec ose formati nuk mbështetet nga kjo pajisje.";else if(e.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)message="Serveri nuk u përgjigj brenda afatit.";else if(e.errorCode==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)message="Serveri ktheu një gabim gjatë playback.";else if(e.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)message="Nuk mund të lidhet me stream-in.";showSafeError(message);}
    private void showSafeError(String message){if(destroyed)return;loading.setVisibility(View.GONE);error.setText(message);error.setVisibility(View.VISIBLE);retry.setVisibility(View.VISIBLE);showControls();}
    private void showFatal(String message){showSafeError(message);retry.setVisibility(View.GONE);}
    private void showInfoOverlay(){if(currentResolved==null)return;title.setText(safe(currentResolved.title));StringBuilder info=new StringBuilder();if(!categoryName.isEmpty())info.append(categoryName);String season=currentResolved.metadata.get("season"),episode=currentResolved.metadata.get("episode");if(!isBlank(season)){if(info.length()>0)info.append(" • ");info.append("S").append(season);if(!isBlank(episode))info.append(" E").append(episode);}String now=currentResolved.metadata.get("epg_now"),nextProgram=currentResolved.metadata.get("epg_next");if(!isBlank(now))info.append("\nNow: ").append(now);if(!isBlank(nextProgram))info.append("\nNext: ").append(nextProgram);meta.setText(info.toString());overlay.setVisibility(View.VISIBLE);actions.setVisibility(View.VISIBLE);loadArtwork(currentResolved.artwork);ui.removeCallbacks(hideOverlay);ui.postDelayed(hideOverlay,4500);}
    private void loadArtwork(String artworkUrl){logo.setImageDrawable(null);if(isBlank(artworkUrl)||!(artworkUrl.startsWith("http://")||artworkUrl.startsWith("https://")))return;final String expectedUrl=artworkUrl;worker.execute(()->{HttpURLConnection connection=null;try{connection=(HttpURLConnection)new URL(expectedUrl).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(7000);Bitmap bitmap=BitmapFactory.decodeStream(connection.getInputStream());if(bitmap!=null)ui.post(()->{if(!destroyed&&currentResolved!=null&&expectedUrl.equals(currentResolved.artwork))logo.setImageBitmap(bitmap);});}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}});}
    private void cycleAspect(){aspectMode=(aspectMode+1)%2;if(aspectMode==0){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);aspectButton.setText("FIT");}else{playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);aspectButton.setText("FILL");}}
    private void showTracks(int trackType,boolean allowOff){if(player==null)return;List<Tracks.Group> groups=new ArrayList<>();List<Integer> trackIndices=new ArrayList<>();List<String> labels=new ArrayList<>();if(allowOff)labels.add("Off");for(Tracks.Group group:player.getCurrentTracks().getGroups()){if(group.getType()!=trackType)continue;for(int i=0;i<group.length;i++){if(!group.isTrackSupported(i))continue;groups.add(group);trackIndices.add(i);Format format=group.getTrackFormat(i);String label=format.label;if(isBlank(label))label=format.language;if(isBlank(label)){int ordinal=labels.size()+(allowOff?0:1);label=(trackType==C.TRACK_TYPE_AUDIO?"Audio ":"Subtitle ")+ordinal;}labels.add(label);}}if(groups.isEmpty()){if(allowOff){labels.clear();labels.add("Off");}else{Toast.makeText(this,"Nuk ka audio tracks të tjera",Toast.LENGTH_SHORT).show();return;}}new AlertDialog.Builder(this).setTitle(trackType==C.TRACK_TYPE_AUDIO?"Audio":"Subtitles").setItems(labels.toArray(new String[0]),(dialog,which)->{if(player==null)return;TrackSelectionParameters.Builder parameters=player.getTrackSelectionParameters().buildUpon();if(allowOff&&which==0)parameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true);else{int index=allowOff?which-1:which;if(index<0||index>=groups.size())return;Tracks.Group group=groups.get(index);parameters.setTrackTypeDisabled(trackType,false);parameters.setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),trackIndices.get(index).intValue()));}player.setTrackSelectionParameters(parameters.build());}).show();}
    private void showControls(){if(destroyed)return;playerView.showController();overlay.setVisibility(View.VISIBLE);actions.setVisibility(View.VISIBLE);View focus=live&&prev.getVisibility()==View.VISIBLE?prev:aspectButton;focus.requestFocus();ui.removeCallbacks(hideOverlay);ui.postDelayed(hideOverlay,5000);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getAction()!=KeyEvent.ACTION_DOWN||event.getRepeatCount()>0)return super.dispatchKeyEvent(event);switch(event.getKeyCode()){case KeyEvent.KEYCODE_BACK:handleBack();return true;case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:if(actions.getVisibility()==View.VISIBLE||!usingVlc&&!usingIjk&&playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);showControls();return true;case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:setPlaybackRunning(!playbackRunning());return true;case KeyEvent.KEYCODE_MEDIA_PLAY:setPlaybackRunning(true);return true;case KeyEvent.KEYCODE_MEDIA_PAUSE:setPlaybackRunning(false);return true;case KeyEvent.KEYCODE_DPAD_LEFT:if(actions.getVisibility()==View.VISIBLE||!usingVlc&&!usingIjk&&playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);if(!live&&player!=null)seekPlayback(Math.max(0,playbackPosition()-10_000));showControls();return true;case KeyEvent.KEYCODE_MEDIA_REWIND:if(!live&&player!=null)seekPlayback(Math.max(0,playbackPosition()-10_000));return true;case KeyEvent.KEYCODE_DPAD_RIGHT:if(actions.getVisibility()==View.VISIBLE||!usingVlc&&!usingIjk&&playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);if(!live&&player!=null){long duration=playbackDuration(),target=playbackPosition()+10_000;if(duration>0)target=Math.min(duration,target);seekPlayback(Math.max(0,target));}showControls();return true;case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:if(!live&&player!=null){long duration=playbackDuration(),target=playbackPosition()+10_000;if(duration>0)target=Math.min(duration,target);seekPlayback(Math.max(0,target));}return true;case KeyEvent.KEYCODE_DPAD_UP:if(actions.getVisibility()==View.VISIBLE||!usingVlc&&!usingIjk&&playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);if(live)switchLive(-1);else showControls();return true;case KeyEvent.KEYCODE_DPAD_DOWN:if(actions.getVisibility()==View.VISIBLE||!usingVlc&&!usingIjk&&playerView.isControllerFullyVisible())return super.dispatchKeyEvent(event);if(live)switchLive(1);else showControls();return true;default:return super.dispatchKeyEvent(event);}}
    @Override public void onBackPressed(){handleBack();}
    private void handleBack(){if(actions.getVisibility()==View.VISIBLE||!usingVlc&&!usingIjk&&playerView.isControllerFullyVisible()){overlay.setVisibility(View.GONE);actions.setVisibility(View.GONE);playerView.hideController();immersive();}else super.onBackPressed();}
    @Override protected void onStart(){super.onStart();if(playerView!=null&&Build.VERSION.SDK_INT>23)playerView.onResume();}
    @Override protected void onResume(){super.onResume();foreground=true;immersive();if(playerView!=null&&Build.VERSION.SDK_INT<=23)playerView.onResume();if(resumeAfterPause){setPlaybackRunning(true);resumeAfterPause=false;}ui.removeCallbacks(saveProgress);ui.postDelayed(saveProgress,15000);}
    @Override protected void onPause(){ui.removeCallbacks(saveProgress);saveResume();resumeAfterPause=desiredPlayback||playbackRunning();foreground=false;setPlaybackRunning(false);if(playerView!=null&&Build.VERSION.SDK_INT<=23)playerView.onPause();super.onPause();}
    @Override protected void onStop(){saveResume();if(playerView!=null&&Build.VERSION.SDK_INT>23)playerView.onPause();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;selectionGeneration++;ui.removeCallbacksAndMessages(null);saveResume();releasePlayer();worker.shutdownNow();engine.close();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy();}
    private void saveResume(){if(live||player==null||isBlank(contentKey))return;long position=playbackPosition(),duration=playbackDuration();if(position<0)return;new PlaybackResumeStore(this).save(contentKey,position,duration);}
    private void releasePlayer(){if(ijkPlayer!=null){try{ijkPlayer.stop();ijkPlayer.release();}catch(Exception ignored){}ijkPlayer=null;}if(vlcPlayer!=null){try{vlcPlayer.stop();vlcPlayer.getVLCVout().detachViews();vlcPlayer.release();}catch(Exception ignored){}vlcPlayer=null;}if(vlc!=null){try{vlc.release();}catch(Exception ignored){}vlc=null;}if(player==null)return;playerView.setPlayer(null);player.release();player=null;httpFactory=null;}
    private String stableContentKey(StreamItem item){return PlaybackContentKey.of(playlistId,item);}
    private void immersive(){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private boolean isBlank(String value){return value==null||value.trim().isEmpty();}
    private String safe(String value){return value==null?"":value;}
    private String formatTime(long ms){if(ms<=0)return"--:--";long seconds=ms/1000;return String.format(Locale.US,"%d:%02d:%02d",seconds/3600,(seconds%3600)/60,seconds%60);}
}
