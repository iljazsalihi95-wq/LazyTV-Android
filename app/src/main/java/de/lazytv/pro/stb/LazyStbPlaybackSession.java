package de.lazytv.pro.stb;

import android.content.Context;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.stb.StbEnginePolicy.Engine;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import java.util.*;
import java.util.concurrent.*;

/** Independent STB session. Fullscreen only resizes the frame; engines change only on selection/failure. */
@UnstableApi
final class LazyStbPlaybackSession {
    interface Events {void loading();void ready();void error(String safeMessage);default void ended(){}}
    private final StbPortalClient portal;
    private final Events events;
    private final ExecutorService resolver=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final StbGeneration generation=new StbGeneration();
    private final StbEnginePolicy policy=new StbEnginePolicy();
    private final ExoPlayer player;
    private final PlayerView view;
    private final SurfaceView surface;
    private org.videolan.libvlc.LibVLC vlc;
    private org.videolan.libvlc.MediaPlayer vlcPlayer;
    private IjkMediaPlayer ijk;
    private android.media.MediaPlayer androidPlayer;
    private final DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(25000);
    private Player.Listener mediaListener;
    private Runnable watchdog;
    private StbProtocol.Channel channel;
    private Engine engine=Engine.AUTO,active;
    private long ticket,attempt;
    private String stream;
    private Map<String,String> headers=Collections.emptyMap();
    private boolean reloadAttempted,foreground=true,closed,prepared;

    LazyStbPlaybackSession(Context context,FrameLayout frame,StbPortalClient portal,Events events){
        this.portal=portal;this.events=events;
        view=new PlayerView(context);view.setUseController(false);view.setFocusable(false);frame.addView(view,new FrameLayout.LayoutParams(-1,-1));
        surface=new SurfaceView(context);surface.setVisibility(View.GONE);surface.setFocusable(false);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        player=new ExoPlayer.Builder(context).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(context,http))).build();view.setPlayer(player);
        surface.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder holder){if(closed)return;if(active==Engine.IJK&&ijk!=null)StbNativeCalls.run(()->ijk.setDisplay(holder));if(active==Engine.ANDROID&&androidPlayer!=null)StbNativeCalls.run(()->androidPlayer.setDisplay(holder));}
            public void surfaceChanged(SurfaceHolder h,int f,int w,int height){if(active==Engine.VLC&&vlcPlayer!=null)vlcPlayer.getVLCVout().setWindowSize(w,height);}
            public void surfaceDestroyed(SurfaceHolder holder){if(ijk!=null)StbNativeCalls.run(()->ijk.setDisplay(null));if(androidPlayer!=null)StbNativeCalls.run(()->androidPlayer.setDisplay(null));}
        });
        frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(vlcPlayer!=null&&active==Engine.VLC)vlcPlayer.getVLCVout().setWindowSize(r-l,b-t);});
    }
    void setEngine(Engine value){if(closed)return;engine=value;if(channel!=null)select(channel);}
    Engine engine(){return engine;}
    StbProtocol.Channel current(){return channel;}
    long ticket(){return ticket;}
    boolean accepts(long value){return generation.accepts(value);}
    private boolean current(long selected,long engineAttempt){return generation.accepts(selected)&&attempt==engineAttempt;}
    void select(StbProtocol.Channel next){
        if(closed||next==null)return;cancelWatchdog();ticket=generation.next();attempt++;stopEngines();active=null;channel=next;reloadAttempted=false;stream=null;policy.reset(engine);events.loading();resolve(ticket,false);
    }
    private void resolve(long selected,boolean reload){
        StbProtocol.Channel item=channel;
        resolver.execute(()->{try{String url=portal.resolve(item);Map<String,String> h=portal.playbackHeaders(url);ui.post(()->{if(!generation.accepts(selected))return;stream=url;headers=h;if(reload&&active!=null)start(selected,active);else next(selected);});}catch(Exception e){ui.post(()->{if(generation.accepts(selected))events.error("create_link: "+StbErrors.message(e));});}});
    }
    private void next(long selected){
        if(!generation.accepts(selected))return;
        Engine next=policy.next();
        if(next==null){cancelWatchdog();attempt++;stopEngines();events.error(engine==Engine.AUTO?"STB: Media3, VLC, IJK dhe Android MediaPlayer nuk e hapën stream-in. Kontrollo linkun/sesionin e portalit.":"STB: engine i zgjedhur nuk e hapi stream-in. Provo Auto ose engine tjetër.");return;}
        start(selected,next);
    }
    private void start(long selected,Engine selectedEngine){
        cancelWatchdog();long engineAttempt=++attempt;stopEngines();active=selectedEngine;prepared=false;
        view.setVisibility(active==Engine.MEDIA3?View.VISIBLE:View.GONE);surface.setVisibility(active==Engine.MEDIA3?View.GONE:View.VISIBLE);events.loading();
        try{
            switch(active){case MEDIA3:playMedia3(selected,engineAttempt);break;case VLC:playVlc(selected,engineAttempt);break;case IJK:playIjk(selected,engineAttempt);break;case ANDROID:playAndroid(selected,engineAttempt);break;default:throw new IllegalStateException();}
            watch(selected,engineAttempt);
        }catch(Exception|LinkageError unavailable){ui.post(()->failed(selected,engineAttempt,false));}
    }
    private void playMedia3(long selected,long engineAttempt){
        if(mediaListener!=null)player.removeListener(mediaListener);
        mediaListener=new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){if(!current(selected,engineAttempt))return;if(state==Player.STATE_READY)ready(selected,engineAttempt);else if(state==Player.STATE_BUFFERING){events.loading();watch(selected,engineAttempt);}else if(state==Player.STATE_ENDED)completed(selected,engineAttempt);}
            @Override public void onPlayerError(PlaybackException error){boolean expired=false;for(Throwable c=error;c!=null;c=c.getCause())if(c instanceof HttpDataSource.InvalidResponseCodeException){int code=((HttpDataSource.InvalidResponseCodeException)c).responseCode;expired=code==401||code==403;}failed(selected,engineAttempt,expired);}
        };player.addListener(mediaListener);http.setDefaultRequestProperties(headers);
        player.setMediaItem(new MediaItem.Builder().setUri(stream).setMediaId(selected+":"+engineAttempt).build());player.prepare();player.setPlayWhenReady(foreground);
    }
    private void playVlc(long selected,long engineAttempt){
        if(vlc==null){ArrayList<String> options=new ArrayList<>();options.add("--quiet");vlc=new org.videolan.libvlc.LibVLC(view.getContext(),options);}
        if(vlcPlayer==null)vlcPlayer=new org.videolan.libvlc.MediaPlayer(vlc);
        vlcPlayer.getVLCVout().setVideoView(surface);vlcPlayer.getVLCVout().attachViews();vlcPlayer.getVLCVout().setWindowSize(surface.getWidth(),surface.getHeight());
        org.videolan.libvlc.Media media=new org.videolan.libvlc.Media(vlc,Uri.parse(stream));media.addOption(":network-caching=1500");media.addOption(":http-user-agent="+headers.get("User-Agent"));
        if(headers.containsKey("Referer"))media.addOption(":http-referrer="+headers.get("Referer"));
        vlcPlayer.setMedia(media);media.release();
        vlcPlayer.setEventListener(event->ui.post(()->{if(!current(selected,engineAttempt))return;if(event.type==org.videolan.libvlc.MediaPlayer.Event.Playing){if(!foreground)vlcPlayer.pause();ready(selected,engineAttempt);}else if(event.type==org.videolan.libvlc.MediaPlayer.Event.EncounteredError)failed(selected,engineAttempt,false);else if(event.type==org.videolan.libvlc.MediaPlayer.Event.EndReached)completed(selected,engineAttempt);}));vlcPlayer.play();
    }
    private void playIjk(long selected,long engineAttempt)throws Exception {
        if(ijk==null){ijk=new IjkMediaPlayer();IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT);}
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER,"mediacodec",0);ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT,"timeout",15000000L);ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT,"reconnect",0);
        StringBuilder raw=new StringBuilder();for(Map.Entry<String,String> h:headers.entrySet())if(h.getKey().matches("[A-Za-z0-9-]+")&&h.getValue()!=null&&!h.getValue().contains("\r")&&!h.getValue().contains("\n"))raw.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT,"headers",raw.toString());ijk.setDisplay(surface.getHolder());ijk.setDataSource(stream);
        ijk.setOnPreparedListener(mp->ui.post(()->{if(!current(selected,engineAttempt))return;if(foreground)mp.start();ready(selected,engineAttempt);}));
        ijk.setOnErrorListener((mp,what,extra)->{ui.post(()->failed(selected,engineAttempt,false));return true;});ijk.setOnCompletionListener(mp->ui.post(()->completed(selected,engineAttempt)));
        ijk.setOnInfoListener((mp,what,extra)->{if(what==701)ui.post(()->{if(current(selected,engineAttempt)){events.loading();watch(selected,engineAttempt);}});else if(what==702)ui.post(()->ready(selected,engineAttempt));return false;});ijk.prepareAsync();
    }
    private void playAndroid(long selected,long engineAttempt)throws Exception {
        if(androidPlayer==null)androidPlayer=new android.media.MediaPlayer();
        androidPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);androidPlayer.setDisplay(surface.getHolder());androidPlayer.setDataSource(view.getContext(),Uri.parse(stream),headers);
        androidPlayer.setOnPreparedListener(mp->{if(!current(selected,engineAttempt))return;if(foreground)mp.start();ready(selected,engineAttempt);});
        androidPlayer.setOnErrorListener((mp,what,extra)->{ui.post(()->failed(selected,engineAttempt,false));return true;});androidPlayer.setOnCompletionListener(mp->ui.post(()->completed(selected,engineAttempt)));
        androidPlayer.setOnInfoListener((mp,what,extra)->{if(!current(selected,engineAttempt))return false;if(what==701){events.loading();watch(selected,engineAttempt);}else if(what==702)ready(selected,engineAttempt);return false;});androidPlayer.prepareAsync();
    }
    private void completed(long selected,long engineAttempt){if(!current(selected,engineAttempt))return;if(channel!=null&&!channel.linear()){cancelWatchdog();events.ended();}else failed(selected,engineAttempt,false);}
    private void ready(long selected,long engineAttempt){if(!current(selected,engineAttempt))return;prepared=true;cancelWatchdog();events.ready();}
    private void failed(long selected,long engineAttempt,boolean expired){
        if(!current(selected,engineAttempt))return;cancelWatchdog();attempt++;stopEngines();
        if(expired&&!reloadAttempted){reloadAttempted=true;events.loading();resolve(selected,true);}else next(selected);
    }
    private void stopEngines(){prepared=false;StbNativeCalls.run(player::stop);try{if(vlcPlayer!=null){vlcPlayer.setEventListener(null);vlcPlayer.stop();vlcPlayer.getVLCVout().detachViews();}}catch(Exception|LinkageError ignored){}try{if(ijk!=null)ijk.reset();}catch(Exception|LinkageError ignored){}try{if(androidPlayer!=null)androidPlayer.reset();}catch(Exception|LinkageError ignored){}}
    private void cancelWatchdog(){if(watchdog!=null){ui.removeCallbacks(watchdog);watchdog=null;}}
    private void watch(long selected,long engineAttempt){cancelWatchdog();watchdog=()->{if(foreground)failed(selected,engineAttempt,false);};ui.postDelayed(watchdog,25000);}
    void pause(){if(closed)return;foreground=false;cancelWatchdog();player.pause();try{if(prepared&&active==Engine.VLC&&vlcPlayer!=null)vlcPlayer.pause();if(prepared&&active==Engine.IJK&&ijk!=null)ijk.pause();if(prepared&&active==Engine.ANDROID&&androidPlayer!=null)androidPlayer.pause();}catch(Exception|LinkageError ignored){}}
    void resume(){if(closed)return;foreground=true;if(channel==null)return;try{if(active==Engine.MEDIA3)player.play();else if(prepared&&active==Engine.VLC&&vlcPlayer!=null)vlcPlayer.play();else if(prepared&&active==Engine.IJK&&ijk!=null)ijk.start();else if(prepared&&active==Engine.ANDROID&&androidPlayer!=null)androidPlayer.start();if(active!=null&&!prepared)watch(ticket,attempt);}catch(Exception|LinkageError ignored){}}
    void close(){if(closed)return;closed=true;generation.close();attempt++;resolver.shutdownNow();ui.removeCallbacksAndMessages(null);stopEngines();view.setPlayer(null);StbNativeCalls.run(player::release);if(vlcPlayer!=null)StbNativeCalls.run(vlcPlayer::release);if(vlc!=null)StbNativeCalls.run(vlc::release);if(ijk!=null)StbNativeCalls.run(ijk::release);if(androidPlayer!=null)StbNativeCalls.run(androidPlayer::release);}
}
