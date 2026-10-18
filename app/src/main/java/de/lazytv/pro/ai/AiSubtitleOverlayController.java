package de.lazytv.pro.ai;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

/**
 * Drives a native subtitle TextView from validated AI cues without touching or
 * rebuilding the active playback session. Movie/Episode players only provide
 * the current playback position, so Exo/VLC/IJK can share the same overlay.
 */
public final class AiSubtitleOverlayController {
    public interface PositionProvider { long positionMs(); }

    private static final long TICK_MS = 125L;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final TextView view;
    private final PositionProvider position;
    private AiSubtitleDocument document;
    private boolean enabled;
    private String visibleText = "";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!enabled || document == null) { clearView(); return; }
            AiSubtitleDocument.Cue cue = document.cueAt(Math.max(0L, position.positionMs()));
            String text = cue == null ? "" : cue.text;
            if (!text.equals(visibleText)) {
                visibleText = text;
                view.setText(text);
                view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            }
            ui.postDelayed(this, TICK_MS);
        }
    };

    public AiSubtitleOverlayController(TextView view, PositionProvider position) {
        if (view == null || position == null) throw new IllegalArgumentException("Subtitle overlay dependencies required");
        this.view = view;
        this.position = position;
        clearView();
    }

    public void show(AiSubtitleDocument document) {
        if (document == null) { off(); return; }
        this.document = document;
        enabled = true;
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    public void off() {
        enabled = false;
        document = null;
        ui.removeCallbacks(tick);
        clearView();
    }

    public boolean isEnabled() { return enabled && document != null; }

    public void release() { off(); }

    private void clearView() {
        visibleText = "";
        view.setText("");
        view.setVisibility(View.GONE);
    }
}
