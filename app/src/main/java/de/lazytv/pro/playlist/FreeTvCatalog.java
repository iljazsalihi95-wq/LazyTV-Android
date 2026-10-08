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

        // Public direct HLS endpoint currently exposed for free viewing by RTV Pendimi.
        // Keep website/embed-only broadcasters out until a direct authorized media endpoint
        // is verified; LazyTV PRO remains a native player and does not use WebView.
        channels.add(new Channel(
                "rtv-pendimi",
                "RTV Pendimi",
                Region.ISLAMIC,
                "https://www.rtvpendimi.com:19360/tvpendimi/tvpendimi.m3u8",
                ""));

        // News 24 Albania stream is served from the broadcaster group's balkanweb.com host.
        channels.add(new Channel(
                "news24-albania",
                "News 24 Albania",
                Region.ALBANIA,
                "https://tv.balkanweb.com/news24/livestream/playlist.m3u8",
                ""));

        // ABC News Albania HLS is served from the broadcaster's abcnews.al domain.
        // The endpoint has also been accepted as a public stream by iptv-org.
        channels.add(new Channel(
                "abc-news-albania",
                "ABC News Albania",
                Region.ALBANIA,
                "https://tv2.abcnews.al/live/abcnews/playlist.m3u8",
                "https://i.imgur.com/q5pjJ2m.png"));

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
