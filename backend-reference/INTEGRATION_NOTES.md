# Existing LazyTV system integration notes

Verified read-only on 2026-09-19 from `LazyTV_USERS_SUBSCRIPTIONS`:

- CLIENTS headers: CLIENT_ID, DEVICE_ID, MAC, DEVICE_KEY, NAME, STATUS, START_AT, END_AT, PACKAGE, MAX_DEVICES, CREATED_AT, LAST_SEEN, NOTES.
- Existing observed statuses include ACTIVE and PENDING.
- SETTINGS includes TRIAL_DAYS=7, SUBSCRIPTION_DAYS=365, MAX_DEVICES_DEFAULT=2, DEVICE_AUTO_REGISTER=TRUE, DEVICE_DEFAULT_DAYS=365, DEFAULT_PACKAGE=FULL, REGISTRATION_ENABLED=TRUE.
- Existing CLIENT rows were read only; this integration did not modify/delete/reset them.

The Apps Script source itself was not available through the connected Drive search and the runtime could not resolve `script.google.com`, so functions such as `adminActivateDevice` were not executed or modified. Integration is deliberately through the same authoritative Google Sheet rather than a guessed private Apps Script admin contract.

SQLite's former `devices.status/expires_at` is no longer consulted by the Android check or Admin device list. It remains in the historical migration for backward compatibility, but the integrated repository uses CLIENTS in Google Sheets for activation decisions. SQLite is used for admin/auth, audit, and identity aliases only.
