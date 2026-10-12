package de.lazytv.pro.player;

import android.app.Activity;
import android.app.AlertDialog;
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
                        // Details are optional; the user can still explicitly choose PLAY.
                    }
                    final StreamItem ready = enriched;
                    final ResolvedStream readyResolved = enrichedResolved;
                    activity.runOnUiThread(() -> showMovieDetails(activity, playlistId, ready,
                            readyResolved, cat));
                }, "LazyTV-Xtream-VOD-Info").start();
                return;
            }
            showMovieDetails(activity, playlistId, item, resolved, categoryName);
            return;
        }

        Class<? extends PlayerActivity> target = item instanceof Episode
                ? EpisodePlayerActivity.class : PlayerActivity.class;
        launch(activity, playlistId, item, resolved, categoryName, target);
    }

    /** Movie selection must never autoplay. It first presents provider metadata and an explicit PLAY action. */
    private static void showMovieDetails(Activity activity, String playlistId, StreamItem item,
                                         ResolvedStream resolved, String categoryName) {
        if (activity.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        Map<String,String> metadata = new LinkedHashMap<>();
        if (item.metadata != null) metadata.putAll(item.metadata);
        if (resolved.metadata != null) metadata.putAll(resolved.metadata);

        StringBuilder summary = new StringBuilder();
        append(summary, clean(metadata.get("year")));
        append(summary, clean(metadata.get("genre")));
        append(summary, duration(metadata));
        String rating = clean(metadata.get("rating"));
        if (!rating.isEmpty()) append(summary, "★ " + rating);

        String country = clean(metadata.get("country"));
        String director = clean(metadata.get("director"));
        String cast = clean(metadata.get("cast"));
        String plot = first(metadata, "plot", "description", "overview", "storyline");
        if (!country.isEmpty()) summary.append("\n\nShteti: ").append(country);
        if (!director.isEmpty()) summary.append("\nRegjia: ").append(director);
        if (!cast.isEmpty()) summary.append("\nAktorët: ").append(cast);
        if (!plot.isEmpty()) summary.append("\n\nPërmbajtja\n").append(plot);
        if (summary.length() == 0) summary.append("Detajet nuk janë dhënë nga provideri.");

        new AlertDialog.Builder(activity)
                .setTitle(item.name == null || item.name.trim().isEmpty() ? "Movie Details" : item.name.trim())
                .setMessage(summary.toString())
                .setNegativeButton("MBYLL", null)
                .setPositiveButton("▶ PLAY", (dialog, which) -> launch(activity, playlistId, item,
                        resolved, categoryName, MoviePlayerActivity.class))
                .show();
    }

    private static String first(Map<String,String> metadata, String... keys) {
        for (String key : keys) {
            String value = clean(metadata.get(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String duration(Map<String,String> metadata) {
        String value = clean(metadata.get("duration"));
        if (!value.isEmpty() && !value.matches("\\d+")) return value;
        String seconds = clean(metadata.get("duration_secs"));
        if (seconds.isEmpty() && value.matches("\\d+")) seconds = value;
        if (seconds.isEmpty()) return value;
        try {
            long total = Long.parseLong(seconds);
            if (total <= 0) return value;
            long hours = total / 3600;
            long minutes = (total % 3600) / 60;
            return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static void append(StringBuilder out, String value) {
        if (value == null || value.isEmpty()) return;
        if (out.length() > 0) out.append(" • ");
        out.append(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
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
