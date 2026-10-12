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

## Remaining verified code gaps

Live browser still covers video with an overlay. EPG is queried before Live resolution returns and may delay selection. Movie and Episode entry points and position storage exist, but Movie Detail lacks an explicit Resume button. These require functional implementation and tests, followed by physical TV/mobile verification.
