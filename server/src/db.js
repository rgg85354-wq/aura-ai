import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import { config } from "./config.js";

export const db = new DatabaseSync(path.join(config.dataDir, "aura.db"));

db.exec(`
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  response_style TEXT NOT NULL DEFAULT 'balanced',
  language TEXT NOT NULL DEFAULT 'auto',
  pro_until INTEGER,
  pro_platform TEXT,
  pro_product TEXT,
  pro_original_id TEXT
);

CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device TEXT,
  created_at INTEGER NOT NULL,
  last_used_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS chats (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  mode TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS chats_by_user ON chats(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS messages (
  id TEXT PRIMARY KEY,
  chat_id TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'complete',
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS messages_by_chat ON messages(chat_id, created_at);

CREATE TABLE IF NOT EXISTS files (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message_id TEXT REFERENCES messages(id) ON DELETE SET NULL,
  kind TEXT NOT NULL DEFAULT 'upload',
  name TEXT NOT NULL,
  mime TEXT NOT NULL,
  size INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS files_by_message ON files(message_id);

CREATE TABLE IF NOT EXISTS images (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  file_id TEXT NOT NULL REFERENCES files(id) ON DELETE CASCADE,
  prompt TEXT NOT NULL,
  style TEXT NOT NULL,
  size TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS images_by_user ON images(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS usage (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day TEXT NOT NULL,
  messages INTEGER NOT NULL DEFAULT 0,
  images INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day)
);
`);

export const newId = () => crypto.randomUUID();
export const now = () => Date.now();

export function transaction(fn) {
  db.exec("BEGIN");
  try {
    const result = fn();
    db.exec("COMMIT");
    return result;
  } catch (err) {
    db.exec("ROLLBACK");
    throw err;
  }
}

// ---------- files on disk ----------

export const filePath = (id) => path.join(config.filesDir, id);

export function removeFilesFromDisk(ids) {
  for (const id of ids) {
    fs.rm(filePath(id), { force: true }, () => {});
  }
}

export function signFile(id) {
  return crypto.createHmac("sha256", config.secret).update(`file:${id}`).digest("base64url").slice(0, 32);
}

export function fileJSON(row) {
  return {
    id: row.id,
    name: row.name,
    mime: row.mime,
    size: row.size,
    // Relative path: the app prefixes it with the server address it uses.
    url: `/files/${row.id}/${signFile(row.id)}`,
  };
}

// ---------- users & usage ----------

export function isPro(user) {
  return Boolean(user.pro_until && user.pro_until > now());
}

export function today() {
  return new Date().toISOString().slice(0, 10);
}

export function getUsage(userId) {
  const row = db.prepare("SELECT messages, images FROM usage WHERE user_id = ? AND day = ?").get(userId, today());
  return { messages: row?.messages ?? 0, images: row?.images ?? 0 };
}

export function addUsage(userId, field) {
  if (field !== "messages" && field !== "images") throw new Error("bad usage field");
  db.prepare(
    `INSERT INTO usage (user_id, day, ${field}) VALUES (?, ?, 1)
     ON CONFLICT(user_id, day) DO UPDATE SET ${field} = ${field} + 1`,
  ).run(userId, today());
}

export function limitsFor(user) {
  return isPro(user) ? config.limits.pro : config.limits.free;
}

export function userJSON(user) {
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    createdAt: user.created_at,
    isPro: isPro(user),
    proUntil: user.pro_until ?? null,
    proPlatform: user.pro_platform ?? null,
    preferences: {
      responseStyle: user.response_style,
      language: user.language,
    },
  };
}

export function accountJSON(user) {
  const usage = getUsage(user.id);
  const limits = limitsFor(user);
  return {
    user: userJSON(user),
    usage: { messagesToday: usage.messages, imagesToday: usage.images },
    limits: { dailyMessages: limits.messages, dailyImages: limits.images },
  };
}

// Remove uploads that were never attached to a message (older than a day).
export function cleanupOrphanFiles() {
  const cutoff = now() - 24 * 60 * 60 * 1000;
  const rows = db
    .prepare(
      `SELECT id FROM files
       WHERE kind = 'upload' AND message_id IS NULL AND created_at < ?`,
    )
    .all(cutoff);
  if (!rows.length) return;
  const ids = rows.map((r) => r.id);
  const del = db.prepare("DELETE FROM files WHERE id = ?");
  transaction(() => ids.forEach((id) => del.run(id)));
  removeFilesFromDisk(ids);
}
