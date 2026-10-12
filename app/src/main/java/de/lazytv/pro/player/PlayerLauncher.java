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
            de.lazytv.pro.catalog.MovieDetailActivity.open(activity, playlistId, item, resolved, categoryName);
            return;
        }

        Class<? extends PlayerActivity> target = item instanceof Episode
                ? EpisodePlayerActivity.class : PlayerActivity.class;
        launch(activity, playlistId, item, resolved, categoryName, target);
    }

    public static void playMovie(Activity activity, String playlistId, StreamItem item,
                                 ResolvedStream resolved, String categoryName) {
        launch(activity, playlistId, item, resolved, categoryName, MoviePlayerActivity.class);
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
