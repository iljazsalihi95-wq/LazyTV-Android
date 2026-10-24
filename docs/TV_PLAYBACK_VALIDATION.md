# TV playback revision 614

Baseline: 46d7f7257523639b87e1f0ec8d2541f547fb8e1e. Owner reports that Build 895 installs but fails real Fire TV playback. This revision does not treat CI success as device validation.

## Changes

- LiveSources owns the selected M3U, Xtream, Stalker, managed, trial or Premium source. Premium has a separate Activity entry and its own link/session path. Shared LiveTvActivity renders the browser and common playback engine. Existing native STB remains separate. VOD provider navigation remains in the existing catalog/player paths.
- HTTP 458 requires a nonempty, different playback URL. Missing resolver results cannot fall back to the rejected URL. Premium refuses recovery without a portal identity, invalidates only the matching session, requests a fresh handshake/create_link and retains explicit provider UA/referrer/origin alongside fresh auth/cookies. One immediate 458 refresh is allowed. Existing provider policy bounds subsequent recovery.
- STB Live starts with get_ordered_list and publishes results page by page without calling get_all_channels. Session/authentication is retained. Duplicate pages stop enumeration, interruption cancels loading, and the existing 500-page ceiling remains.
- Series library request context distinguishes seasons and episodes even when provider rows use type=series. Explicit file/episode/season metadata remains authoritative.
- Live controls include pause/play when Media3 reports a seekable buffer, previous/next, EPG, list, Back and recording; controls hide after six seconds. STB controls no longer intercept horizontal focus movement as channel changes while controls are open. STB VOD OK only enters fullscreen when the video frame owns focus.
- Playback screen-on state is released on pause/stop/error/release as appropriate. No background wakelock is introduced. Existing VOD resume storage remains in place. STB user pause survives lifecycle resume.
- The native Live bridge skips LibVLC when it cannot represent required HTTP headers, using the existing full-header IJK path instead. Native engine availability errors are handled without swallowing fatal application crashes.
- Lifecycle and playback-phase logs contain class/engine/stage information. LazyTV-Crash logs exception class and stack frames, omitting Throwable messages and Intent extras. The normal Android uncaught exception handler still runs.

## Recording scope

RECORD uses Android ACTION_CREATE_DOCUMENT (SAF), no broad storage permission. The initial implementation records only progressive, clear 188-byte MPEG-TS packets over HTTP(S), retaining request headers. HLS/DASH manifests, known DRM metadata, scrambled TS packets, redirects and unsupported responses are rejected with “Ky transmetim nuk mbështet regjistrimin.” It does not decrypt, remux or record protected media. Provider connection limits and device document-picker availability may prevent recording. A persistent REC badge appears during recording. STOP, channel change, leaving the screen and Activity destruction stop the recording. The destination filename contains title and date/time. Unsupported/failed recording can leave an empty or partial user-selected file.

## Automated validation

Run `gradle --no-daemon testDebugUnitTest` before assembling. Tests cover provider routing, native callback fences, display state, resume policy, portal-session continuity, fresh Premium links, bounded HTTP recovery, preservation of fresh cookies/headers, STB ordered pagination and contextual seasons/episodes. Recording policy tests reject known protection, manifests and scrambled packets. These are JVM tests; they do not execute a Fire OS decoder, SurfaceView, D-pad or SAF picker.

## Required physical validation

Build/automated tests passed — physical Fire TV validation required.

No Fire TV crash log or connected Fire TV was available while implementing this revision. The root cause of the owner's crash is therefore not proven, and no claim that Fire TV is fixed is made.

Test on Fire TV and a phone:
1. HOME D-pad; Live > category > channel > fullscreen.
2. Pause/play where supported, next/previous, controls focus and Back.
3. Premium beyond the old 458 interval; verify a new create_link succeeds or one clear terminal error appears without retrying the old URL.
4. STB Live starts before the full catalog finishes; existing Movies and Series > Season > Episode still work.
5. Rotate the phone during playback, leave/resume the Activity, and switch channels rapidly.
6. Active playback prevents standby; stopping releases that state.
7. Record a permitted clear TS stream, stop, and play the saved file. Try an unsupported manifest and verify the player remains stable.
8. Verify VOD resume for the same provider/content after reopening.

On a crash, capture the concrete stack instead of changing unrelated code:

```
adb logcat -d -v time AndroidRuntime:E LazyTV-Crash:E LazyTV-Lifecycle:I LazyTV-Playback:I '*:S'
```

Provider accounts, credentials, portal addresses, MAC values, API keys, TLS verification, application ID and repository are unchanged.
