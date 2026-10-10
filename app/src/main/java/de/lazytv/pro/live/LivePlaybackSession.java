package de.lazytv.pro.live;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.lazytv.pro.catalog.ResolvedStream;
import de.lazytv.pro.catalog.StreamItem;

/** Owns one persistent Live TV player and one video surface. */
@UnstableApi
final class LivePlaybackSession {
    interface RetryResolver { ResolvedStream refresh(StreamItem item) throws Exception; }
    interface Listener {
        void onBuffering(boolean buffering);
        void onReady();
        void onFatalPlaybackError(String message);
    }

    private static final int MAX_RETRIES = 3;
    private static final long STABLE_PLAYBACK_MS = 12000L;
    private final ExoPlayer player;
    private final PlayerView playerView;
    private final DefaultHttpDataSource.Factory http;
    private final RetryResolver retryResolver;
    private final Listener listener;
    private final ExecutorService retryWorker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable stablePlaybackReset;
    private long generation;
    private int retryCount;
    private boolean retryRunning;
    private boolean released;
    private StreamItem currentItem;
    private ResolvedStream currentStream;

    LivePlaybackSession(Context context, PlayerView playerView,
                        RetryResolver retryResolver, Listener listener) {
        this.playerView = playerView;
        this.retryResolver = retryResolver;
        this.listener = listener;
        http = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000).setReadTimeoutMs(60000)
                .setAllowCrossProtocolRedirects(true).setKeepPostFor302Redirects(true);
        DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(context, http);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 60000, 2500, 5000)
                .setPrioritizeTimeOverSizeThresholds(true).build();
        player = new ExoPlayer.Builder(context).setLoadControl(loadControl)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource)).build();
        playerView.setUseController(false);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (released) return;
                listener.onBuffering(state == Player.STATE_BUFFERING);
                if (state == Player.STATE_READY) {
                    retryRunning = false;
                    scheduleStableReset();
                    listener.onReady();
                } else {
                    cancelStableReset();
                    if (state == Player.STATE_ENDED) retryCurrent(generation, currentItem);
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (released) return;
                cancelStableReset();
                Log.e("LazyTV-LiveSession", "Playback failed host=" + currentHost()
                        + " code=" + error.getErrorCodeName());
                retryCurrent(generation, currentItem);
            }
        });
    }

    void play(StreamItem item, ResolvedStream stream) {
        if (released) return;
        generation++;
        cancelStableReset();
        retryCount = 0;
        retryRunning = false;
        currentItem = item;
        apply(generation, item, stream);
    }

    boolean isActive() {
        return !released && (player.isPlaying() || player.getPlaybackState() == Player.STATE_BUFFERING
                || player.getPlaybackState() == Player.STATE_READY);
    }

    private void scheduleStableReset() {
        cancelStableReset();
        final long expectedGeneration = generation;
        final StreamItem expectedItem = currentItem;
        stablePlaybackReset = () -> {
            if (!released && expectedGeneration == generation && currentItem == expectedItem
                    && player.getPlaybackState() == Player.STATE_READY && player.isPlaying()) {
                retryCount = 0;
            }
        };
        main.postDelayed(stablePlaybackReset, STABLE_PLAYBACK_MS);
    }

    private void cancelStableReset() {
        if (stablePlaybackReset != null) {
            main.removeCallbacks(stablePlaybackReset);
            stablePlaybackReset = null;
        }
    }

    private void retryCurrent(long expectedGeneration, StreamItem expectedItem) {
        if (released || expectedGeneration != generation || expectedItem == null
                || expectedItem != currentItem || currentStream == null || retryRunning
                || retryCount >= MAX_RETRIES) {
            if (!released && expectedGeneration == generation && expectedItem == currentItem
                    && retryCount >= MAX_RETRIES) {
                listener.onBuffering(false);
                listener.onFatalPlaybackError("Rilidhja e stream-it dështoi");
            }
            return;
        }
        final ResolvedStream fallbackStream = currentStream;
        final int attempt = ++retryCount;
        retryRunning = true;
        retryWorker.execute(() -> {
            try {
                Thread.sleep(Math.min(2500L, 400L * attempt));
                ResolvedStream refreshed = retryResolver == null
                        ? fallbackStream : retryResolver.refresh(expectedItem);
                main.post(() -> {
                    if (released || expectedGeneration != generation || expectedItem != currentItem) return;
                    retryRunning = false;
                    apply(expectedGeneration, expectedItem, refreshed);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (released || expectedGeneration != generation || expectedItem != currentItem) return;
                    retryRunning = false;
                    retryCurrent(expectedGeneration, expectedItem);
                });
            }
        });
    }

    private void apply(long expectedGeneration, StreamItem item, ResolvedStream stream) {
        if (released || expectedGeneration != generation || item != currentItem || stream == null
                || stream.url == null || stream.url.trim().isEmpty()) return;
        currentStream = stream;
        http.setDefaultRequestProperties(stream.headers);
        MediaItem.Builder media = new MediaItem.Builder().setUri(Uri.parse(stream.url));
        String url = stream.url.toLowerCase(Locale.US);
        String type = stream.streamType == null ? "" : stream.streamType.toLowerCase(Locale.US);
        if (url.contains(".m3u8") || type.contains("hls") || type.contains("m3u8"))
            media.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (url.matches(".*\\.(ts|mpeg|mpegts)(\\?.*)?$") || url.contains("extension=ts")
                || url.contains("extension=mpegts") || type.equals("ts")
                || type.contains("mpegts") || type.contains("mpeg-ts"))
            media.setMimeType(MimeTypes.VIDEO_MP2T);
        else if (url.matches(".*\\.(mp4|m4v)(\\?.*)?$") || url.contains("extension=mp4")
                || type.equals("mp4")) media.setMimeType(MimeTypes.VIDEO_MP4);
        player.setMediaItem(media.build(), true);
        player.prepare();
        player.play();
    }

    private String currentHost() {
        try { return currentStream == null ? "n/a" : new URL(currentStream.url).getHost(); }
        catch (Exception ignored) { return "n/a"; }
    }

    void release() {
        if (released) return;
        released = true;
        generation++;
        retryRunning = false;
        cancelStableReset();
        main.removeCallbacksAndMessages(null);
        retryWorker.shutdownNow();
        playerView.setPlayer(null);
        player.release();
    }
}
