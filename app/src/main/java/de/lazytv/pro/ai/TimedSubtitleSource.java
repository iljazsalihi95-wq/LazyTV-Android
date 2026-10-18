package de.lazytv.pro.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts an existing SRT/WebVTT subtitle document into the timed cue payload
 * accepted by the owner AI backend. Audio is deliberately not involved here:
 * existing subtitle tracks are always preferred before transcription fallback.
 */
public final class TimedSubtitleSource {
    private static final Pattern TIMING = Pattern.compile(
            "^\\s*((?:\\d{1,2}:)?\\d{1,2}:\\d{2}[,.]\\d{3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{1,2}:\\d{2}[,.]\\d{3})(?:\\s+.*)?$"
    );
    private static final int MAX_INPUT_CHARS = 4_000_000;

    private TimedSubtitleSource() {}

    public static JSONObject parse(String text, String sourceLanguage) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Subtitle source missing");
        if (text.length() > MAX_INPUT_CHARS) throw new IllegalArgumentException("Subtitle source too large");

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        JSONArray cues = new JSONArray();

        for (int i = 0; i < lines.length; i++) {
            Matcher timing = TIMING.matcher(lines[i]);
            if (!timing.matches()) continue;

            long start = timestampMs(timing.group(1));
            long end = timestampMs(timing.group(2));
            if (start < 0 || end <= start) continue;

            StringBuilder body = new StringBuilder();
            int j = i + 1;
            while (j < lines.length && !lines[j].trim().isEmpty()) {
                // Stop safely if malformed input starts the next cue without a blank line.
                if (TIMING.matcher(lines[j]).matches()) break;
                String line = cleanCueText(lines[j]);
                if (!line.isEmpty()) {
                    if (body.length() > 0) body.append('\n');
                    body.append(line);
                }
                j++;
            }
            if (body.length() == 0) continue;

            JSONObject cue = new JSONObject();
            cue.put("index", cues.length() + 1);
            cue.put("start_ms", start);
            cue.put("end_ms", end);
            cue.put("text", body.toString());
            cues.put(cue);
            if (cues.length() > AiSubtitleDocument.MAX_CUES) {
                throw new IllegalArgumentException("Too many subtitle cues");
            }
            i = Math.max(i, j - 1);
        }

        if (cues.length() == 0) throw new IllegalArgumentException("No timed subtitle cues found");
        JSONObject request = new JSONObject();
        String language = sourceLanguage == null ? "" : sourceLanguage.trim().toLowerCase(Locale.ROOT);
        if (!language.isEmpty()) request.put("source_language", language);
        request.put("format", normalized.trim().startsWith("WEBVTT") ? "vtt" : "srt");
        request.put("cues", cues);
        return request;
    }

    private static String cleanCueText(String line) {
        String value = line == null ? "" : line.trim();
        if (value.isEmpty()) return "";
        // Remove common WebVTT voice/class tags while retaining their visible text.
        return value.replaceAll("<[^>]+>", "").trim();
    }

    private static long timestampMs(String raw) {
        if (raw == null) return -1;
        String[] parts = raw.replace(',', '.').split(":");
        try {
            double seconds = 0d;
            for (String part : parts) seconds = seconds * 60d + Double.parseDouble(part);
            return Math.round(seconds * 1000d);
        } catch (Exception invalid) {
            return -1;
        }
    }
}
