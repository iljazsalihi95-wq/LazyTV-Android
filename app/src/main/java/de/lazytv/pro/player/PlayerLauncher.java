package de.lazytv.pro.player;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.Map;

import de.lazytv.pro.catalog.CatalogType;
import de.lazytv.pro.catalog.Episode;
import de.lazytv.pro.catalog.ResolvedStream;
import de.lazytv.pro.catalog.StreamItem;
import de.lazytv.pro.catalog.XtreamVodInfoSource;
import de.lazytv.pro.playlist.Playlist;
import de.lazytv.pro.playlist.PlaylistStorage;
import de.lazytv.pro.playlist.PlaylistType;

public final class PlayerLauncher {
    private PlayerLauncher() {}

    public static void open(Activity activity, String playlistId, StreamItem item,
                            ResolvedStream resolved, String categoryName) {
        if (activity == null) return;
        if (playlistId == null || playlistId.trim().isEmpty() || item == null
                || resolved == null || resolved.url == null || resolved.url.trim().isEmpty()) {
            Toast.makeText(activity, "Stream-i nuk mund të hapet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (item.type == CatalogType.MOVIES) {
            Playlist playlist = new PlaylistStorage(activity).get(playlistId);
            if (playlist != null && playlist.getType() == PlaylistType.XTREAM_CODES) {
                final StreamItem original = item;
                final ResolvedStream originalResolved = resolved;
                final String cat = categoryName;
                new Thread(() -> {
                    StreamItem enriched = original;
                    ResolvedStream enrichedResolved = originalResolved;
                    try {
                        Map<String,String> details = new XtreamVodInfoSource().load(playlist, original.id);
                        if (!details.isEmpty()) {
                            Map<String,String> merged = new LinkedHashMap<>(original.metadata);
                            merged.putAll(details);
                            String poster = details.get("poster");
                            String artwork = poster == null || poster.trim().isEmpty() ? original.logo : poster;
                            enriched = new StreamItem(original.id, original.name, original.categoryId,
                                    artwork, original.streamUrl, original.tvgId, original.type,
                                    original.fallbackUrls, merged);

                            Map<String,String> resolvedMetadata = new LinkedHashMap<>(originalResolved.metadata);
                            resolvedMetadata.putAll(merged);
                            String resolvedArtwork = artwork == null || artwork.trim().isEmpty()
                                    ? originalResolved.artwork : artwork;
                            enrichedResolved = new ResolvedStream(originalResolved.url,
                                    originalResolved.streamType, originalResolved.title,
                                    resolvedArtwork, originalResolved.epgId,
                                    originalResolved.headers, resolvedMetadata);
                        }
                    } catch (Exception ignored) {
                        // VOD metadata is optional; playback must still work when a provider omits get_vod_info.
                    }
                    final StreamItem ready = enriched;
                    final ResolvedStream readyResolved = enrichedResolved;
                    activity.runOnUiThread(() -> launch(activity, playlistId, ready, readyResolved, cat,
                            MoviePlayerActivity.class));
                }, "LazyTV-Xtream-VOD-Info").start();
                return;
            }
            launch(activity, playlistId, item, resolved, categoryName, MoviePlayerActivity.class);
            return;
        }

        Class<? extends PlayerActivity> target = item instanceof Episode
                ? EpisodePlayerActivity.class : PlayerActivity.class;
        launch(activity, playlistId, item, resolved, categoryName, target);
    }

    private static void launch(Activity activity, String playlistId, StreamItem item,
                               ResolvedStream resolved, String categoryName,
                               Class<? extends PlayerActivity> target) {
        if (activity.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        Intent intent = new Intent(activity, target);
        intent.putExtra(PlayerActivity.EXTRA_PLAYLIST_ID, playlistId);
        intent.putExtra(PlayerActivity.EXTRA_ITEM, item);
        intent.putExtra(PlayerActivity.EXTRA_RESOLVED, resolved);
        intent.putExtra(PlayerActivity.EXTRA_CATEGORY_NAME, categoryName == null ? "" : categoryName);
        activity.startActivity(intent);
    }
}
