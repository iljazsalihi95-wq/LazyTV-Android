PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS playlists (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL,
 type TEXT NOT NULL CHECK(type IN ('M3U_URL','XTREAM_CODES','STALKER_PORTAL')),
 server_url TEXT NOT NULL,
 username TEXT,
 password TEXT,
 mac_address TEXT,
 created_at TEXT NOT NULL,
 updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS device_playlists (
 device_id INTEGER NOT NULL,
 playlist_id INTEGER NOT NULL,
 is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1)),
 assigned_at TEXT NOT NULL,
 PRIMARY KEY(device_id, playlist_id),
 FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE,
 FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_device_playlists_device ON device_playlists(device_id, is_active);
CREATE INDEX IF NOT EXISTS idx_device_playlists_playlist ON device_playlists(playlist_id);
