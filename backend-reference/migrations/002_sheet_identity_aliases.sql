CREATE TABLE IF NOT EXISTS device_aliases (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 client_id TEXT NOT NULL,
 sheet_device_id TEXT NOT NULL,
 android_device_id TEXT NOT NULL UNIQUE,
 serial TEXT NOT NULL UNIQUE,
 lazytv_id TEXT,
 created_at TEXT NOT NULL,
 updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_alias_client ON device_aliases(client_id);
CREATE INDEX IF NOT EXISTS idx_alias_lazy ON device_aliases(lazytv_id);
ALTER TABLE device_aliases ADD COLUMN app_version TEXT;
