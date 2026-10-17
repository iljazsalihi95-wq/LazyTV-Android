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
        if (playlistId == null || playlistId.trim().isEmpty() || item == null) {
            Toast.makeText(activity, "Përmbajtja nuk mund të hapet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!PlayerContentPolicy.playable(item)) {
            Toast.makeText(activity,"Zgjidhni sezonin dhe episodin",Toast.LENGTH_SHORT).show();
            return;
        }

        // Movie browsing is metadata-first: opening details must never depend on
        // an expiring/resolved playback URL. MovieDetailActivity resolves only
        // when PLAY/RESUME is pressed.
        if (item.type == CatalogType.MOVIES) {
            de.lazytv.pro.catalog.MovieDetailActivity.open(activity, playlistId, item, null, categoryName);
            return;
        }

        if (resolved == null || resolved.url == null || resolved.url.trim().isEmpty()) {
            Toast.makeText(activity, "Stream-i nuk mund të hapet", Toast.LENGTH_SHORT).show();
            return;
        }

        Class<? extends PlayerActivity> target = item instanceof Episode
                ? EpisodePlayerActivity.class : PlayerActivity.class;
        launch(activity, playlistId, item, resolved, categoryName, target);
    }

    public static void playMovie(Activity activity, String playlistId, StreamItem item,
                                 ResolvedStream resolved, String categoryName) {
        playMovie(activity,playlistId,item,resolved,categoryName,false);
    }

    public static void playMovie(Activity activity,String playlistId,StreamItem item,ResolvedStream resolved,String categoryName,boolean resume){
        launch(activity,playlistId,item,resolved,categoryName,MoviePlayerActivity.class,resume?"resume":"start");
    }
    public static void playEpisode(Activity activity,String provider,Episode episode,ResolvedStream stream,String seriesName,boolean resume){
        launch(activity,provider,episode,stream,seriesName,EpisodePlayerActivity.class,resume?"resume":"start");
    }

    private static void launch(Activity activity, String playlistId, StreamItem item,
                               ResolvedStream resolved, String categoryName,
                               Class<? extends PlayerActivity> target) {
        launch(activity,playlistId,item,resolved,categoryName,target,null);
    }
    private static void launch(Activity activity,String playlistId,StreamItem item,ResolvedStream resolved,String categoryName,Class<? extends PlayerActivity> target,String resumeMode){
        if (activity == null || item == null || resolved == null || resolved.url == null || resolved.url.trim().isEmpty()) {
            if (activity != null) Toast.makeText(activity,"Stream-i nuk mund të hapet",Toast.LENGTH_SHORT).show();
            return;
        }
        if (activity.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        Intent intent = new Intent(activity, target);
        intent.putExtra(PlayerActivity.EXTRA_PLAYLIST_ID, playlistId);
        if(resumeMode!=null)intent.putExtra(PlayerActivity.EXTRA_RESUME_MODE,resumeMode);
        intent.putExtra(PlayerActivity.EXTRA_ITEM, item);
        intent.putExtra(PlayerActivity.EXTRA_RESOLVED, resolved);
        intent.putExtra(PlayerActivity.EXTRA_CATEGORY_NAME, categoryName == null ? "" : categoryName);
        activity.startActivity(intent);
    }
}
