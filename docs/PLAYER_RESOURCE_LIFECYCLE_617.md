# Background playback resource correction (617)

Base: 483c7011cba0b7ffec0dc1ecf8f1d265caf8ca82, version 616 / CI 899.

## Reproduced defects

LivePlaybackSession.pauseForBackground only called player.pause(). The Activity could
remain in the back stack while its Media3 loader retained an active HTTP connection.
A real HTTP fixture confirmed the connection was still open five seconds after leaving
the player. The player remained BUFFERING instead of becoming IDLE.

A second path was a delayed provider resolve or fresh-link recovery completing after
onPause. The renderer's callback correctly rejected destroyed Activities, but a paused
Activity is still alive. LivePlaybackSession.apply unconditionally called prepare, starting
hidden loading even though foreground was false. Merely withholding player.play did
not stop preparation/loading.

Three regression tests failed against the unchanged 616 implementation: network cancellation,
late resolve in background and paused-player background state. This is a demonstrated
resource retention/loading defect. It is NOT a captured proof of the owner's physical
process termination, native crash, ANR or low-memory kill. No device log is available.

## Correction

- onPause now pauses and stops playback, cancelling loading and returning Media3 to IDLE.
  It retains the same ExoPlayer instance, current media and position, and cancels the
  stable-playback timer. onPause/onStop remain idempotent.
- A late resolve/create_link sets the fresh media item but defers prepare until foreground.
  No hidden decoder/loader is started by that callback.
- Foreground return prepares the retained media once, preserving manual pause/play intent.
  A previous error/refused URL goes through bounded recovery; a pending recovery is not
  raced by preparing the old item. Resume no longer resets the retry budget.
- Fullscreen still swaps native PlayerViews on one ExoPlayer at android.R.id.content.
  Source routing, headers/auth, Premium 458 policy, STB, Movies and Series are unchanged.

## Executed validation

Complete local Android suite: 241 tests, zero failures/errors/skips.

- Actual HTTP socket closes after background transition; Media3 becomes IDLE.
- Delayed channel resolve stays IDLE until return, then prepares on the same engine.
- Manual pause remains paused; a pending seek position and player identity are retained.
- A fresh 458 recovery result completing in background waits for foreground.
- Populated catalog rendering/navigation on API 25, 28 and 30: all four provider layouts,
  country/category/channel rows, OK, next/previous, fullscreen, BACK and actual layout
  qualifier change from landscape to portrait, with the same player.
- Existing real HTTP 458 fresh-session tests and provider/lifecycle/STB tests remain green.

Robolectric does not validate physical hardware decoders, vendor native libraries or audio
output. The HTTP test establishes loader cancellation, not a measured reduction in native
decoder memory. Physical Fire TV/Samsung launch, repeated provider switches and prolonged
playback still require validation. The installed app version and its original signing key
are not available from this environment. Public updates remain gated on stable signing.

Build/automated tests passed — physical Fire TV validation required.
