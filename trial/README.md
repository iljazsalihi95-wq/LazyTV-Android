# LazyTV Trial streams

This directory defines the boundary for LazyTV's built-in TRIAL source.

- TRIAL is a separate source from user-added M3U, Xtream Codes and Stalker/Portal providers.
- User provider playlists remain in PlaylistStorage and are never overwritten by TRIAL.
- Private provider credentials, tokens and expiring stream URLs MUST NOT be committed to this public repository.
- Trial playback preserves the provider's real stream URL. MPEG-TS is selected by Media3 MIME handling when the source is TS; URLs are not blindly rewritten to end in .ts.
- The app-side entry point is TrialCatalogSource.
- ACTIVE/TRIAL/EXPIRED/BLOCKED access remains controlled by the activation backend.

The private trial manifest/stream resolver belongs on the server side. GitHub contains only the app integration and non-secret structure.
