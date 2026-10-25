package de.lazytv.pro.live;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.media3.common.util.UnstableApi;
import de.lazytv.pro.activation.ActivationGuard;
import de.lazytv.pro.catalog.CatalogException;

/** Independent Managed entry with explicit Fire TV key/lifecycle ownership. */
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public final class LiveTvActivity extends Activity {
  private NativeLiveScreen screen;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    if (!ActivationGuard.enforce(this)) return;
    try {
      screen = new NativeLiveScreen(this, new ManagedModule(this));
      screen.create();
    } catch (CatalogException invalid) {
      LiveModule.error(this, invalid.getMessage());
    }
  }

  @Override protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    recreate();
  }

  @Override public boolean dispatchKeyEvent(KeyEvent e) {
    if (screen != null && screen.key(e)) return true;
    // Amazon Fire TV requires handled media keys to be consumed. In particular a live stream
    // that cannot pause must not leak PLAY_PAUSE to another app/session.
    if (e != null) {
      int k = e.getKeyCode();
      if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
          k == KeyEvent.KEYCODE_MEDIA_PLAY ||
          k == KeyEvent.KEYCODE_MEDIA_PAUSE ||
          k == KeyEvent.KEYCODE_MEDIA_STOP) return true;
    }
    return super.dispatchKeyEvent(e);
  }

  @Override public void onBackPressed() {
    if (screen == null || !screen.back()) super.onBackPressed();
  }

  @Override public void onConfigurationChanged(Configuration c) {
    super.onConfigurationChanged(c);
    if (screen != null) screen.configuration(c);
  }

  @Override protected void onStart() {
    super.onStart();
    if (screen != null) screen.start();
  }

  @Override protected void onPause() {
    if (screen != null) screen.pause();
    super.onPause();
  }

  @Override protected void onStop() {
    // Fire TV: playback/decoder resources must be released before Activity becomes stopped.
    if (screen != null) screen.stop();
    super.onStop();
  }

  @Override protected void onDestroy() {
    if (screen != null) screen.close();
    super.onDestroy();
  }

  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (screen != null) screen.result(request, result, data);
  }
}
