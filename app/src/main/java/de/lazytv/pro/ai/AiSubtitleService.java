package de.lazytv.pro.ai;

import android.content.Context;
import org.json.JSONObject;

/**
 * Cache-first subtitle translation coordinator used by Movie/Episode players.
 * Keeps AI transport, validation and persistent cache outside playback code so
 * Live/STB sessions are not touched by subtitle work.
 */
public final class AiSubtitleService {
    public static final String ALBANIAN = "sq";

    private final AiSubtitleCache cache;
    private final AiBackendClient backend;

    public AiSubtitleService(Context context) {
        if (context == null) throw new IllegalArgumentException("Context required");
        Context app = context.getApplicationContext();
        cache = new AiSubtitleCache(app);
        backend = new AiBackendClient(app);
    }

    /** Returns a validated cached Albanian document, or null when absent/invalid. */
    public AiSubtitleDocument cachedAlbanian(String mediaIdentity) {
        if (blank(mediaIdentity)) return null;
        String json = cache.get(mediaIdentity, ALBANIAN);
        if (json == null) return null;
        try {
            AiSubtitleDocument document = AiSubtitleDocument.parse(json);
            if (!ALBANIAN.equals(document.language) && !"alb".equals(document.language)) {
                cache.remove(mediaIdentity, ALBANIAN);
                return null;
            }
            return document;
        } catch (Exception invalid) {
            cache.remove(mediaIdentity, ALBANIAN);
            return null;
        }
    }

    /**
     * Translates timed source cues through the owner backend, validates the
     * returned timing document before use, then persists it for future playback.
     * The caller supplies timed cues only; stream URLs/credentials are not added.
     */
    public AiSubtitleDocument translateAlbanian(String mediaIdentity, JSONObject timedCueRequest)
            throws Exception {
        if (blank(mediaIdentity)) throw new IllegalArgumentException("Media identity required");
        AiSubtitleDocument cached = cachedAlbanian(mediaIdentity);
        if (cached != null) return cached;
        if (timedCueRequest == null) throw new IllegalArgumentException("Timed subtitle cues required");

        JSONObject request = new JSONObject(timedCueRequest.toString());
        request.put("target_language", ALBANIAN);
        JSONObject response = backend.translateSubtitles(request);
        String serialized = response.toString();
        AiSubtitleDocument document = AiSubtitleDocument.parse(serialized);
        if (!ALBANIAN.equals(document.language) && !"alb".equals(document.language)) {
            throw new IllegalArgumentException("AI subtitle language mismatch");
        }
        if (!cache.put(mediaIdentity, ALBANIAN, serialized)) {
            // Cache failure must not discard a valid translation for this session.
        }
        return document;
    }

    public void invalidateAlbanian(String mediaIdentity) {
        if (!blank(mediaIdentity)) cache.remove(mediaIdentity, ALBANIAN);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
