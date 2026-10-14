# Managed server integration (2026-10-14)

The original Google Apps Script `servers/channels` contract remains the source for LazyIPTV, Krystal, MASTER and SARAY DARK. The app additionally discovers managed server spreadsheets from the existing CMS `SOURCES` registry. Only the registry spreadsheet identity is configured; individual server IDs, spreadsheet IDs, names and active flags are read at runtime. Adding a valid active source row changes the app menu on the next open without an APK rebuild.

Registered sources currently include SERVER_01 through SERVER_10. Separate similarly named documents and Server 11–20 were also discovered in Drive, but those are not registered in SOURCES and their anonymous CSV exports are unavailable. Authenticated full bounded ID-column checks of all seven tabs now confirm Server 11–20 catalog tabs contain headers only; they are empty and unregistered, with anonymous CSV access unavailable.

LIVE and 24_7 are loaded from their real named tabs, never Sheet1. 24_7 has its own category namespace and remains linear Live playback. Category names such as CINEMA or NETFLIX never imply movie content. All seven named tabs are read. Commands that explicitly identify linear Stalker channels (`localhost/ch/` or signed MAG stream references without movie/VOD markers) are recovered from incorrectly populated VOD/SERIES tabs into Live and deduplicated. Real on-demand UI integration is a subsequent step.

Rows are upserted by SERVER_ID + SOURCE_ID, with existing ID as a fallback only when SOURCE_ID is absent. Off/disabled rows and rows belonging to another server are discarded. Token changes and row ordering never change IDs; FavoritesStore is not cleared. INFO portal/MAC hints enrich only that server's rows. Original commands are retained, and managed Stalker sessions are keyed by managed server identity, portal and MAC. Each selection calls fresh create_link when the row provides a valid MAG reference; existing Stalker authentication retry remains bounded. No credential, full MAC, token or private URL is written to logs or this audit.

Country mapping is isolated in ManagedCountryResolver. Provider codes/country, category names/prefixes and channel-name fallback use the ISO resolver. Explicit Kosovo markers may correct an old AL import to XK; a mixed Albania + Kosovo category alone does not reclassify all Albanian channels.

Runtime remains NOT VERIFIED. In particular, Server 10's owner-reported physical playback failure is still open. Existing ServerTenPlaybackResolver, Premium access, Activation and device identity were not changed by this integration.

## Refresh milestone

The visible Live Refresh control reloads the selected managed registry identity and catalog. Generation guards reject stale catalog completion. The active channel keeps playing in the retained session, and stable IDs restore browser selection without resolving or starting playback again. Favorites/History stores are not cleared. Home RELOAD remembers the managed server selected in the current Home session, provided the personal active provider has not changed.
