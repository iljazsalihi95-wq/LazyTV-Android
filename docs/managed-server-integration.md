# Managed server integration (2026-10-14)

The original Google Apps Script `servers/channels` contract remains the source for LazyIPTV, Krystal, MASTER and SARAY DARK. The app additionally discovers managed server spreadsheets from the existing CMS `SOURCES` registry. Only the registry spreadsheet identity is configured; individual server IDs, spreadsheet IDs, names and active flags are read at runtime. Adding a valid active source row changes the app menu on the next open without an APK rebuild.

Registered sources currently include SERVER_01 through SERVER_10. Separate similarly named documents and Server 11–20 were also discovered in Drive, but those are not registered in SOURCES and their anonymous CSV exports are unavailable. Authenticated full bounded ID-column checks of all seven tabs now confirm Server 11–20 catalog tabs contain headers only; they are empty and unregistered, with anonymous CSV access unavailable.

LIVE and 24_7 are loaded from their real named tabs, never Sheet1. 24_7 has its own category namespace and remains linear Live playback. Category names such as CINEMA or NETFLIX never imply movie content. All seven named tabs are read. Commands that explicitly identify linear Stalker channels (`localhost/ch/` or signed MAG stream references without movie/VOD markers) are recovered from incorrectly populated VOD/SERIES tabs into Live and deduplicated. Managed Movies/Series now use a dedicated native catalog entry. Movie detail resolves a fresh managed row at Play/Resume; episodes require explicit SERIES_ID/season/episode structure and use EpisodePlayer. Series parents with no episode records show an unavailable state and are never played as streams. Existing personal provider routing stays unchanged.

Rows are upserted by SERVER_ID + SOURCE_ID, with existing ID as a fallback only when SOURCE_ID is absent. Off/disabled rows and rows belonging to another server are discarded. Token changes and row ordering never change IDs; FavoritesStore is not cleared. INFO portal/MAC hints enrich only that server's rows. Original commands are retained, and managed Stalker sessions are keyed by managed server identity, portal and MAC. Each selection calls fresh create_link when the row provides a valid MAG reference; existing Stalker authentication retry remains bounded. No credential, full MAC, token or private URL is written to logs or this audit.

Country mapping is isolated in ManagedCountryResolver. Provider codes/country, category names/prefixes and channel-name fallback use the ISO resolver. Explicit Kosovo markers may correct an old AL import to XK; a mixed Albania + Kosovo category alone does not reclassify all Albanian channels.

Runtime remains NOT VERIFIED. In particular, Server 10's owner-reported physical playback failure is still open. Existing ServerTenPlaybackResolver, Premium access, Activation and device identity were not changed by this integration.

## Refresh milestone

The visible Live Refresh control reloads the selected managed registry identity and catalog. Generation guards reject stale catalog completion. The active channel keeps playing in the retained session, and stable IDs restore browser selection without resolving or starting playback again. Favorites/History stores are not cleared. Home RELOAD remembers the managed server selected in the current Home session, provided the personal active provider has not changed.

Managed on-demand player contexts are ephemeral and scoped to the managed server. They do not create/edit saved personal playlists. Signed movie links without an original VOD command are refreshed through the catalog, not rewritten as a Live channel command.

## Stable registry selection and Free TV

Server selection uses SERVER_ID, while display names come dynamically from SOURCES. Renaming a server or giving two servers the same display name cannot change its source identity, Favorites/History key, or sheet target. Original API and registry discovery are independent; one unavailable source reports a warning while the other remains usable. Home Live with no personal provider now opens the existing MASTER API catalog (observed 1,001 active rows), matching the owner system; the separate public FREE_TV playlist source remains available.

## Country metadata verification

The live original API returned MASTER country metadata SHQIPERIA, KOSOVA, MAQEDONIA E VERIUT, MALI I ZI, SERBIA, BOSNIA, KROACIA, TURQIA, ITALIA and GJERMANIA. Managed-only normalization now covers these actual values, ISO codes/names and Albanian definite forms generated from the ISO locale data. Existing EXYU regional grouping remains available. The global CountryResolver and Premium country parsing were not edited.

## Provider 24/7 and Kosovo evidence

Server 09 has 72 linear entries with explicit provider category 24/7 inside LIVE. A managed-only 24/7 section now selects these and actual 24_7 tab rows; Live selects the remaining linear rows. IDs stay unchanged and no Google Sheet rows are moved or duplicated. API type live24 is also respected. Real Movie/VOD items do not become linear solely from a 24/7 category title. Old AL may become XK from clear Kosovo category/country/channel evidence, while mixed Albania/Kosovo genres alone and authoritative non-AL provider countries remain intact.

Managed catalog navigation binds the existing official tile artwork. Long OK toggles provider-scoped Favorites; episode searches stay within the selected season. The adaptive column count follows the existing catalog browser. No STBEmu/IBO assets or new proprietary graphics are used.
