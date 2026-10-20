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
        append(details, duration(episode));
        append(details, episode.meta("year"));
        append(details, episode.meta("genre"));
        String rating = clean(episode.meta("rating"));
        if (!rating.isEmpty()) append(details, "★ " + rating);

        String plot = first(episode.meta("plot"), episode.meta("description"),
                episode.meta("overview"), episode.meta("storyline"));
        if (!plot.isEmpty()) details.append("\n\nPërmbajtja\n").append(plot);
        meta.setText(details);
    }

    private String duration(Episode episode) {
        String value = clean(episode.duration);
        if (!value.isEmpty() && !value.matches("\\d+")) return value;
        String seconds = clean(episode.meta("duration_secs"));
        if (seconds.isEmpty() && value.matches("\\d+")) seconds = value;
        if (seconds.isEmpty()) return value;
        try {
            long total = Long.parseLong(seconds);
            if (total <= 0) return value;
            long hours = total / 3600;
            long minutes = (total % 3600) / 60;
            if (hours > 0) return hours + "h " + minutes + "m";
            return minutes + "m";
        } catch (NumberFormatException ignored) {
            return value;
        }
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
