package de.lazytv.pro.live;

import android.content.Intent;
import android.view.KeyEvent;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.R;
import de.lazytv.pro.playlist.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={25,28,30},application=android.app.Application.class,qualifiers="land")
@LooperMode(LooperMode.Mode.PAUSED)
public class StalkerScreenLifecycleTest {
 @Test public void nativeFullscreenAndHomeOwnOnlyOnePlayer() {
  android.content.Context c=RuntimeEnvironment.getApplication();
  new PlaylistStorage(c).save(new Playlist("stb-fixture","STB",PlaylistType.STALKER_PORTAL,"http://127.0.0.1:1","","","00:11:22:33:44:55",1));
  var controller=Robolectric.buildActivity(StalkerLivePlayerActivity.class,new Intent(c,StalkerLivePlayerActivity.class).putExtra("playlist_id","stb-fixture"));
  var activity=controller.create().start().resume().visible().get();
  try {
   if(!de.lazytv.pro.BuildConfig.DEBUG){assertTrue(activity.isFinishing());return;}
   PlayerView preview=activity.findViewById(R.id.live_player);Object player=preview.getPlayer();assertNotNull(player);
   preview.performClick();assertNull(preview.getPlayer());
   assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_NUMPAD_ENTER)));
   assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_NUMPAD_ENTER)));
   assertSame(player,preview.getPlayer());
   controller.pause().stop();assertNull(preview.getPlayer());
   controller.start().resume();assertNotNull(preview.getPlayer());assertNotSame(player,preview.getPlayer());
  } finally {controller.pause().stop().destroy();}
 }
}
