package de.lazytv.pro.live;
import android.app.Activity;
import android.os.*;
import android.content.res.Configuration;
import android.view.*;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.R;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.lang.reflect.Field;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={25,28,30},application=android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeScreenTest {
 private Activity activity;private NativeLiveScreen screen;
 private LiveModule fixture(int layout){return new LiveModule(){
  public Playlist playlist(){return null;}public String identity(){return "fixture";}public int layout(){return layout;}
  public void openCatalog(Activity a,String type){}
  public LiveSources.Source source(){return new LiveSources.Source(){public Catalog load(){return new Catalog();}public ResolvedStream resolve(StreamItem i,boolean refresh){return new ResolvedStream("http://127.0.0.1:1/live.ts","ts","Fixture","","",null,null);}};}
 };}
 @After public void cleanup(){if(screen!=null)screen.close();if(activity!=null)activity.finish();Shadows.shadowOf(Looper.getMainLooper()).idle();}
 private Object field(String name)throws Exception{Field f=NativeLiveScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
 @Test public void providerEntriesAreIndependentActivities(){for(Class<?> c:new Class<?>[]{PremiumLivePlayerActivity.class,XtreamLivePlayerActivity.class,IptvLivePlayerActivity.class})assertEquals(Activity.class,c.getSuperclass());}
 @Test @Config(qualifiers="land") public void previewFullscreenBackRetainsOnePlayerAcrossLayouts()throws Exception{
  activity=Robolectric.buildActivity(Activity.class).setup().get();
  for(int layout:new int[]{R.layout.activity_premium_player,R.layout.activity_xtream_player,R.layout.activity_m3u_player,R.layout.activity_servers_player}){
   screen=new NativeLiveScreen(activity,fixture(layout));screen.create();PlayerView preview=activity.findViewById(R.id.live_player);Object player=preview.getPlayer();assertNotNull(player);
   assertNotNull(activity.findViewById(R.id.live_record));assertNotNull(activity.findViewById(R.id.live_fullscreen));
   assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,preview.getResizeMode());
   activity.findViewById(R.id.live_fit).performClick();assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,preview.getResizeMode());
   assertTrue((activity.getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)!=0);
   preview.performClick();ReferenceFullscreenHost host=(ReferenceFullscreenHost)field("fullscreenHost");
   assertSame(player,host.activeView().getPlayer());assertNull(preview.getPlayer());assertFalse(activity.isFinishing());
   assertTrue(screen.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)));
   assertTrue(screen.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)));
   assertTrue(screen.back());assertTrue(screen.back());assertSame(player,preview.getPlayer());assertNull(host.fullscreenView().getPlayer());
   screen.stop();assertNull(preview.getPlayer());assertNull(host.fullscreenView().getPlayer());screen.start();assertNotNull(preview.getPlayer());assertNotSame(player,preview.getPlayer());screen.close();screen=null;
  }
 }
 @Test public void configurationRebindKeepsSessionAndPlayer()throws Exception{
  activity=Robolectric.buildActivity(Activity.class).setup().get();screen=new NativeLiveScreen(activity,fixture(R.layout.activity_m3u_player));screen.create();
  PlayerView old=activity.findViewById(R.id.live_player);Object player=old.getPlayer();old.performClick();
  screen.configuration(new Configuration(activity.getResources().getConfiguration()));ReferenceFullscreenHost host=(ReferenceFullscreenHost)field("fullscreenHost");
  assertSame(player,host.activeView().getPlayer());assertNull(old.getPlayer());assertFalse(activity.isFinishing());
 }
}
