# Native IPTV behavior analysis

Baseline: c78e4868bc60b8ea5f3c8c454f0297993f77248f, CI 657 SUCCESS. Runtime not verified.

## Reference material actually inspected

STBEmu v2.0.23.01 XAPK contains a base application and ABI/language/density splits. Base APK SHA-256: e0504684f4717daa3c21a7d00fcd7a50eda61d2b23007a0980cc0616c932f1af.
IBO reference ibop.apk SHA-256: e4fbc9704da7b709d5d6e2cb2b0a55357d3626c7ec04017b2c477524a8f1b154.

STBEmu stores portal, MAC, model, user agent, serial/device fields and media engine in a provider profile. It exposes browser/native MAG interfaces and video-window geometry. Its bundled init page is empty: the complete provider portal JavaScript and provider-specific handshake/category/EPG implementation are loaded remotely, and cannot be reconstructed from this APK alone. No runtime trace of its portal was captured. LazyTV will implement an original native browser; its portal logic will not depend on a WebView.

IBO DEX instruction inspection finds a portal request routine which builds the handshake request, calls its HTTP helper, decodes a JSON js object and reads a token. The same helper family builds create_link with an encoded command and provider headers. Separate series selection routines build season/episode requests and update metadata. This is behavioral evidence, not imported implementation. No proprietary code, images, layouts, keys or assets are included in LazyTV.

## Protected Server 10 path

MainActivity.openServerTen -> LiveTvActivity.load -> SheetServerCatalogSource.load reads the configured Sheet CSV. LiveTvActivity.refreshServerTen reloads rows by stable ID -> ServerTenPlaybackResolver.resolve derives a portal command from a signed row -> provider session/create_link -> ResolvedStream. LivePlaybackSession retries through that resolver and skips redundant portal reconstruction for server10_fresh_link. Existing Sheet endpoint, columns, filtering, token behavior and credentials remain frozen.
ActivationGuard, ActivationApiClient, ActivationCache and DeviceIdentityManager are protected. CountryResolver is also frozen; StalkerCountryResolver is separate. Hashes are recorded in livetv-protected-baseline.json.

## Native Stalker boundary

CatalogEngine creates StalkerNativePortalClient exclusively for user Stalker playlists. ServerTenPlaybackResolver continues to create the original StalkerCatalogSource. The native client reuses the existing handshake/profile/cookie lifetime but separately handles numeric JSON auth refusals, endpoint variants, command encoding and fresh link response parsing. One initial create_link plus at most one authorization refresh retry; never store a resolved URL in a channel.
MAG profile parameters only come from the existing provider identity; new serial/device credentials are not fabricated. Provider-specific profile requirements still require an authorized test portal.

## Implemented milestones

1. Native-only Stalker endpoint/session/link resolution, including numeric auth expiry and one refresh retry.
2. Provider genres/keyed payloads/metadata are mapped by stable channel ID, ISO countries isolated to native Stalker.
3. One Live session survives Preview/Fullscreen/Back/browser, media and callback generations invalidate old selections.
4. Browser occupies categories/channels/large preview columns, not a video overlay; same PlayerView survives configuration changes.
5. Native Previous/Next/Favorite/EPG/List/Back controls; optional EPG on a separate executor with generation checks and actual current/next timestamps.
6. One Xtream URL builder for API/catalog/series details/EPG/M3U/stream URLs, preserving scheme and nondefault ports and encoding credentials.
7. Movie Play/Resume seeks and saves the active Exo/VLC/IJK engine, not an inactive Exo instance. Resume keys retain the previous provider/content contract.
Continue Watching is a separate native Movies catalog filter, sorted by actual saved timestamp and showing saved position/progress. It uses the same keys and Resume action; expired/completed records are excluded by PlaybackResumeStore. Movie retries retain the active-engine position.

8. Series containers cannot play; episode metadata now reaches StreamItem; native details/artwork and per-episode Play/Resume/progress.
9. M3U EXTINF titles respect the first unquoted separator, retain provider metadata/headers, and build seasons/episodes from explicit metadata or clear SxxExx/1xNN markers. Unknown season/episode numbers remain unknown. SAF requires persisted URI read permission; no local-file copying. Existing Live M3U IDs remain unchanged.

API 23 compatibility uses core-library desugaring (AGP 8.7.3, desugar_jdk_libs 2.1.4) for collection/stream APIs already used in this application. Official configuration reference: https://developer.android.com/studio/write/java8-support ; dependency compatibility: https://github.com/google/desugar_jdk_libs/blob/master/CHANGELOG.md . Min SDK remains 23.

## Verification limits

CI tests/builds are not physical playback verification. No authorized Stalker portal runtime trace was supplied; actual provider genre coverage, profile requirements, codecs, TV D-pad focus, surface transitions, resumed seeking and token-expiry playback still require owner tests. The specific previously reported 685 channels were not downloaded or counted from a live portal. Fixture tests demonstrate metadata enrichment rather than a fabricated count.
