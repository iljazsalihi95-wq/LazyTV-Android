package de.lazytv.pro.player;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import de.lazytv.pro.catalog.CatalogType;
import de.lazytv.pro.catalog.Episode;
import de.lazytv.pro.catalog.ResolvedStream;
import de.lazytv.pro.catalog.StreamItem;

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

        Class<? extends PlayerActivity> target = PlayerActivity.class;
        if (item instanceof Episode) {
            target = EpisodePlayerActivity.class;
        } else if (item.type == CatalogType.MOVIES) {
            target = MoviePlayerActivity.class;
        }

        Intent intent = new Intent(activity, target);
        intent.putExtra(PlayerActivity.EXTRA_PLAYLIST_ID, playlistId);
        intent.putExtra(PlayerActivity.EXTRA_ITEM, item);
        intent.putExtra(PlayerActivity.EXTRA_RESOLVED, resolved);
        intent.putExtra(PlayerActivity.EXTRA_CATEGORY_NAME, categoryName == null ? "" : categoryName);
        activity.startActivity(intent);
    }
}
