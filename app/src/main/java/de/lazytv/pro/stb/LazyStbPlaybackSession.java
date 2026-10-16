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
import java.util.*;
import java.util.concurrent.*;

/** Lazy STB alone owns this player. Resizing its existing frame never resolves/recreates playback. */
@UnstableApi
final class LazyStbPlaybackSession {
    enum Engine { AUTO, MEDIA3, VLC }
    interface Events {void loading();void ready();void error(String safeMessage);}
    private final StbPortalClient portal;
    private final Events events;
    private final ExecutorService resolver=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final StbGeneration generation=new StbGeneration();
    private final ExoPlayer player;
    private final PlayerView view;
    private final SurfaceView vlcSurface;
    private org.videolan.libvlc.LibVLC vlc;
    private org.videolan.libvlc.MediaPlayer vlcPlayer;
    private final DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(25000);
    private Player.Listener mediaListener;
    private Runnable watchdog;
    private StbProtocol.Channel channel;
    private Engine engine=Engine.AUTO;
    private long ticket;
    private String stream;
    private Map<String,String> headers=Collections.emptyMap();
    private boolean nativePlaying,vlcAttempted,reloadAttempted,foreground=true,closed;

    LazyStbPlaybackSession(Context context,FrameLayout frame,StbPortalClient portal,Events events){
        this.portal=portal;this.events=events;
        view=new PlayerView(context);view.setUseController(false);view.setFocusable(false);frame.addView(view,new FrameLayout.LayoutParams(-1,-1));
        vlcSurface=new SurfaceView(context);vlcSurface.setVisibility(View.GONE);vlcSurface.setFocusable(false);frame.addView(vlcSurface,new FrameLayout.LayoutParams(-1,-1));
        player=new ExoPlayer.Builder(context).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(context,http))).build();view.setPlayer(player);
        frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(vlcPlayer!=null&&nativePlaying)vlcPlayer.getVLCVout().setWindowSize(r-l,b-t);});
    }
    void setEngine(Engine value){engine=value;if(channel!=null)select(channel);}
    Engine engine(){return engine;}
    StbProtocol.Channel current(){return channel;}
    long ticket(){return ticket;}
    boolean accepts(long value){return generation.accepts(value);}
    void select(StbProtocol.Channel next){
        cancelWatchdog();ticket=generation.next();channel=next;reloadAttempted=false;vlcAttempted=false;stream=null;
        player.stop();if(vlcPlayer!=null)vlcPlayer.stop();events.loading();resolve(ticket);
    }
    private void resolve(long selected){
        StbProtocol.Channel item=channel;
        resolver.execute(()->{try{String url=portal.resolve(item);Map<String,String> h=portal.playbackHeaders(url);ui.post(()->{if(!generation.accepts(selected))return;stream=url;headers=h;play(selected);});}catch(Exception e){ui.post(()->{if(generation.accepts(selected))events.error("create_link: "+StbErrors.message(e));});}});
    }
    private void play(long selected){
        if(engine==Engine.VLC){playVlc(selected);return;}
        nativePlaying=false;if(vlcPlayer!=null)vlcPlayer.stop();vlcSurface.setVisibility(View.GONE);view.setVisibility(View.VISIBLE);
        if(mediaListener!=null)player.removeListener(mediaListener);
        mediaListener=new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){if(!generation.accepts(selected)||nativePlaying)return;if(state==Player.STATE_READY){cancelWatchdog();events.ready();}else if(state==Player.STATE_BUFFERING){events.loading();watch(selected,false);}else if(state==Player.STATE_ENDED)failed(selected,false);}
            @Override public void onPlayerError(PlaybackException error){if(!generation.accepts(selected)||nativePlaying)return;boolean expired=false;for(Throwable c=error;c!=null;c=c.getCause())if(c instanceof HttpDataSource.InvalidResponseCodeException){int code=((HttpDataSource.InvalidResponseCodeException)c).responseCode;expired=code==401||code==403;}failed(selected,expired);}
        };player.addListener(mediaListener);
        http.setDefaultRequestProperties(headers);
        player.setMediaItem(new MediaItem.Builder().setUri(stream).setMediaId(String.valueOf(selected)).build());player.prepare();player.setPlayWhenReady(foreground);watch(selected,false);
    }
    private void failed(long selected,boolean expired){
        if(!generation.accepts(selected))return;
        cancelWatchdog();
        if(expired&&!reloadAttempted){reloadAttempted=true;events.loading();resolve(selected);return;}
        if(engine==Engine.AUTO&&!vlcAttempted){playVlc(selected);return;}
        events.error("Kanali nuk u hap me engine të zgjedhur. Provo engine tjetër ose kanalin përsëri.");
    }
    private void playVlc(long selected){
        if(!generation.accepts(selected))return;
        cancelWatchdog();vlcAttempted=true;nativePlaying=true;player.stop();view.setVisibility(View.GONE);vlcSurface.setVisibility(View.VISIBLE);
        try{
            if(vlc==null){ArrayList<String> options=new ArrayList<>();options.add("--quiet");vlc=new org.videolan.libvlc.LibVLC(view.getContext(),options);}
            if(vlcPlayer==null)vlcPlayer=new org.videolan.libvlc.MediaPlayer(vlc);else vlcPlayer.stop();
            vlcPlayer.getVLCVout().detachViews();vlcPlayer.getVLCVout().setVideoView(vlcSurface);vlcPlayer.getVLCVout().attachViews();vlcPlayer.getVLCVout().setWindowSize(vlcSurface.getWidth(),vlcSurface.getHeight());
            org.videolan.libvlc.Media media=new org.videolan.libvlc.Media(vlc,Uri.parse(stream));media.addOption(":network-caching=1500");media.addOption(":http-user-agent="+headers.get("User-Agent"));
            if(headers.containsKey("Referer"))media.addOption(":http-referrer="+headers.get("Referer"));
            vlcPlayer.setMedia(media);media.release();
            vlcPlayer.setEventListener(event->ui.post(()->{if(!generation.accepts(selected)||!nativePlaying)return;if(event.type==org.videolan.libvlc.MediaPlayer.Event.Playing){cancelWatchdog();if(!foreground)vlcPlayer.pause();events.ready();}else if(event.type==org.videolan.libvlc.MediaPlayer.Event.EncounteredError||event.type==org.videolan.libvlc.MediaPlayer.Event.EndReached)events.error("VLC nuk e hapi këtë stream STB");}));
            vlcPlayer.play();watch(selected,true);
        }catch(Exception|LinkageError e){events.error("VLC nuk është i disponueshëm për këtë stream/pajisje");}
    }
    private void cancelWatchdog(){if(watchdog!=null){ui.removeCallbacks(watchdog);watchdog=null;}}
    private void watch(long selected,boolean nativeEngine){cancelWatchdog();watchdog=()->{if(!generation.accepts(selected)||!foreground)return;if(nativeEngine){events.error("VLC: stream-i nuk u hap brenda afatit");}else failed(selected,false);};ui.postDelayed(watchdog,25000);}
    void pause(){foreground=false;cancelWatchdog();player.pause();if(vlcPlayer!=null&&nativePlaying)vlcPlayer.pause();}
    void resume(){foreground=true;if(channel!=null){if(nativePlaying&&vlcPlayer!=null)vlcPlayer.play();else{player.play();if(player.getPlaybackState()==Player.STATE_BUFFERING)watch(ticket,false);}}}
    void close(){if(closed)return;closed=true;generation.close();resolver.shutdownNow();ui.removeCallbacksAndMessages(null);view.setPlayer(null);player.release();if(vlcPlayer!=null){vlcPlayer.stop();vlcPlayer.getVLCVout().detachViews();vlcPlayer.release();}if(vlc!=null)vlc.release();}
}
