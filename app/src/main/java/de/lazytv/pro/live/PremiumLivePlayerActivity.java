package de.lazytv.pro.live;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.media3.common.util.UnstableApi;
import de.lazytv.pro.activation.ActivationGuard;
import de.lazytv.pro.catalog.CatalogException;

/**
 * Independent Premium entry. Own module, layout and native session, composed without LiveTvActivity
 * inheritance.
 */
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public final class PremiumLivePlayerActivity extends Activity {
  private NativeLiveScreen screen;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    if (!ActivationGuard.enforce(this)) return;
    try {
      screen = new NativeLiveScreen(this, new PremiumModule(this));
      screen.create();
    } catch (CatalogException invalid) {
      android.util.Log.e("LazyTV-Premium", "Premium catalog initialization failed", invalid);
      screen = null;
      LiveModule.error(this, invalid.getMessage());
    } catch (RuntimeException startup) {
      android.util.Log.e("LazyTV-Premium", "Premium native screen initialization failed", startup);
      screen = null;
      new android.app.AlertDialog.Builder(this)
          .setTitle("Premium nuk u hap")
          .setMessage("Gabim gjatë hapjes së Premium. Kontrollo raportin e gabimit në pajisje.")
          .setPositiveButton("Kthehu", (dialog, which) -> finish())
          .setOnCancelListener(dialog -> finish())
          .show();
    }
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    recreate();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {
    return screen != null && screen.key(e) || super.dispatchKeyEvent(e);
  }

  @Override
  public void onBackPressed() {
    if (screen == null || !screen.back()) super.onBackPressed();
  }

  @Override
  public void onConfigurationChanged(Configuration c) {
    super.onConfigurationChanged(c);
    if (screen != null) screen.configuration(c);
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (screen != null) screen.start();
  }

  @Override public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (screen != null) screen.windowFocus(focused);
  }

  @Override protected void onResume() {
    super.onResume();
    if (screen != null) screen.start();
  }

  @Override
  protected void onPause() {
    if (screen != null) screen.pause();
    super.onPause();
  }

  @Override
  protected void onStop() {
    if (screen != null) screen.stop();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (screen != null) screen.close();
    super.onDestroy();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (screen != null) screen.result(request, result, data);
  }
}
