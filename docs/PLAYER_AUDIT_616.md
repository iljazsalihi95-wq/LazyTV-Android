# Player audit 616

Base: ae7183e6cfad6cb8eb17a2df94f32aa93eee2506 (615, CI 898).
The owner's report that the app still exits on Fire TV remains an unresolved physical
failure. A passing JVM test cannot establish its native/device cause.

## Inspected entry and lifecycle paths

- MainActivity: HOME Premium opens PremiumLivePlayerActivity. The registered SERVER_10
  selection previously opened LiveTvActivity; it now opens PremiumLivePlayerActivity
  with its original MANAGED_BACKEND/server_name extras. SERVER_20 keeps its existing route.
- PremiumModule: the managed entry keeps ManagedBackendCatalogSource, country grouping,
  24/7 filtering, catalog navigation and MANAGED_BACKEND|SERVER_10 favorite/EPG identity.
  The normal Premium HOME entry keeps its existing sheet source. No server configuration
  or catalog data is migrated/replaced.
- PremiumLivePlayerActivity, XtreamLivePlayerActivity, IptvLivePlayerActivity and
  LiveTvActivity extend Activity directly. Their modules own sources. STB/Stalker remain
  separate. Shared NativeLiveScreen is a composed renderer, not their Activity superclass.
- All eight portrait/landscape provider layouts contain the IDs referenced by the renderer.
  Preview/fullscreen uses ReferenceFullscreenHost at android.R.id.content; no reparenting
  or unsafe LayoutParams casts. The previous view is detached before attaching the same player.
- onPause/onStop pause the existing session; onDestroy releases it and cancels workers.
  Configuration rebind retains the same engine. OK/ENTER only acts on player/native focus;
  HOME behavior is tested as pause/stop/start/resume, not synthetic system-key injection.
- MainActivity artwork is small (320x160 sheet, 192x192 logo). This inspection supplies
  no evidence for claiming those assets caused an OutOfMemoryError.

## Concrete 458 gap and correction

615's application-level recovery runs only after Media3 reports onPlayerError. Its default
load policy can retry a refused HTTP 458 before this callback, despite the application-level
stale URL guard. FreshLinkLoadErrorPolicy returns TIME_UNSET for 458 only for Premium and
managed SERVER_10. Media3 hands the refusal to the existing bounded session recovery
instead of retrying the old request internally. Other providers/statuses retain defaults.

Managed SERVER_10 now owns a ServerTenPlaybackResolver.OwnerSession. A refreshable portal
row uses that owner for initial create_link and for session invalidation/fresh handshake/link.
Managed SERVER_20, personal playlists and STB auth/session paths are unchanged.
Rejected URL/token checks and the once-per-selection recovery budget remain in place.

The new HTTP regression test uses an actual Media3 request against a local fixture portal:
handshake, profile, account info, create_link, old stream HTTP 458 and fresh stream HTTP 200.
It asserts exactly one old request, one fresh request, two handshakes/create_link calls,
fresh cookies, one recovery and unchanged ExoPlayer identity. It does not decode real video.

## Reference provenance

The four owner-provided ChannelsActivity references listed in REFERENCE_PLAYER_TRANSPLANT.md
supplied the native one-player/two-view handoff and content-root fullscreen mechanism.
They use old ExoPlayer2 APIs; LazyTV maps that mechanism to Media3. Their WebView/JS catalog
and embedded credentials are excluded. No proprietary IBO source/assets are used.

The references do not contain player.release/onDestroy cleanup. LazyTV lifecycle cleanup,
provider separation, fresh-session recovery, recording, VOD resume, XMLTV and update handling
are LazyTV implementations/adaptations, not transplanted reference functions. The new 616
load policy and managed routing/session correction are newly implemented here.

## Validation limits

Automated entry tests open real Premium/managed/Xtream/M3U Activities on API 25/28 in
portrait and landscape, exercising OK, fullscreen, BACK and background/foreground with
one retained player. Existing tests cover STB pages, series classification, resume and
bounded stale-link refusal. This is not physical Fire TV/Samsung validation.

Physical requirements remain: actual launch/decoder/audio, prolonged playback across the
provider's expiry window, real-provider 458, channel changes, native process termination,
STB Movies/Series/episodes, recording/storage and signed installation compatibility.
No device is connected to this environment. Do not label the owner's crash fixed.

Public Auto Update still requires the repository's existing production signing and activation
configuration. Do not replace its keys or publish a random debug signer as a compatible update.

## Executed automated checks

Local complete Android suite: 233 tests, 0 failures/errors/skips. Actual entry tests
ran on API 25/28 in both orientations. A controlled test disabled only the new loader
policy: both HTTP integration tests failed with expected 1 old request, actual 4.
The policy was restored before final validation. PayPal/webhook suite: 6 passed.
Existing full lint debt documented for 615 is not represented as a green lint result.

Build/automated tests passed — physical Fire TV validation required.
