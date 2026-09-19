PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS devices (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 device_id TEXT NOT NULL UNIQUE,
 serial TEXT NOT NULL UNIQUE,
 status TEXT NOT NULL CHECK(status IN ('ACTIVE','INACTIVE','EXPIRED','BLOCKED')) DEFAULT 'INACTIVE',
 expires_at TEXT,
 first_seen_at TEXT NOT NULL,
 last_seen_at TEXT NOT NULL,
 last_active_check_at TEXT,
 app_version TEXT,
 created_at TEXT NOT NULL,
 updated_at TEXT NOT NULL,
 admin_note TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen ON devices(last_seen_at);
CREATE TABLE IF NOT EXISTS admins (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 username TEXT NOT NULL UNIQUE,
 password_hash TEXT NOT NULL,
 created_at TEXT NOT NULL,
 updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS audit_log (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 timestamp TEXT NOT NULL,
 admin_id INTEGER,
 admin_username TEXT NOT NULL,
 device_id INTEGER,
 device_identifier TEXT,
 action TEXT NOT NULL,
 previous_value TEXT,
 new_value TEXT,
 FOREIGN KEY(admin_id) REFERENCES admins(id) ON DELETE SET NULL,
 FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_device ON audit_log(device_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_log(timestamp DESC);
CREATE TABLE IF NOT EXISTS sessions (
 sid TEXT PRIMARY KEY,
 sess TEXT NOT NULL,
 expire INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_expire ON sessions(expire);
CREATE TABLE IF NOT EXISTS schema_migrations (
 name TEXT PRIMARY KEY,
 applied_at TEXT NOT NULL
);
