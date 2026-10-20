package de.lazytv.pro.player;

import android.os.Bundle;
import android.widget.TextView;

import androidx.media3.common.util.UnstableApi;

import java.util.Map;

import de.lazytv.pro.R;
import de.lazytv.pro.catalog.ResolvedStream;

/** Dedicated VOD playback entry point. Keeps movie metadata/resume UX isolated
 * from Live TV and Episode playback lifecycles. */
@UnstableApi
public final class MoviePlayerActivity extends PlayerActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ResolvedStream resolved = (ResolvedStream) getIntent().getSerializableExtra(EXTRA_RESOLVED);
        if (resolved == null || resolved.metadata == null) return;
        TextView metaView = findViewById(R.id.player_meta);
        if (metaView == null) return;
        String details = movieDetails(resolved.metadata);
        if (!details.isEmpty()) metaView.setText(details);
    }

    private String movieDetails(Map<String,String> metadata) {
        StringBuilder line = new StringBuilder();
        append(line, year(metadata));
        append(line, metadata.get("genre"));
        append(line, duration(metadata));
        String rating = clean(metadata.get("rating"));
        if (!rating.isEmpty()) append(line, "★ " + rating);

        StringBuilder result = new StringBuilder(line);
        String country = clean(metadata.get("country"));
        if (!country.isEmpty()) result.append("\nShteti: ").append(country);
        String director = clean(metadata.get("director"));
        if (!director.isEmpty()) result.append("\nRegjia: ").append(director);
        String cast = clean(metadata.get("cast"));
        if (!cast.isEmpty()) result.append("\nAktorët: ").append(cast);
        String plot = clean(metadata.get("plot"));
        if (plot.isEmpty()) plot = clean(metadata.get("description"));
        if (plot.isEmpty()) plot = clean(metadata.get("overview"));
        if (plot.isEmpty()) plot = clean(metadata.get("storyline"));
        if (!plot.isEmpty()) result.append("\n\nPërmbajtja\n").append(plot);
        return result.toString();
    }

    private String year(Map<String,String> metadata) {
        String value = clean(metadata.get("year"));
        if (!value.isEmpty()) return value;
        value = clean(metadata.get("release_date"));
        if (value.isEmpty()) value = clean(metadata.get("releasedate"));
        if (value.length() >= 4 && value.substring(0, 4).matches("\\d{4}")) return value.substring(0, 4);
        return value;
    }

    private String duration(Map<String,String> metadata) {
        String value = clean(metadata.get("duration"));
        if (value.isEmpty()) value = clean(metadata.get("runtime"));
        if (!value.isEmpty() && !value.matches("\\d+")) return value;
        String seconds = clean(metadata.get("duration_secs"));
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

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
