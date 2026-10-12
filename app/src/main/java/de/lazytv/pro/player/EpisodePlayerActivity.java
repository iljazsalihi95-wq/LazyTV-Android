package de.lazytv.pro.player;
import android.os.Bundle;import android.widget.TextView;import androidx.media3.common.util.UnstableApi;import de.lazytv.pro.R;import de.lazytv.pro.catalog.Episode;
/** Dedicated episode entry; shares engine mechanics with MoviePlayer, never Live state. */
@UnstableApi public final class EpisodePlayerActivity extends PlayerActivity {
 @Override protected void onCreate(Bundle state){super.onCreate(state);Object item=getIntent().getSerializableExtra(EXTRA_ITEM);if(!(item instanceof Episode)){finish();return;}Episode episode=(Episode)item;TextView meta=findViewById(R.id.player_meta);if(meta==null)return;StringBuilder description=new StringBuilder();if(episode.seasonNumber>0)description.append("Sezoni ").append(episode.seasonNumber);if(episode.episodeNumber>0)description.append(" • Episodi ").append(episode.episodeNumber);if(episode.duration!=null&&!episode.duration.isEmpty())description.append(" • ").append(episode.duration);String plot=episode.meta("plot");if(plot.isEmpty())plot=episode.meta("description");if(!plot.isEmpty())description.append("\n").append(plot);meta.setText(description);}
}
