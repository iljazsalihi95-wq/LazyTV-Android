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

## Recovery after owner playback failure (2026-10-13)

Owner reports no Stalker playback and no Premium fresh-token recovery. Runtime is failed on the owner's device, not verified fixed. Native Stalker previously selected an independent endpoint/cookie store for create_link after inherited handshake/profile: a JSON-valid decoy endpoint could win despite authentication at server/load.php. Native requests now use the same authenticated endpoint and cookie store, and playback headers are snapshotted after create_link updates cookies. Bare host/port normalization also runs before handshake. Regression fixture exercises the decoy portal.php and rotating profile/playback cookies.

The shared StalkerCatalogSource used by managed Server 10 ignored numeric JSON 401/403 in HTTP 200 and could ignore a refused profile. Its auth response handling now triggers one handshake refresh for create_link, stops after a second refusal, and rejects refused profile before playback. This necessary Server 10 session change is explicitly authorized by the owner's failure report. Sheet endpoint/loading, stable identity, Activation, device identity, Premium access and global CountryResolver are unchanged. Real ServerTenPlaybackResolver fixture tests ensure a fresh signed URL, new Bearer/cookie, original sheet row retention, and no stale URL returned on repeated refusal.

IBO DEX Lw7/fa methods a/b encode cmd, execute portal HTTP and read js.cmd; method e reads js.token after handshake. STBEmu exposes MAG profile identity and video viewport; full provider portal JavaScript is fetched remotely. No proprietary implementation or assets copied, no WebView. Actual provider diagnostics/credentials and physical playback still required; these fixtures do not prove that a particular real provider supports its assigned MAC/profile/channel command.

Endpoint cache is now keyed by normalized full portal URL, not just origin. A two-portal/one-host regression fixture verifies that a cached tenant endpoint cannot replace the second configured original portal. No endpoint constants or private provider credentials were changed.

## Native player separation and transport coverage (2026-10-13 evening)

XtreamLivePlayerActivity, StalkerLivePlayerActivity and IptvLivePlayerActivity are distinct provider entry points. Connect opens the selected provider directly, and an incoming replacement provider closes the previous activity/session rather than silently reusing its credentials. Managed Server 10 retains its existing entry and resolver. No provider credentials, MAC defaults or private hosts were copied or changed.

Live previously had only Media3 even though the application packaged VLC/IJK for VOD. Live now tries a persistent IJK software decoder once on Media3 format/codec failure, retains all validated portal headers and moves the same native surface with the retained preview frame. HTTP account/network/DRM refusals do not trigger decoder fallback. Only one engine plays at a time; fullscreen does not create an engine or resolve again. Native retries use the same provider resolver/generation checks and reset their transient-error budget after stable playback, while Stalker keeps its existing per-selection guard. Media3 DASH, SmoothStreaming and RTSP modules are included alongside HLS/progressive playback. This is not a claim of universal format/DRM support.

Owner legacy reference Lazytv (1).apk contains LazyTV_PREMIUM_FINAL-1.html. Offline inspection finds native Android playback bridge, API catalog refresh and Server 03/05 create_link calls through configured LazyMapper Apps Script backends. It contains no direct handshake implementation in that HTML. These managed backend routes are not the same as a personal Xtream login or personal Stalker portal. The public original catalog API is now connected natively through ManagedBackendCatalogSource, with separate managed source identity and fresh catalog lookup for expiring URL references. No proprietary HTML/UI or embedded private default MAC was imported. Server 03/05 CMD proxy variants still require their provider identities from authorized configuration; no missing MAC/default was fabricated. Existing managed Server 10 endpoints/row loading remain unchanged. Runtime remains failed by owner report until physical tests and authorized provider/API diagnostics succeed.

Original public catalog API was probed without MAC/token submission: HTTP 200, ok=true, four servers. Channels responses contain Krystal 17,936, LAZI IPTV 3,728, MASTER 998 and SARAY DARK 885 rows. Only field names/counts were recorded; private stream URLs were not logged. Native Servers menu now reads this existing endpoint, and selected managed catalogs load from action=channels&server=... with provider identity separate from personal playlists and Server 10. For expiring signed URLs, selection requests the latest row by stable ID; retry forces a new lookup. This is fresh catalog data, not proof that the backend itself rotated every provider play_token. No account identity is sent to a portal by these catalog requests.
