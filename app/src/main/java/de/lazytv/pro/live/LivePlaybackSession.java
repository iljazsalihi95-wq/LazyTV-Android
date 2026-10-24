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
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.catalog.ProviderPlaybackPolicy;
import de.lazytv.pro.catalog.ResolvedStream;
import de.lazytv.pro.catalog.StreamItem;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Fire-TV safe playback core.
 *
 * <p>One Activity owns one ExoPlayer for its whole lifetime. Fullscreen/list UI may change around
 * it, but the decoder/player is not replaced and there is no VLC/IJK hand-off. This mirrors the
 * proven IBO-model behaviour used by the old working ChannelsActivity while keeping LazyTV provider
 * resolution and fresh-link retry.
 */
@UnstableApi
final class LivePlaybackSession {
  interface RetryResolver {
    ResolvedStream refresh(StreamItem item) throws Exception;
  }

  interface Listener {
    void onBuffering(boolean buffering);

    void onReady();

    void onFatalPlaybackError(String message);
  }

  private static final int MAX_RETRIES = 2;
  private static final long STABLE_PLAYBACK_MS = 12000L;

  private PlayerView playerView;
  private boolean recovered458;
  private String provider = "SELECTED_PROVIDER";
  private final java.util.Set<String> refused458 = new java.util.LinkedHashSet<>();
  private final ExoPlayer player;
  private final DefaultHttpDataSource.Factory http;
  private final RetryResolver retryResolver;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ThreadPoolExecutor retryWorker =
      new ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(1),
          new ThreadPoolExecutor.DiscardOldestPolicy());

  private volatile boolean released;
  private volatile long generation;
  private boolean foreground = true;
  private boolean resumePlayback;
  private boolean retryRunning;
  private boolean fatalNotified;
  private int retryCount;
  private int lastHttpStatus;
  private String lastSafeError = "";
  private Runnable stableReset;
  private StreamItem currentItem;
  private ResolvedStream currentStream;

  LivePlaybackSession(
      Context context, PlayerView initialView, RetryResolver retryResolver, Listener listener) {
    this.playerView = initialView;
    this.retryResolver = retryResolver;
    this.listener = listener;

    http =
        new DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(60000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true);
    DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(context, http);

    // Deliberately use ExoPlayer defaults for decoder/load-control selection.
    // The old Fire-TV working player did the same instead of forcing a second engine.
    player =
        new ExoPlayer.Builder(context)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
            .build();

    playerView.setUseController(false);
    playerView.setClickable(true);
    playerView.setFocusable(true);
    playerView.setKeepScreenOn(false);
    playerView.setPlayer(player);

    player.addListener(
        new Player.Listener() {
          @Override
          public void onIsPlayingChanged(boolean playing) {
            playerView.setKeepScreenOn(
                !released
                    && foreground
                    && player.getPlayWhenReady()
                    && (playing || player.getPlaybackState() == Player.STATE_BUFFERING));
          }

          @Override
          public void onPlaybackStateChanged(int state) {
            if (released || !belongsToCurrent()) return;
            playerView.setKeepScreenOn(
                foreground
                    && player.getPlayWhenReady()
                    && (state == Player.STATE_READY || state == Player.STATE_BUFFERING));
            listener.onBuffering(state == Player.STATE_BUFFERING);
            if (state == Player.STATE_READY) {
              retryRunning = false;
              scheduleStableReset();
              listener.onReady();
            } else if (state == Player.STATE_ENDED && foreground && !retryRunning) {
              cancelStableReset();
              retryCurrent(generation, currentItem);
            }
          }

          @Override
          public void onPlayerError(PlaybackException error) {
            if (released || !belongsToCurrent()) return;
            cancelStableReset();
            lastHttpStatus = ProviderPlaybackPolicy.status(error);
            lastSafeError = "Media3: " + error.getErrorCodeName();
            Log.e(
                "LazyTV-LiveSession",
                "Playback failed host="
                    + currentHost()
                    + " http="
                    + lastHttpStatus
                    + " code="
                    + error.getErrorCodeName());
            if (lastHttpStatus == 402) {
              fatal(ProviderPlaybackPolicy.message(402));
              return;
            }
            if (foreground && !retryRunning) retryCurrent(generation, currentItem);
          }
        });
  }

  // Transplanted reference order: detach previous surface, then attach SAME player to target.
  void attach(PlayerView target) {
    if (released || target == playerView) return;
    playerView.setPlayer(null);
    playerView.setKeepScreenOn(false);
    playerView = target;
    target.setPlayer(player);
    target.setKeepScreenOn(
        foreground
            && player.getPlayWhenReady()
            && (player.getPlaybackState() == Player.STATE_READY
                || player.getPlaybackState() == Player.STATE_BUFFERING));
  }

  void provider(String provider) {
    this.provider = provider;
  }

  void beginSelection() {
    generation++;
    retryRunning = false;
    cancelStableReset();
  }

  void play(StreamItem item, ResolvedStream stream) {
    if (released || item == null || !valid(stream)) return;
    if (sameActive(item, stream)) {
      if (playerView.getPlayer() != player) playerView.setPlayer(player);
      if (foreground && !player.isPlaying()) player.play();
      return;
    }
    generation++;
    cancelStableReset();
    retryCount = 0;
    recovered458 = false;
    lastHttpStatus = 0;
    lastSafeError = "";
    retryRunning = false;
    fatalNotified = false;
    currentItem = item;
    currentStream = null;
    if (refused458.contains(stream.url)) {
      currentStream = stream;
      lastHttpStatus = 458;
      retryCurrent(generation, item);
      return;
    }
    apply(generation, item, stream, true);
  }

  private boolean sameActive(StreamItem item, ResolvedStream stream) {
    if (currentItem == null || currentStream == null || currentItem.id == null || item.id == null)
      return false;
    if (!currentItem.id.equals(item.id) || !currentStream.url.equals(stream.url)) return false;
    int state = player.getPlaybackState();
    return belongsToCurrent() && (state == Player.STATE_BUFFERING || state == Player.STATE_READY);
  }

  void pauseForBackground() {
    if (released || !foreground) return;
    foreground = false;
    playerView.setKeepScreenOn(false);
    resumePlayback = player.getPlayWhenReady();
    player.pause();
  }

  void resumeFromBackground() {
    if (released) return;
    foreground = true;
    if (resumePlayback) {
      resumePlayback = false;
      retryCount = 0;
      fatalNotified = false;
      if (player.getPlayerError() != null && !retryRunning) retryCurrent(generation, currentItem);
      else player.play();
    }
  }

  ResolvedStream recordingStream() {
    return currentStream;
  }

  boolean togglePause() {
    if (released || currentStream == null || !player.isCurrentMediaItemSeekable()) return false;
    if (player.getPlayWhenReady()) player.pause();
    else player.play();
    return true;
  }

  String diagnosticSummary() {
    return de.lazytv.pro.catalog.PlaybackRequestSummary.describe(currentStream, lastHttpStatus);
  }

  boolean isBuffering() {
    return !released && player.getPlaybackState() == Player.STATE_BUFFERING;
  }

  boolean isActive() {
    if (released || !belongsToCurrent()) return false;
    int state = player.getPlaybackState();
    return player.isPlaying() || state == Player.STATE_BUFFERING || state == Player.STATE_READY;
  }

  private boolean belongsToCurrent() {
    MediaItem media = player.getCurrentMediaItem();
    if (media == null || media.localConfiguration == null) return false;
    Object tag = media.localConfiguration.tag;
    return tag instanceof Long && ((Long) tag).longValue() == generation;
  }

  private void scheduleStableReset() {
    cancelStableReset();
    final long ticket = generation;
    stableReset =
        () -> {
          if (!released
              && ticket == generation
              && belongsToCurrent()
              && player.getPlaybackState() == Player.STATE_READY
              && player.isPlaying()) {
            retryCount = 0;
            fatalNotified = false;
          }
        };
    main.postDelayed(stableReset, STABLE_PLAYBACK_MS);
  }

  private void cancelStableReset() {
    if (stableReset != null) {
      main.removeCallbacks(stableReset);
      stableReset = null;
    }
  }

  private boolean valid(ResolvedStream stream) {
    return stream != null && stream.url != null && !stream.url.trim().isEmpty();
  }

  private boolean retryAllowed(int status, int attempts) {
    // Premium 458 is special: never replay the rejected URL; permit exactly one
    // provider refresh/create_link attempt. Other statuses keep provider policy.
    if (status == 458) return !recovered458;
    return attempts < MAX_RETRIES && ProviderPlaybackPolicy.shouldRetry(status, attempts);
  }

  private void retryCurrent(long expectedGeneration, StreamItem expectedItem) {
    if (released
        || expectedGeneration != generation
        || expectedItem == null
        || expectedItem != currentItem
        || currentStream == null
        || retryRunning
        || !retryAllowed(lastHttpStatus, retryCount)) {
      if (!released
          && expectedGeneration == generation
          && expectedItem == currentItem
          && !retryAllowed(lastHttpStatus, retryCount)
          && !fatalNotified) {
        fatal(
            lastHttpStatus > 0
                ? ProviderPlaybackPolicy.message(lastHttpStatus)
                : (lastSafeError.isEmpty() ? "Rilidhja e stream-it dështoi" : lastSafeError));
      }
      return;
    }

    final ResolvedStream rejected = currentStream;
    final int status = lastHttpStatus;
    if (status == 458) {
      refused458.add(rejected.url);
      if (refused458.size() > 256) refused458.remove(refused458.iterator().next());
    }
    final int attempt = ++retryCount;
    if (status == 458) recovered458 = true;
    RecoveryLog.event(provider, currentItem, rejected, status, attempt, null);
    retryRunning = true;
    retryWorker.execute(
        () -> {
          try {
            Thread.sleep(status == 429 ? 10000L : Math.min(1800L, 350L * attempt));
            if (released || expectedGeneration != generation || expectedItem != currentItem) return;
            ResolvedStream refreshed =
                retryResolver == null ? null : retryResolver.refresh(expectedItem);
            if (released || expectedGeneration != generation || expectedItem != currentItem) return;
            if (!valid(refreshed)) throw new IllegalStateException("Fresh playback URL missing");
            if (refused458.contains(refreshed.url)
                || !ProviderPlaybackPolicy.usableRefresh(status, rejected.url, refreshed.url))
              throw new IllegalStateException("Provider did not issue a fresh playback URL");
            RecoveryLog.event(provider, expectedItem, rejected, status, attempt, refreshed);
            final ResolvedStream result =
                ProviderPlaybackPolicy.retryHeaders(refreshed, expectedItem, status, attempt);
            main.post(
                () -> {
                  if (released || expectedGeneration != generation || expectedItem != currentItem)
                    return;
                  retryRunning = false;
                  apply(expectedGeneration, expectedItem, result, false);
                });
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            main.post(
                () -> {
                  if (!released && expectedGeneration == generation) retryRunning = false;
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  if (released || expectedGeneration != generation || expectedItem != currentItem)
                    return;
                  retryRunning = false;
                  int code = ProviderPlaybackPolicy.status(failure);
                  if (code > 0) lastHttpStatus = code;
                  else lastSafeError = LiveErrorSummary.connection(failure);
                  retryCurrent(expectedGeneration, expectedItem);
                });
          }
        });
  }

  private void apply(
      long expectedGeneration, StreamItem item, ResolvedStream stream, boolean resetPosition) {
    if (released || expectedGeneration != generation || item != currentItem || !valid(stream))
      return;
    currentStream = stream;
    http.setDefaultRequestProperties(stream.headers);
    MediaItem.Builder media =
        new MediaItem.Builder()
            .setUri(Uri.parse(stream.url))
            .setTag(Long.valueOf(expectedGeneration));
    String url = stream.url.toLowerCase(Locale.US);
    String type = stream.streamType == null ? "" : stream.streamType.toLowerCase(Locale.US);
    if (url.contains(".mpd") || type.equals("dash")) media.setMimeType(MimeTypes.APPLICATION_MPD);
    else if (url.contains(".ism/manifest")
        || url.contains(".isml/manifest")
        || type.equals("smoothstreaming")) media.setMimeType(MimeTypes.APPLICATION_SS);
    else if (url.contains(".m3u8") || type.contains("hls") || type.contains("m3u8"))
      media.setMimeType(MimeTypes.APPLICATION_M3U8);
    else if (url.matches(".*\\.(ts|mpeg|mpegts)(\\?.*)?$")
        || url.contains("extension=ts")
        || type.equals("ts")
        || type.contains("mpegts")
        || type.contains("mpeg-ts")) media.setMimeType(MimeTypes.VIDEO_MP2T);
    else if (url.matches(".*\\.(mp4|m4v)(\\?.*)?$")
        || url.contains("extension=mp4")
        || type.equals("mp4")) media.setMimeType(MimeTypes.VIDEO_MP4);

    if (playerView.getPlayer() != player) playerView.setPlayer(player);
    player.setMediaItem(media.build(), resetPosition);
    player.prepare();
    if (foreground) player.play();
    else resumePlayback = true;
  }

  private void fatal(String message) {
    if (fatalNotified) return;
    fatalNotified = true;
    listener.onBuffering(false);
    listener.onFatalPlaybackError(message);
  }

  private String currentHost() {
    try {
      return currentStream == null ? "n/a" : new URL(currentStream.url).getHost();
    } catch (Exception ignored) {
      return "n/a";
    }
  }

  void release() {
    if (released) return;
    released = true;
    generation++;
    retryRunning = false;
    cancelStableReset();
    main.removeCallbacksAndMessages(null);
    retryWorker.shutdownNow();
    playerView.setKeepScreenOn(false);
    playerView.setPlayer(null);
    player.release();
  }
}
