package de.lazytv.pro.live;
import android.view.*;import android.widget.FrameLayout;import androidx.media3.ui.PlayerView;import de.lazytv.pro.catalog.ResolvedStream;import java.util.Map;import tv.danmaku.ijk.media.player.IjkMediaPlayer;
/** One persistent native decoder/surface; ownership stays with the Live session. */
final class LiveNativeEngine {
 interface Events {void ready();void buffering(boolean value);void failed();}
 private final PlayerView frame;private final SurfaceView surface;private final IjkMediaPlayer player;private boolean released,wantsPlay;private int width,height;
 LiveNativeEngine(PlayerView frame){this.frame=frame;surface=new SurfaceView(frame.getContext());surface.setClickable(false);surface.setFocusable(false);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));player=new IjkMediaPlayer();surface.getHolder().addCallback(new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder holder){if(!released)player.setDisplay(holder);}public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){resize();}public void surfaceDestroyed(SurfaceHolder holder){if(!released)player.setDisplay(null);}});frame.addOnLayoutChangeListener((v,a,b,c,d,e,f,g,h)->resize());surface.setVisibility(View.GONE);}
 void play(ResolvedStream stream,boolean foreground,Events events)throws Exception {
  player.reset();wantsPlay=foreground;surface.setVisibility(View.VISIBLE);width=height=0;
  player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER,"mediacodec",0);player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT,"timeout",15000000L);player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT,"reconnect",0);
  StringBuilder headers=new StringBuilder();for(Map.Entry<String,String> header:stream.headers.entrySet())if(header.getKey()!=null&&header.getValue()!=null&&!header.getValue().contains("\r")&&!header.getValue().contains("\n"))headers.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
  player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT,"headers",headers.toString());player.setDisplay(surface.getHolder());player.setDataSource(stream.url);
  player.setOnPreparedListener(mp->{if(released)return;if(wantsPlay)mp.start();events.ready();});
  player.setOnVideoSizeChangedListener((mp,w,h,sarN,sarD)->{width=sarD>0?(int)((long)w*Math.max(1,sarN)/sarD):w;height=h;frame.post(this::resize);});
  player.setOnErrorListener((mp,what,extra)->{events.failed();return true;});player.setOnCompletionListener(mp->events.failed());
  player.setOnInfoListener((mp,what,extra)->{if(what==701)events.buffering(true);else if(what==702)events.buffering(false);return false;});player.prepareAsync();
 }
 private void resize(){if(released||width<=0||height<=0||frame.getWidth()<=0||frame.getHeight()<=0)return;float ratio=Math.min((float)frame.getWidth()/width,(float)frame.getHeight()/height);FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(Math.max(1,Math.round(width*ratio)),Math.max(1,Math.round(height*ratio)),Gravity.CENTER);android.view.ViewGroup.LayoutParams previous=surface.getLayoutParams();if(previous.width!=params.width||previous.height!=params.height)surface.setLayoutParams(params);}
 void pause(){wantsPlay=false;try{player.pause();}catch(Exception preparing){}}
 void resume(){wantsPlay=true;try{player.start();}catch(Exception preparing){}}
 void stop(){wantsPlay=false;try{player.reset();}catch(Exception ignored){}surface.setVisibility(View.GONE);}
 boolean playing(){try{return player.isPlaying();}catch(Exception preparing){return false;}}
 void release(){released=true;player.release();frame.removeView(surface);}
}
