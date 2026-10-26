package de.lazytv.pro;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.UiDevice;
import de.lazytv.pro.live.*;
import de.lazytv.pro.playlist.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android TV process, window manager, SurfaceView and Media3 initialization; no Robolectric. */
@RunWith(AndroidJUnit4.class)
public class TvEntryDeviceTest {
  private Activity resumed(Class<?> expected) {
    long until=SystemClock.uptimeMillis()+10000;
    while(SystemClock.uptimeMillis()<until){
      AtomicReference<Activity> found=new AtomicReference<>();
      InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
        for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
          if(expected.isInstance(a))found.set(a);
      });
      if(found.get()!=null)return found.get();
      SystemClock.sleep(100);
    }
    throw new AssertionError("Expected resumed screen: "+expected.getSimpleName());
  }

  @Test public void homeRemoteOpensPremiumAndReturnsWithoutProcessExit() {
    UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    PlaylistStorage storage=new PlaylistStorage(context);
    storage.save(new Playlist("home-fixture","Fixture",PlaylistType.M3U_URL,"http://127.0.0.1:1/fixture.m3u","","","",1));storage.setActive("home-fixture");
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      resumed(MainActivity.class);
      device.pressDPadRight();device.pressDPadCenter();
      Activity premium=resumed(PremiumLivePlayerActivity.class);
      InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
        PlayerView preview=premium.findViewById(R.id.live_player);
        assertNotNull(preview);assertNotNull("Real Media3 engine must initialize",preview.getPlayer());
        preview.performClick();
      });
      device.pressBack();device.pressBack();resumed(MainActivity.class);
      for(String label:new String[]{"MOVIES","SERIES"}){
        var tile=device.findObject(androidx.test.uiautomator.By.text(label));assertNotNull(label,tile);tile.click();
        resumed(de.lazytv.pro.catalog.CatalogActivity.class);device.pressBack();resumed(MainActivity.class);
      }
      device.pressHome();SystemClock.sleep(500);
      // Restore the same task using its launcher, matching a Fire TV HOME -> app transition.
      Intent launch=context.getPackageManager().getLeanbackLaunchIntentForPackage(context.getPackageName());
      assertNotNull(launch);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch);
      SystemClock.sleep(500);
      assertNotNull(resumed(MainActivity.class));
    }
  }

  @Test public void personalProviderEntriesCreateActualNativeViews() {
    var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    PlaylistStorage storage=new PlaylistStorage(context);
    storage.save(new Playlist("device-m3u","Fixture",PlaylistType.M3U_URL,"http://127.0.0.1:1/fixture.m3u","","","",1));
    storage.save(new Playlist("device-xtream","Fixture",PlaylistType.XTREAM_CODES,"http://127.0.0.1:1","fixture","fixture","",1));
    storage.save(new Playlist("device-stb","Fixture",PlaylistType.STALKER_PORTAL,"http://127.0.0.1:1","","","00:11:22:33:44:55",1));
    Class<?>[] types={IptvLivePlayerActivity.class,XtreamLivePlayerActivity.class,StalkerLivePlayerActivity.class,LiveTvActivity.class};
    for(Class<?> type:types){
      Intent intent=new Intent(context,type).putExtra("playlist_id",type==XtreamLivePlayerActivity.class?"device-xtream":type==StalkerLivePlayerActivity.class?"device-stb":"device-m3u");
      if(type==LiveTvActivity.class)intent.putExtra("source_scope","MANAGED_BACKEND").putExtra("server_name","SERVER_20");
      try(ActivityScenario<Activity> scenario=ActivityScenario.launch(intent)){
        resumed(type);
        scenario.onActivity(a->{PlayerView view=a.findViewById(R.id.live_player);assertNotNull(view);assertNotNull(view.getPlayer());view.performClick();});
      }
    }
  }
}
