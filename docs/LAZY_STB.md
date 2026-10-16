# LAZY STB

Independent native module under `de.lazytv.pro.stb`.

## Boundaries

The LAZY STB button opens `LazyStbActivity`, not a playlist editor or a Live activity.
Profiles, favorites, protocol DTOs, transport, EPG and playback have private STB ownership.
No dependency on LiveTvActivity, LivePlaybackSession, LazyTV catalog DTOs or WebView.
Xtream, M3U, Premium, Activation and managed server implementations are unchanged.
The manifest and the existing playlist-manager STB button are the only integration hooks.
Older ordinary portal playlists retain their previous route; the dedicated LAZY STB entry is independent.

## Flow

Profile Name + Portal URL + MAC -> bounded endpoint discovery -> handshake -> token ->
profile -> optional account data -> genres -> channels -> selection-time create_link -> preview.
Explicit endpoint is tried first. Context, scheme and port are preserved. Discovery tries at most
six distinct known addresses. HTTP 404 does not route into another module.
Catalog fallback uses ordered pages only for an empty/unsupported all-channels response.
Original cmd, channel id/name/logo/EPG id, genre id/title and country metadata are retained.
No resolved playback URL is persisted. One private transport instance owns cookies and token.
Session authentication is reused for 15 minutes or until rejected; auth failure retries once.
API cookies, MAG headers and bearer are retained. Playback receives session headers only for
the same scheme/host/port. Referrer respects the portal installation context.
EPG network calls do not serialize create_link requests. Background mapping avoids repeated
ISO-name scanning during D-pad navigation. Logo work/size/cache are bounded.

## UI and playback

Large preview above metadata/controls; countries 16%, categories 24%, channels 60% below.
Country display uses codes; abbreviations are display-only. Full channel names are retained.
One LazyStbPlaybackSession owns Media3 ExoPlayer; HLS/DASH/TS are supported through Media3.
VLC, IJK/FFmpeg and Android MediaPlayer are independently selectable. Auto tries Media3,
VLC, IJK and Android once each; it stops after that finite sequence. Engine callbacks
have a separate attempt fence as well as the channel generation fence. IJK and Android
receive the resolved URL and playback headers. Existing global engines
are not changed. VLC receives the resolved URL, user agent and referrer; portals requiring
additional protected native-engine headers require provider/device runtime verification.
Preview/fullscreen only changes visibility and frame size; no new Activity, player or link.
A channel selection increments an independent generation fence. Old link/EPG callbacks are ignored.
Playback 401/403 permits one fresh-link reload per selection. Buffering startup has a 25-second
watchdog and bounded fallback. An error permits an explicit user retry; there is no retry loop.

D-pad selects country/category/channel, OK on preview enters fullscreen, OK in fullscreen
shows controls. Back returns fullscreen -> preview -> channel list -> profiles.
Channel +/- and previous/next media keys replace media in the same session.
Favorites use a separate private preference namespace and profile + portal + channel id.
EPG is optional, async and guarded, with current/next times/title/progress when available.

## Evidence and limits

The supplied STBEmu APK was inspected only for behavior (viewport/fullscreen surface sizing).
Its remote portal scripts are not bundled, so static inspection is not proof of any specific
provider's authentication requirements. No proprietary code, branding or assets were copied.

JUnit fixtures cover normalization/discovery, handshake/token, genre/channel fields, original cmd,
fresh links, cookies, bounded auth retry, nonblocking EPG, ISO aliases/display abbreviations,
EPG/progress, stale generations, stable previous/next and favorite identity separation.
CI establishes build/test success. Physical playback, D-pad/surface behavior and compatibility
with a real Portal/MAC remain NOT VERIFIED until device testing. There is no claim of support
for every codec or proprietary portal/device-binding extension.
