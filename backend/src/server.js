import express from 'express';
import { createHash } from 'node:crypto';
import { readFileSync, existsSync, statSync } from 'node:fs';
import { mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import Database from 'better-sqlite3';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Load configuration
// Config paths are relative to the directory that contains config.json
// (i.e. the backend/ root), not to this source file.
const configPath = process.env.CONFIG_PATH || join(__dirname, '..', 'config.json');
const config = JSON.parse(readFileSync(configPath, 'utf-8'));
const configDir = dirname(resolve(configPath));

const PORT = process.env.PORT || config.port || 3000;
const CATALOG_PATH = resolve(configDir, config.catalog_path || '../shared/catalog/exercises_seed.json');
const DB_PATH = resolve(configDir, config.db_path || './data/leanhome.db');

// ---------------------------------------------------------------------------
// Catalog loading & versioning
// ---------------------------------------------------------------------------

let catalogCache = null;

function loadCatalog() {
  if (catalogCache) return catalogCache;
  const raw = readFileSync(CATALOG_PATH, 'utf-8');
  catalogCache = JSON.parse(raw);
  return catalogCache;
}

/**
 * Derive a deterministic catalog version from the content hash of the seed
 * file. The version is the first 8 hex characters of the SHA-256 digest,
 * interpreted as a 32-bit unsigned integer so it fits in a JSON number.
 * This guarantees that the version changes if and only if the catalog
 * content changes.
 */
function catalogHash() {
  const raw = readFileSync(CATALOG_PATH);
  return createHash('sha256').update(raw).digest('hex');
}

function catalogVersion() {
  // Take first 8 hex chars → 32-bit int → deterministic version number
  return parseInt(catalogHash().slice(0, 8), 16);
}

function catalogUpdatedAt() {
  // V1: the catalog is a static seed file; use the file mtime (in epoch
  // seconds) as the "updated_at" timestamp so clients can report a useful
  // value even though there is no mutation API yet.
  return Math.floor(statSync(CATALOG_PATH).mtimeMs / 1000);
}

// ---------------------------------------------------------------------------
// SQLite profile sync store
// ---------------------------------------------------------------------------

function ensureDbDir() {
  const dir = dirname(DB_PATH);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
}

function openDb() {
  ensureDbDir();
  const db = new Database(DB_PATH);
  db.pragma('journal_mode = WAL');
  db.exec(`
    CREATE TABLE IF NOT EXISTS profile_syncs (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id       TEXT NOT NULL,
      profile       TEXT NOT NULL,
      sessions      TEXT NOT NULL,
      weight_logs   TEXT NOT NULL,
      synced_at     INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_profile_syncs_user_id ON profile_syncs(user_id);
  `);
  return db;
}

// ---------------------------------------------------------------------------
// API server
// ---------------------------------------------------------------------------

const app = express();
app.use(express.json({ limit: '2mb' }));

// Health check
app.get('/api/v1/health', (_req, res) => {
  res.json({ status: 'ok' });
});

// Catalog version
app.get('/api/v1/catalog/version', (_req, res) => {
  res.json({
    version: catalogVersion(),
    updated_at: catalogUpdatedAt(),
  });
});

// Catalog delta
app.get('/api/v1/catalog/delta', (req, res) => {
  const since = parseInt(req.query.since, 10);
  const currentVersion = catalogVersion();

  // V1: if the client's `since` is older than the current version (or
  // missing / invalid), return a full refresh — every exercise in the
  // seed catalog and an empty deleted_ids list.
  const stale = Number.isNaN(since) || since < currentVersion;

  if (stale) {
    const exercises = loadCatalog();
    return res.json({
      version: currentVersion,
      exercises,
      deleted_ids: [],
      updated_at: catalogUpdatedAt(),
    });
  }

  // Client is already up to date
  return res.json({
    version: currentVersion,
    exercises: [],
    deleted_ids: [],
    updated_at: catalogUpdatedAt(),
  });
});

// Profile sync
app.post('/api/v1/profiles/sync', (req, res) => {
  const { user_id, profile, sessions, weight_logs } = req.body;

  if (!user_id || typeof user_id !== 'string') {
    return res.status(400).json({ error: 'user_id is required and must be a string' });
  }

  const syncedAt = Math.floor(Date.now() / 1000);

  const db = openDb();
  try {
    const stmt = db.prepare(`
      INSERT INTO profile_syncs (user_id, profile, sessions, weight_logs, synced_at)
      VALUES (?, ?, ?, ?, ?)
    `);
    stmt.run(
      user_id,
      JSON.stringify(profile ?? {}),
      JSON.stringify(sessions ?? []),
      JSON.stringify(weight_logs ?? []),
      syncedAt,
    );
  } finally {
    db.close();
  }

  res.json({ synced_at: syncedAt, status: 'ok' });
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------

app.listen(PORT, () => {
  // Pre-warm the catalog cache so the first request is fast and any
  // parse error surfaces immediately at startup.
  loadCatalog();
  console.log(`LeanHome backend listening on port ${PORT}`);
});

export { app, catalogVersion, loadCatalog, openDb };
