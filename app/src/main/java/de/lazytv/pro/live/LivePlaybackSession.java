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
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.lazytv.pro.catalog.CatalogType;
import de.lazytv.pro.catalog.ProviderPlaybackPolicy;
import de.lazytv.pro.catalog.ResolvedStream;
import de.lazytv.pro.catalog.StalkerCatalogSource;
import de.lazytv.pro.catalog.StreamItem;
import de.lazytv.pro.playlist.Playlist;
import de.lazytv.pro.playlist.PlaylistType;

/** Owns exactly one persistent Live TV player and one video surface. */
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
    private LiveNativeEngine nativeEngine;
    private boolean usingNative,nativeTried,nativeBuffering;
    private final PlayerView playerView;
    private final DefaultHttpDataSource.Factory http;
    private final RetryResolver retryResolver;
    private final Listener listener;
    private final ExecutorService retryWorker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable stablePlaybackReset;
    private volatile long generation;
    private final StalkerPlaybackGuard stalkerGuard = new StalkerPlaybackGuard();
    private long stalkerTicket;
    private int retryCount;
    private int lastHttpStatus;
    private boolean retryRunning;
    private boolean fatalNotified;
    private boolean foreground=true, resumePlayback;
    private volatile boolean released;
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
                if (released || usingNative || !callbackBelongsToCurrentGeneration()) return;
                listener.onBuffering(state == Player.STATE_BUFFERING);
                if (state == Player.STATE_READY) {
                    retryRunning = false;
                    scheduleStableReset();
                    listener.onReady();
                } else {
                    cancelStableReset();
                    if (state == Player.STATE_ENDED && foreground && !retryRunning) retryCurrent(generation, currentItem);
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (released || usingNative || !callbackBelongsToCurrentGeneration()) return;
                cancelStableReset();
                Log.e("LazyTV-LiveSession", "Playback failed host=" + currentHost()
                        + " code=" + error.getErrorCodeName());
                lastHttpStatus = ProviderPlaybackPolicy.status(error);
                if(LiveEnginePolicy.nativeFallback(error.errorCode,lastHttpStatus,nativeTried)){nativeTried=true;startNative(generation,currentItem,currentStream);return;}
                if (lastHttpStatus == 402) {
                    fatalNotified = true;
                    listener.onBuffering(false);
                    listener.onFatalPlaybackError(ProviderPlaybackPolicy.message(402));
                    return;
                }
                if (foreground && !retryRunning) retryCurrent(generation, currentItem);
            }
        });
    }

    /** Invalidate pending old-channel retries immediately, while retaining the player/surface. */
    void beginSelection() {
        generation++;
        stalkerTicket = stalkerGuard.begin();
        retryRunning = false;
        cancelStableReset();
    }

    private boolean isStalker(ResolvedStream stream) {
        return stream != null && "true".equals(stream.metadata.get("stalker_session"));
    }

    void play(StreamItem item, ResolvedStream stream) {
        if (released || item == null || !validStream(stream)) return;
        if (sameActiveStream(item, stream)) {
            if (playerView.getPlayer() != player) playerView.setPlayer(player);
            if(usingNative){if(foreground)nativeEngine.resume();}else if (!player.isPlaying() && foreground) player.play();
            return;
        }
        generation++;
        usingNative=false;nativeTried=false;nativeBuffering=false;if(nativeEngine!=null)nativeEngine.stop();
        cancelStableReset();
        retryCount = 0;
        lastHttpStatus = 0;
        retryRunning = false;
        fatalNotified = false;
        currentItem = item;
        currentStream = null;
        if (isStalker(stream)) stalkerTicket = stalkerGuard.begin();
        apply(generation, item, stream, true);
    }

    private boolean sameActiveStream(StreamItem item, ResolvedStream stream) {
        if (currentItem == null || currentStream == null) return false;
        if (currentItem.id == null || item.id == null || !currentItem.id.equals(item.id)) return false;
        if (currentStream.url == null || !currentStream.url.equals(stream.url)) return false;
        if(usingNative)return nativeEngine!=null&&(nativeEngine.playing()||nativeBuffering);
        int state = player.getPlaybackState();
        return callbackBelongsToCurrentGeneration()
                && (state == Player.STATE_BUFFERING || state == Player.STATE_READY);
    }

    void pauseForBackground() {
        foreground=false;resumePlayback=usingNative||player.getPlayWhenReady();if(usingNative)nativeEngine.pause();else player.pause();
    }
    void resumeFromBackground() {
        foreground=true;
        if(resumePlayback){resumePlayback=false;stalkerTicket=stalkerGuard.begin();retryCount=0;fatalNotified=false;
            if(usingNative)nativeEngine.resume();else if(player.getPlayerError()!=null&&!retryRunning)retryCurrent(generation,currentItem);else player.play();
        }
    }

    boolean isBuffering() { return !released && (usingNative?nativeBuffering:player.getPlaybackState() == Player.STATE_BUFFERING); }

    boolean isActive() {
        if(usingNative)return !released&&nativeEngine!=null&&(nativeEngine.playing()||nativeBuffering);
        return !released && callbackBelongsToCurrentGeneration()
                && (player.isPlaying() || player.getPlaybackState() == Player.STATE_BUFFERING
                || player.getPlaybackState() == Player.STATE_READY);
    }

    private boolean callbackBelongsToCurrentGeneration() {
        MediaItem item = player.getCurrentMediaItem();
        if (item == null || item.localConfiguration == null) return false;
        Object tag = item.localConfiguration.tag;
        return tag instanceof Long && ((Long) tag).longValue() == generation;
    }

    private void scheduleStableReset() {
        cancelStableReset();
        final long expectedGeneration = generation;
        final StreamItem expectedItem = currentItem;
        final ResolvedStream expectedStream = currentStream;
        stablePlaybackReset = () -> {
            if (!released && expectedGeneration == generation && currentItem == expectedItem
                    && currentStream==expectedStream
                    && (usingNative?nativeEngine!=null&&nativeEngine.playing():callbackBelongsToCurrentGeneration()&&player.getPlaybackState()==Player.STATE_READY&&player.isPlaying())) {
                retryCount = 0;
                // A Stalker channel that has played stably earned a fresh bounded recovery
                // allowance. Rotate only the Stalker guard ticket; keep the same ExoPlayer,
                // MediaItem and playback generation so Preview/Fullscreen/Back never rebuilds.
                if (isStalker(currentStream)) stalkerTicket = stalkerGuard.begin();
                fatalNotified = false;
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

    private boolean validStream(ResolvedStream stream) {
        return stream != null && stream.url != null && !stream.url.trim().isEmpty();
    }

    private void retryCurrent(long expectedGeneration, StreamItem expectedItem) {
        if (released || expectedGeneration != generation || expectedItem == null
                || expectedItem != currentItem || currentStream == null || retryRunning
                || retryCount >= MAX_RETRIES) {
            if (!released && expectedGeneration == generation && expectedItem == currentItem
                    && retryCount >= MAX_RETRIES && !fatalNotified) {
                fatalNotified = true;
                listener.onBuffering(false);
                listener.onFatalPlaybackError(lastHttpStatus > 0
                        ? ProviderPlaybackPolicy.message(lastHttpStatus) : "Rilidhja e stream-it dështoi");
            }
            return;
        }
        final boolean stalkerPlayback = isStalker(currentStream);
        final long expectedStalkerTicket = stalkerTicket;
        if (stalkerPlayback && !stalkerGuard.retry(expectedStalkerTicket)) {
            if (!fatalNotified) {
                fatalNotified = true;
                listener.onBuffering(false);
                listener.onFatalPlaybackError(lastHttpStatus > 0
                        ? ProviderPlaybackPolicy.message(lastHttpStatus) : "Stalker: rilidhja e kanalit dështoi pas një prove.");
            }
            return;
        }
        final ResolvedStream fallbackStream = currentStream;
        final int attempt = ++retryCount;
        final int httpStatus = lastHttpStatus;
        retryRunning = true;
        retryWorker.execute(() -> {
            try {
                Thread.sleep(Math.min(2500L, 400L * attempt));
                // Server 10 may rotate Premium URLs. Always ask the catalog resolver for a
                // fresh URL first; only fall back to the current URL when refresh is unavailable.
                ResolvedStream refreshed = retryResolver == null ? null : retryResolver.refresh(expectedItem);
                if (released || expectedGeneration != generation || expectedItem != currentItem) return;
                if (stalkerPlayback && !stalkerGuard.current(expectedStalkerTicket)) return;
                if (!validStream(refreshed)) {
                    if (stalkerPlayback) throw new IllegalStateException("Fresh Stalker URL missing");
                    refreshed = fallbackStream;
                }
                // Retain Server 10's existing recovery; a real Stalker playlist already used
                // its shared provider session and must not handshake/resolve a second time.
                ResolvedStream stalkerRetry = stalkerPlayback || "true".equals(refreshed.metadata.get("server10_fresh_link")) ? null : refreshExpiredStalkerLink(refreshed, expectedItem);
                if (stalkerRetry != null) refreshed = stalkerRetry;
                final ResolvedStream result = ProviderPlaybackPolicy.retryHeaders(refreshed, expectedItem, httpStatus, attempt);
                main.post(() -> {
                    if (released || expectedGeneration != generation || expectedItem != currentItem) return;
                    retryRunning = false;
                    if (!validStream(result)) {
                        retryCurrent(expectedGeneration, expectedItem);
                        return;
                    }
                    apply(expectedGeneration, expectedItem, result, false);
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                main.post(() -> {
                    if (!released && expectedGeneration == generation && expectedItem == currentItem)
                        retryRunning = false;
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (released || expectedGeneration != generation || expectedItem != currentItem) return;
                    retryRunning = false;
                    int code = ProviderPlaybackPolicy.status(error);
                    if (code > 0) lastHttpStatus = code;
                    if (code == 402) {
                        fatalNotified = true;
                        listener.onBuffering(false);
                        listener.onFatalPlaybackError(ProviderPlaybackPolicy.message(code));
                        return;
                    }
                    retryCurrent(expectedGeneration, expectedItem);
                });
            }
        });
    }

    private ResolvedStream refreshExpiredStalkerLink(ResolvedStream stream, StreamItem item) {
        if (stream == null || stream.url == null || !stream.url.contains("play_token=")) return null;
        try {
            URL u = new URL(stream.url);
            String mac = queryValue(u.getQuery(), "mac");
            String streamId = queryValue(u.getQuery(), "stream");
            if (mac.isEmpty() || streamId.isEmpty()) return null;
            String portal = u.getProtocol() + "://" + u.getAuthority();
            Playlist stalker = new Playlist("server10-stalker", "Server 10", PlaylistType.STALKER_PORTAL,
                    portal, "", "", mac, System.currentTimeMillis());
            StreamItem reference = new StreamItem(item.id, item.name, item.categoryId, item.logo,
                    "ffmpeg http://localhost/ch/" + streamId + "_", item.tvgId,
                    CatalogType.LIVE, new ArrayList<String>());
            StalkerCatalogSource.LinkResult link = new StalkerCatalogSource().resolve(stalker, reference);
            return new ResolvedStream(link.url, stream.streamType, stream.title, stream.artwork,
                    stream.epgId, link.headers, stream.metadata);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String queryValue(String query, String wanted) {
        if (query == null || wanted == null) return "";
        for (String part : query.split("&")) {
            int p = part.indexOf('=');
            if (p <= 0 || !wanted.equalsIgnoreCase(part.substring(0, p))) continue;
            try { return URLDecoder.decode(part.substring(p + 1), "UTF-8"); }
            catch (Exception ignored) { return ""; }
        }
        return "";
    }

    private void apply(long expectedGeneration, StreamItem item, ResolvedStream stream, boolean resetPosition) {
        if (released || expectedGeneration != generation || item != currentItem || !validStream(stream)) return;
        currentStream = stream;
        if(usingNative){startNative(expectedGeneration,item,stream);return;}
        http.setDefaultRequestProperties(stream.headers);
        MediaItem.Builder media = new MediaItem.Builder().setUri(Uri.parse(stream.url))
                .setTag(Long.valueOf(expectedGeneration));
        String url = stream.url.toLowerCase(Locale.US);
        String type = stream.streamType == null ? "" : stream.streamType.toLowerCase(Locale.US);
        if(url.contains(".mpd")||type.equals("dash"))media.setMimeType(MimeTypes.APPLICATION_MPD);
        else if(url.contains(".ism/manifest")||url.contains(".isml/manifest")||type.equals("smoothstreaming"))media.setMimeType(MimeTypes.APPLICATION_SS);
        else if (url.contains(".m3u8") || type.contains("hls") || type.contains("m3u8"))
            media.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (url.matches(".*\\.(ts|mpeg|mpegts)(\\?.*)?$") || url.contains("extension=ts")
                || url.contains("extension=mpegts") || type.equals("ts")
                || type.contains("mpegts") || type.contains("mpeg-ts"))
            media.setMimeType(MimeTypes.VIDEO_MP2T);
        else if (url.matches(".*\\.(mp4|m4v)(\\?.*)?$") || url.contains("extension=mp4")
                || type.equals("mp4")) media.setMimeType(MimeTypes.VIDEO_MP4);
        if(playerView.getPlayer()!=player)playerView.setPlayer(player);
        player.setMediaItem(media.build(), resetPosition);
        player.prepare();
        if (foreground) player.play(); else resumePlayback=true;
    }

    private void startNative(long ticket,StreamItem item,ResolvedStream stream){
        if(released||ticket!=generation||item!=currentItem||!validStream(stream))return;
        usingNative=true;nativeBuffering=true;player.stop();playerView.setPlayer(null);listener.onBuffering(true);
        try{if(nativeEngine==null)nativeEngine=new LiveNativeEngine(playerView);nativeEngine.play(stream,foreground,new LiveNativeEngine.Events(){
            private boolean current(){return !released&&usingNative&&ticket==generation&&item==currentItem&&stream==currentStream;}
            public void ready(){main.post(()->{if(!current())return;nativeBuffering=false;retryRunning=false;listener.onBuffering(false);scheduleStableReset();listener.onReady();});}
            public void buffering(boolean value){main.post(()->{if(!current())return;nativeBuffering=value;listener.onBuffering(value);});}
            public void failed(){main.post(()->{if(!current())return;nativeBuffering=false;retryRunning=false;listener.onBuffering(false);if(foreground)retryCurrent(ticket,item);});}
        });}catch(Exception unavailable){nativeBuffering=false;listener.onBuffering(false);fatalNotified=true;listener.onFatalPlaybackError("Native Live decoder nuk mund të përgatisë stream-in");}
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
        if(nativeEngine!=null)nativeEngine.release();
    }
}
