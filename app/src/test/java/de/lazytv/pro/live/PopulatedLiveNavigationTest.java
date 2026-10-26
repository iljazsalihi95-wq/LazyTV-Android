package de.lazytv.pro.live;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.R;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.Playlist;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Render and navigate populated rows, including posted selection and error callbacks. */
@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = {25, 28, 30},
    application = android.app.Application.class,
    qualifiers = "land")
@LooperMode(LooperMode.Mode.PAUSED)
public class PopulatedLiveNavigationTest {
  private Activity activity;
  private NativeLiveScreen screen;

  private Object field(String name) throws Exception {
    Field f = NativeLiveScreen.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(screen);
  }

  private void pump() throws Exception {
    Thread.sleep(10);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS);
  }

  private void layout() {
    View root = activity.findViewById(android.R.id.content);
    root.measure(
        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 1920, 1080);
  }

  @After
  public void close() {
    if (screen != null) screen.close();
    if (activity != null) activity.finish();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void populatedRowsOkChannelChangesRotationAndBack() throws Exception {
    activity = Robolectric.buildActivity(Activity.class).setup().get();
    for (int resource :
        new int[] {
          R.layout.activity_premium_player,
          R.layout.activity_servers_player,
          R.layout.activity_xtream_player,
          R.layout.activity_m3u_player
        }) {
      Catalog catalog = new Catalog();
      for (int group = 0; group < 3; group++) {
        String category = "category" + group;
        catalog.categories.add(new Category(category, "AL | Group " + group, CatalogType.LIVE));
        for (int channel = 0; channel < 5; channel++)
          catalog.items.add(
              new StreamItem(
                  group + "-" + channel,
                  "Fixture " + channel,
                  category,
                  "",
                  "http://127.0.0.1:1/fixture.ts",
                  "",
                  CatalogType.LIVE));
      }
      screen =
          new NativeLiveScreen(
              activity,
              new LiveModule() {
                public Playlist playlist() {
                  return null;
                }

                public String identity() {
                  return "fixture";
                }

                public int layout() {
                  return resource;
                }

                public void openCatalog(Activity a, String type) {}

                public LiveSources.Source source() {
                  return new LiveSources.Source() {
                    public Catalog load() {
                      return catalog;
                    }

                    public ResolvedStream resolve(StreamItem i, boolean refresh) {
                      return new ResolvedStream(i.streamUrl, "ts", i.name, "", "", null, null);
                    }
                  };
                }
              });
      screen.create();
      ListView channels = activity.findViewById(R.id.live_channels);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while ((channels.getAdapter() == null || channels.getAdapter().getCount() == 0)
          && System.nanoTime() < deadline) pump();
      assertEquals(5, channels.getAdapter().getCount());
      layout();
      PlayerView preview = activity.findViewById(R.id.live_player);
      Object player = preview.getPlayer();
      for (int category = 0; category < 3; category++) {
        ListView categories = activity.findViewById(R.id.live_categories);
        categories.performItemClick(
            categories.getAdapter().getView(category, null, categories), category, category);
        layout();
        channels = activity.findViewById(R.id.live_channels);
        channels.requestFocus();
        channels.setSelection(0);
        layout();
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
          KeyEvent key = new KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER);
          if (!screen.key(key)) activity.dispatchKeyEvent(key);
        }
        channels.performItemClick(channels.getAdapter().getView(0, null, channels), 0, 0);
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (field("current") == null && System.nanoTime() < deadline) pump();
        assertNotNull(field("current"));
        for (int key : new int[] {KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS}) {
          assertTrue(screen.key(new KeyEvent(KeyEvent.ACTION_DOWN, key)));
          pump();
        }
        assertFalse(activity.isFinishing());
      }
      preview.performClick();
      screen.stop();
      screen.start();
      RuntimeEnvironment.setQualifiers("port");
      screen.configuration(new Configuration(activity.getResources().getConfiguration()));
      layout();
      ReferenceFullscreenHost host = (ReferenceFullscreenHost) field("fullscreenHost");
      assertNotNull(host.activeView().getPlayer());
      assertNotSame(player, host.activeView().getPlayer());
      screen.back();
      screen.back();
      assertFalse(activity.isFinishing());
      RuntimeEnvironment.setQualifiers("land");
      screen.close();
      screen = null;
    }
  }
}
