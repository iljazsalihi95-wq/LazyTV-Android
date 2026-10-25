package de.lazytv.pro.live;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.view.KeyEvent;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.R;
import de.lazytv.pro.playlist.*;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Exercise real entry points, not only the screen inside a bare Activity. */
@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = {25, 28},
    application = android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ProviderActivityEntryTest {
  @Test
  @Config(qualifiers = "land")
  public void landscapeEntries() throws Exception {
    entries();
  }

  @Test
  @Config(qualifiers = "port")
  public void portraitEntries() throws Exception {
    entries();
  }

  private void entries() throws Exception {
    android.content.Context context = RuntimeEnvironment.getApplication();
    PlaylistStorage storage = new PlaylistStorage(context);
    storage.save(
        new Playlist(
            "entry-m3u",
            "Fixture",
            PlaylistType.M3U_URL,
            "http://127.0.0.1:1/fixture.m3u",
            "",
            "",
            "",
            1));
    storage.save(
        new Playlist(
            "entry-xtream",
            "Fixture",
            PlaylistType.XTREAM_CODES,
            "http://127.0.0.1:1",
            "fixture",
            "fixture",
            "",
            1));
    for (Class<? extends Activity> type :
        new Class[] {
          PremiumLivePlayerActivity.class, LiveTvActivity.class,
          XtreamLivePlayerActivity.class, IptvLivePlayerActivity.class
        }) {
      Intent intent = new Intent(context, type);
      intent.putExtra(
          "playlist_id", type == XtreamLivePlayerActivity.class ? "entry-xtream" : "entry-m3u");
      if (type == LiveTvActivity.class) {
        intent.putExtra("source_scope", "MANAGED_BACKEND");
        intent.putExtra("server_name", "SERVER_10");
      }
      ActivityController<? extends Activity> controller = Robolectric.buildActivity(type, intent);
      Activity activity = controller.create().start().resume().visible().get();
      try {
        PlayerView preview = activity.findViewById(R.id.live_player);
        assertNotNull(type.getSimpleName(), preview);
        Object player = preview.getPlayer();
        assertNotNull(player);
        preview.requestFocus();
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(activity.isFinishing());
        preview.performClick();
        Field field = type.getDeclaredField("screen");
        field.setAccessible(true);
        NativeLiveScreen screen = (NativeLiveScreen) field.get(activity);
        Field hostField = NativeLiveScreen.class.getDeclaredField("fullscreenHost");
        hostField.setAccessible(true);
        ReferenceFullscreenHost host = (ReferenceFullscreenHost) hostField.get(screen);
        assertSame(player, host.activeView().getPlayer());
        assertNull(preview.getPlayer());
        // HOME is delivered by Android as pause/stop; don't pretend to inject a system HOME key.
        controller.pause().stop().start().resume();
        assertSame(player, host.activeView().getPlayer());
        activity.onBackPressed();
        activity.onBackPressed();
        assertFalse(activity.isFinishing());
        assertSame(player, preview.getPlayer());
      } finally {
        controller.pause().stop().destroy();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
      }
    }
  }

  @Test
  public void managedPremiumKeepsItsBackendCatalogIdentity() throws Exception {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    activity.setIntent(
        new Intent()
            .putExtra("source_scope", "MANAGED_BACKEND")
            .putExtra("server_name", "SERVER_10"));
    PremiumModule module = new PremiumModule(activity);
    try {
      assertTrue(module.source() instanceof LiveSources.Managed);
      assertEquals("MANAGED_BACKEND|SERVER_10", module.identity());
      assertTrue(module.managed());
      assertTrue(module.source().requiresFreshLinkOn458());
      assertFalse(new LiveSources.Managed(activity, "SERVER_20").requiresFreshLinkOn458());
    } finally {
      module.source().close();
      activity.finish();
    }
  }
}
