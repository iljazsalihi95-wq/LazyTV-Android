package de.lazytv.pro.live;

import android.app.Activity;
import android.view.*;
import android.widget.FrameLayout;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/**
 * Native-only transplant of the owner's ChannelsActivity SINGLE_NAV / PREMIUM V1. Two native views,
 * ONE session/player. Never move/cast the preview's parent or layout params. The WebView catalog
 * and embedded configuration from the reference are intentionally excluded.
 */
@UnstableApi
final class ReferenceFullscreenHost {
  private final Activity activity;
  private final PlayerView preview, full;
  private final FrameLayout content, screen;
  private boolean fullscreen;
  private boolean fill = true;
  private final android.widget.ProgressBar buffering;

  ReferenceFullscreenHost(Activity activity, PlayerView preview) {
    this.activity = activity;
    this.preview = preview;
    content = activity.findViewById(android.R.id.content);
    screen = new FrameLayout(activity);
    screen.setBackgroundColor(android.graphics.Color.BLACK);
    screen.setVisibility(View.GONE);
    full = new PlayerView(activity);
    full.setId(View.generateViewId());
    full.setUseController(false); // Native channel/EPG/record controls are supplied by screen.
    full.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
    full.setFocusable(true);
    full.setClickable(true);
    full.setPlayer(null);
    screen.addView(full, new FrameLayout.LayoutParams(-1, -1));
    buffering = new android.widget.ProgressBar(activity);
    buffering.setVisibility(View.GONE);
    screen.addView(buffering, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    content.addView(screen, new FrameLayout.LayoutParams(-1, -1));
    preview.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
    immersive();
  }

  void buffering(boolean on) {
    buffering.setVisibility(on ? View.VISIBLE : View.GONE);
  }

  void error(CharSequence text) {
    preview.setCustomErrorMessage(text);
    full.setCustomErrorMessage(text);
  }

  FrameLayout container() {
    return screen;
  }

  PlayerView fullscreenView() {
    return full;
  }

  PlayerView activeView() {
    return fullscreen ? full : preview;
  }

  void show(boolean on, LivePlaybackSession session) {
    fullscreen = on;
    // Same detach/attach order as the working ChannelsActivity. No new decoder/session.
    if (session != null) session.attach(activeView());
    screen.setVisibility(on ? View.VISIBLE : View.GONE);
    if (on) screen.bringToFront();
    immersive();
    activeView().requestFocus();
  }

  void toggleFit() {
    fill = !fill;
    int mode = fill ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT;
    preview.setResizeMode(mode);
    full.setResizeMode(mode);
  }

  void immersive() {
    android.view.Window window = activity.getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    if (android.os.Build.VERSION.SDK_INT >= 28) {
      WindowManager.LayoutParams params = window.getAttributes();
      params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      window.setAttributes(params);
    }
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);
    androidx.core.view.WindowInsetsControllerCompat controller = androidx.core.view.WindowCompat.getInsetsController(window, window.getDecorView());
    controller.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
    window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  void close() {
    preview.setPlayer(null);
    full.setPlayer(null);
    preview.setKeepScreenOn(false);
    full.setKeepScreenOn(false);
    content.removeView(screen);
  }
}
