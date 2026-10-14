# Managed server integration (2026-10-14)

The original Google Apps Script `servers/channels` contract remains the source for LazyIPTV, Krystal, MASTER and SARAY DARK. The app additionally discovers managed server spreadsheets from the existing CMS `SOURCES` registry. Only the registry spreadsheet identity is configured; individual server IDs, spreadsheet IDs, names and active flags are read at runtime. Adding a valid active source row changes the app menu on the next open without an APK rebuild.

Registered sources currently include SERVER_01 through SERVER_10. Separate similarly named documents and Server 11–20 were also discovered in Drive, but those are not registered in SOURCES and their anonymous CSV exports are unavailable. They must not be reported as confirmed empty or playable.

LIVE and 24_7 are loaded from their real named tabs, never Sheet1. 24_7 has its own category namespace and remains linear Live playback. Category names such as CINEMA or NETFLIX never imply movie content. VOD and SERIES are inspected separately in the audit; UI integration is a subsequent step.

Rows are upserted by SERVER_ID + SOURCE_ID, with existing ID as a fallback only when SOURCE_ID is absent. Off/disabled rows and rows belonging to another server are discarded. Token changes and row ordering never change IDs; FavoritesStore is not cleared. INFO portal/MAC hints enrich only that server's rows. Original commands are retained, and managed Stalker sessions are keyed by managed server identity, portal and MAC. Each selection calls fresh create_link when the row provides a valid MAG reference; existing Stalker authentication retry remains bounded. No credential, full MAC, token or private URL is written to logs or this audit.

Country mapping is isolated in ManagedCountryResolver. Provider codes/country, category names/prefixes and channel-name fallback use the ISO resolver. Explicit Kosovo markers may correct an old AL import to XK; a mixed Albania + Kosovo category alone does not reclassify all Albanian channels.

Runtime remains NOT VERIFIED. In particular, Server 10's owner-reported physical playback failure is still open. Existing ServerTenPlaybackResolver, Premium access, Activation and device identity were not changed by this integration.
