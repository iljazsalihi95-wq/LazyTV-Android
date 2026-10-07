package de.lazytv.pro.playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Curated catalog for streams that broadcasters intentionally expose for free viewing.
 *
 * Keep this catalog separate from user supplied M3U/Xtream/Stalker sources. A channel
 * must only be enabled after its broadcaster-authorized direct stream endpoint has
 * been verified. Web pages, embeds and scraped/pirated IPTV URLs do not belong here.
 */
public final class FreeTvCatalog {
    public enum Region {
        ALBANIA("Albania"),
        KOSOVO("Kosovo"),
        NORTH_MACEDONIA("North Macedonia"),
        ISLAMIC("Islamic"),
        RADIO("Radio"),
        INTERNATIONAL("International");

        public final String label;
        Region(String label) { this.label = label; }
    }

    public static final class Channel {
        public final String id;
        public final String name;
        public final Region region;
        public final String streamUrl;
        public final String logoUrl;

        public Channel(String id, String name, Region region, String streamUrl, String logoUrl) {
            this.id = id;
            this.name = name;
            this.region = region;
            this.streamUrl = streamUrl;
            this.logoUrl = logoUrl;
        }
    }

    private static final List<Channel> CHANNELS;

    static {
        List<Channel> channels = new ArrayList<>();
        // Add only verified broadcaster-authorized DIRECT media endpoints here.
        // Do not add website/embed URLs: LazyTV PRO is a native player and uses no WebView.
        CHANNELS = Collections.unmodifiableList(channels);
    }

    private FreeTvCatalog() {}

    public static List<Channel> all() {
        return CHANNELS;
    }

    public static List<Channel> byRegion(Region region) {
        List<Channel> result = new ArrayList<>();
        for (Channel channel : CHANNELS) {
            if (channel.region == region) result.add(channel);
        }
        return result;
    }
}
