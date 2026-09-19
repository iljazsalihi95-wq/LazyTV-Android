# LazyTV PRO — Android

Package: `de.lazytv.pro`

## Implemented in this build
- Local playlist/server management
- M3U URL
- Local M3U document selection with persisted URI permission
- Xtream Codes configuration: name, server URL, username, password
- Stalker/Portal configuration: name, portal URL, MAC/device identifier
- Multiple saved configurations
- Edit, delete and select/connect configuration
- Local persistence with SharedPreferences/JSON

`Connect` currently validates and marks the selected configuration active. Playback/catalog/network protocol handling belongs to the later player/provider implementation and is intentionally not faked here.

No private server credentials, API keys, MAC addresses or playlists are embedded in source code.


## IPTV Catalog Engine
Supports M3U URL/local file parsing, Xtream Codes API catalog loading, and Stalker/Portal handshake/catalog loading. Network operations run off the UI thread. No provider credentials are hardcoded.

## Build compatibility

- Android Gradle Plugin: 8.7.3 (API 35 support)
- Required Gradle for AGP 8.7.x: 8.9+
- Java source/target compatibility: 17
- compileSdk / targetSdk: 35
- minSdk: 23
- AndroidX Media3: 1.5.1 (`media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui`)
- Clear-text HTTP is enabled at application level because user-configured IPTV endpoints may use `http://`; no provider/domain is hardcoded.
- Application backup is disabled so locally stored playlist/server configuration is not included in Android backup.

## Device activation
The launcher is `ActivationGateActivity`. Device identity is derived from `Settings.Secure.ANDROID_ID`, hashed and exposed as a LazyTV Device ID, a MAC-style display identifier (not a physical network MAC), and deterministic LazyTV serial. No IMEI, Wi-Fi MAC, location, contacts, advertising ID, SSID or installed-app inventory is used.

Set the production HTTPS activation service in the single `ACTIVATION_BASE_URL` BuildConfig field in `app/build.gradle`. An empty value intentionally produces `Activation service not configured`; it never grants activation.

Contract: `POST /api/v1/device/check` with `device_id`, `serial`, `app_version`; response must contain one of `ACTIVE`, `INACTIVE`, `EXPIRED`, `BLOCKED`, plus `server_time`, with optional `expires_at` and public `message`.

A successful ACTIVE check is cached with an Android Keystore HMAC. Offline access is limited to 24 hours and only while the same boot's monotonic clock can prove the elapsed interval; reboot/offline requires a fresh server check. EXPIRED/BLOCKED/INACTIVE never receive offline grace.

See `backend-reference/` for a minimal deployment reference. Production activation must use HTTPS with normal certificate/hostname validation.
