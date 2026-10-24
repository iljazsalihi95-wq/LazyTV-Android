# Native playback transplant (615)

Baseline inspected: f436c0edf01a114d9b8373a12afe06cd617f5fad. No old commit was reverted.

## Owner's source references

All four saved files were read: SAFE_BACKUP_ChannelsActivity_WORKING_13-09-2026.txt,
ChannelsActivity_FIRETV_FINAL_SINGLE_NAV.txt, ChannelsActivity_IBO_NATIVE_PREMIUM_V1.txt,
and ChannelsActivity_IBO_NATIVE_PREMIUM_V1-1.txt. They are user-supplied ChannelsActivity
implementations, not IBO proprietary source. Their WebView/JS catalogs and embedded
configuration are excluded. No credentials were copied into the repository.

ReferenceFullscreenHost transplants their native mechanism onto the existing Media3 engine:
android.R.id.content owns a fullscreen FrameLayout; two native PlayerViews exchange the
same ExoPlayer by detaching the old view before attaching the target. The reference's
ExoPlayer2 API calls map to Media3 without introducing a second engine. Native controls
retain channel navigation/EPG/record and automatic hiding. No preview parent or layout
parameters are cast or moved for fullscreen. The newer baseline's one-player change is retained.

PremiumLivePlayerActivity, XtreamLivePlayerActivity, IptvLivePlayerActivity and managed
LiveTvActivity each extend Activity directly and compose NativeLiveScreen. Each creates
its own module, source, layout and catalog adapter. NativeLiveScreen renders native views;
LiveModule implementations own provider selection, resolver and catalog navigation.
StalkerLivePlayerActivity and the STB portal/browser/library remain separate.
Existing CatalogActivity / native VOD players and resume storage are reused.

## Crash evidence and limits

HOME had a deferred focus callback using x.getParent().requestChildFocus(x,x) without
checking the parent again. A detached tile therefore has a concrete null-dereference path.
HomeFocus checks attachment, current parent and focus at execution time. Regression tests
exercise the actual HOME tile's queued callback while detaching it.
The original callback was restored temporarily for a controlled regression: it failed
with NullPointerException on API 25 and 28 at MainActivity.lambda$tile$13; the reviewed
callback passes the same detachment scenario.
This does NOT prove this was the sole cause of the owner's physical Fire TV termination.
No physical device trace is available. The previous fullscreen casts were also removed
from the routed activities; they are not labeled a proven ClassCastException.
A sanitized cause-chain/stack report is now persisted locally and readable/copyable in
Settings after reopening. Native process crashes/system kills may still need device logs.

## Recovery and STB Live

458 has a separate once-per-selection budget, unaffected by the stable-playback counter
reset. Refused URLs are remembered for the activity session and cannot be handed back to
ExoPlayer on re-selection. Fresh URLs cannot reuse an old play_token, even if another query
parameter changes. Premium with portal identity obtains create_link before initial playback;
458 invalidates only that portal/MAC session and requests a fresh handshake/link.
Provider headers remain attached. Logs use a hashed channel reference and host only.

STB ordered pagination keeps the existing authentication. Browser appears after genres,
progressive pages update it, and an empty first page now emits completion rather than
leaving the loading label forever. Existing Movies/Series authentication is unchanged.

Servers opens configured Premium and local playlists immediately, with a persisted remote
registry snapshot. Personal selections open their routed player directly instead of recreating
HOME. Background registry fetch does not block selection or add an endless loading row.
M3U XMLTV metadata is retained when supplied; streaming parsing caches real now/next data.

## Auto Update and production prerequisite

On HOME startup AppUpdater checks the public latest GitHub Release update.json. UPDATE /
ME VONE are native TV-focusable controls. APK download checks completion, SHA-256,
package name, exact versionCode and matching signer certificates before granting a narrow
FileProvider content URI to the system installer. Installation always requires Android's UI.
No broad storage permission, silent install or TLS bypass is used.

The release workflow builds/publishes a production APK and manifest ONLY if the EXISTING
stable signing secrets and activation endpoint are configured. It never generates a replacement
key and never publishes a randomly signed debug APK as a compatible update. Baseline CI
897 reports SIGNING_KEYSTORE=NOT_CONFIGURED and ACTIVATION_BASE_URL=NOT_CONFIGURED.
Therefore public automatic updates are not operational until that existing configuration is
supplied. Existing installed builds with a different signer cannot be updated in place.
The normal debug test APK remains available as a CI artifact.

## Required physical validation

HOME Live/Premium/Servers/Playlists; Xtream/M3U routing; DPAD CENTER/ENTER/BACK;
preview/fullscreen/return; one audible playback session; repeated background/foreground;
real decoder behavior; Premium lasting past its former 458 threshold; fresh-link recovery;
STB Live/Movies/Series/Season/Episode; keep-screen-on; supported clear stream recording;
and an actual signed update installation on Fire TV and phone.

Automated coverage includes HOME/provider routing, detached focus, native preview/fullscreen,
configuration/lifecycle, bounded fresh-link recovery, provider auth isolation, ordered/empty
STB pages, season/episode classification, resume policy and M3U/XMLTV parsing.
Two existing EPG decoders also used String.chars (API 24) despite minSdk 23; identical
character loops remove that API-level crash risk without changing decoding semantics.
Local Android unit suite: 220 tests, 0 failures. A controlled restoration of the original
focus callback failed twice (API 25/28); the reviewed callback passes.
Full Lint is not green: baseline f436c0e had 111 errors (87 opt-in annotations,
22 UI constant annotations, 2 NewApi). The new-code compatibility errors were corrected;
legacy annotation/constant findings remain and are not hidden with a baseline or suppression.

Build/automated tests passed — physical Fire TV validation required.
