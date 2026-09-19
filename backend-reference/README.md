# LazyTV PRO Activation — Google Sheet integrated

## Source of truth
`LazyTV_USERS_SUBSCRIPTIONS` / `CLIENTS` is authoritative for activation status and expiration. SQLite is **not** an activation authority anymore. It stores admin accounts/sessions, audit history, and identity aliases used to connect the new Android Device ID/Serial/LazyTV ID to an existing `CLIENT_ID` without rewriting legacy rows.

Spreadsheet ID: configure with `GOOGLE_SPREADSHEET_ID`. The existing Apps Script and this Node layer therefore observe the same `CLIENTS.STATUS` / `END_AT` values. There is no independent SQLite ACTIVE flag used by `/api/v1/device/check`.

### Status mapping
| Sheet CLIENTS.STATUS | END_AT | Android |
|---|---|---|
| `ACTIVE` | empty/future | `ACTIVE` |
| `ACTIVE` | past | `EXPIRED` |
| `PENDING` / `INACTIVE` / unknown non-active | any | `INACTIVE` |
| `EXPIRED` | any | `EXPIRED` |
| `BLOCKED` | any | `BLOCKED` |

Server time makes the expiration decision. Failure to read Google Sheets is an API error, never fallback ACTIVE.

## Identity compatibility
Legacy `CLIENTS.DEVICE_ID`, `MAC`, and `DEVICE_KEY` are preserved. They are **not** rewritten or guessed to be the new Serial. Android keeps the existing deterministic `DeviceIdentityManager` algorithm. The check request remains compatible and adds only optional `lazytv_id`:

```json
{"device_id":"LTV-...","serial":"LTV-XXXX-XXXX-XXXX","lazytv_id":"XX:XX:XX:XX:XX:XX","app_version":"1.0"}
```

If the Android identity is new, a `PENDING` CLIENTS row is created. For a legacy client whose old Device ID cannot safely be inferred, Admin Device Detail provides **PAIR IDENTITY**. Pairing is server-side metadata only; it does not change Sheet status/expiration and does not delete legacy data. After pairing, checks for the new identity resolve to the existing CLIENT row.

## Google access / secrets
The Android APK never receives Google credentials. Node authenticates to Google Sheets with a service account using `GOOGLE_SERVICE_ACCOUNT_EMAIL` + `GOOGLE_PRIVATE_KEY` from environment variables. Share the spreadsheet with that service-account email. Do not commit the real private key or `.env`.

The existing Apps Script Web App may continue to operate on the same spreadsheet. Node Admin actions write `CLIENTS.STATUS`, `END_AT`, `NOTES`, and `LAST_SEEN` directly through the Google Sheets API; Apps Script and Node therefore do not maintain two independent activation states.

## Android API
`POST /api/v1/device/check` response remains:
```json
{"status":"ACTIVE|INACTIVE|EXPIRED|BLOCKED","expires_at":null,"message":null,"server_time":"ISO-8601"}
```
Android's `ActivationApiClient` already parses these fields. `lazytv_id` is optional request metadata only.

## Install
```sh
cp .env.example .env
# edit .env and keep real secrets outside source control
npm install
set -a; . ./.env; set +a
npm run migrate
npm run init-admin
npm start
```
Open `/admin/login` after startup. Admin authentication remains bcrypt + HttpOnly/SameSite session cookie, CSRF-protected mutations, rate limiting and audit log.

## Deployment
Required: Node 20+, persistent SQLite path for admin/audit/aliases, HTTPS reverse proxy, and server-side Google service-account credentials. `SESSION_SECURE=true` in production. Android `ACTIVATION_BASE_URL` should be the final HTTPS Node backend URL only.

SQLite backup still matters for admin/audit/identity aliases, but activation status/expiration is backed by the existing Google Sheet. Back up both the SQLite file and the Google Sheet before any controlled migration.

## Important migration rule
There is **no automatic migration** of legacy Device IDs and no bulk rewrite of CLIENTS. Existing devices remain unchanged. Pair legacy devices only when their new Android identity is known and verified.
