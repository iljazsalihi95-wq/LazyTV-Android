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
    full.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    full.setFocusable(true);
    full.setClickable(true);
    full.setPlayer(null);
    screen.addView(full, new FrameLayout.LayoutParams(-1, -1));
    buffering = new android.widget.ProgressBar(activity);
    buffering.setVisibility(View.GONE);
    screen.addView(buffering, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    content.addView(screen, new FrameLayout.LayoutParams(-1, -1));
    preview.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
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
    if (on) {
      screen.bringToFront();
      activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      activity
          .getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    } else {
      activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }
    activeView().requestFocus();
  }

  void close() {
    preview.setPlayer(null);
    full.setPlayer(null);
    preview.setKeepScreenOn(false);
    full.setKeepScreenOn(false);
    content.removeView(screen);
  }
}
