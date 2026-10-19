package de.lazytv.pro.player;

import android.os.Bundle;
import android.widget.TextView;

import androidx.media3.common.util.UnstableApi;

import de.lazytv.pro.R;
import de.lazytv.pro.catalog.Episode;

/** Dedicated episode entry; shares engine mechanics with MoviePlayer, never Live state. */
@UnstableApi
public final class EpisodePlayerActivity extends PlayerActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Object item = getIntent().getSerializableExtra(EXTRA_ITEM);
        if (!(item instanceof Episode)) {
            finish();
            return;
        }
        Episode episode = (Episode) item;
        TextView meta = findViewById(R.id.player_meta);
        if (meta == null) return;

        StringBuilder details = new StringBuilder();
        if (episode.seasonNumber > 0) append(details, "Sezoni " + episode.seasonNumber);
        if (episode.episodeNumber > 0) append(details, "Episodi " + episode.episodeNumber);
        append(details, episode.duration);
        append(details, episode.meta("year"));
        append(details, episode.meta("genre"));
        String rating = clean(episode.meta("rating"));
        if (!rating.isEmpty()) append(details, "★ " + rating);

        String plot = first(episode.meta("plot"), episode.meta("description"),
                episode.meta("overview"), episode.meta("storyline"));
        if (!plot.isEmpty()) details.append("\n\nPërmbajtja\n").append(plot);
        meta.setText(details);
    }

    private void append(StringBuilder out, String value) {
        value = clean(value);
        if (value.isEmpty()) return;
        if (out.length() > 0) out.append(" • ");
        out.append(value);
    }

    private String first(String... values) {
        for (String value : values) {
            value = clean(value);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
