import crypto from "node:crypto";
import { Router } from "express";
import { accountJSON, db, newId, now, removeFilesFromDisk, transaction } from "./db.js";
import { HttpError, badRequest, rateLimit } from "./http.js";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const STYLES = ["concise", "balanced", "detailed"];
const LANGUAGES = ["auto", "ru", "en", "uk", "kk", "de", "es", "fr"];

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(password, salt, 64);
  return `scrypt$${salt.toString("hex")}$${hash.toString("hex")}`;
}

function verifyPassword(password, stored) {
  const [scheme, saltHex, hashHex] = String(stored).split("$");
  if (scheme !== "scrypt" || !saltHex || !hashHex) return false;
  const expected = Buffer.from(hashHex, "hex");
  const actual = crypto.scryptSync(password, Buffer.from(saltHex, "hex"), expected.length);
  return crypto.timingSafeEqual(expected, actual);
}

const hashToken = (token) => crypto.createHash("sha256").update(token).digest("hex");

function createSession(userId, device) {
  const token = crypto.randomBytes(32).toString("base64url");
  const t = now();
  db.prepare(
    "INSERT INTO sessions (token_hash, user_id, device, created_at, last_used_at) VALUES (?, ?, ?, ?, ?)",
  ).run(hashToken(token), userId, String(device || "").slice(0, 80), t, t);
  return token;
}

export function requireAuth(req, res, next) {
  const header = req.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token) return next(new HttpError(401, "unauthorized", "Требуется вход в аккаунт"));
  const tokenHash = hashToken(token);
  const user = db
    .prepare("SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?")
    .get(tokenHash);
  if (!user) return next(new HttpError(401, "unauthorized", "Сессия истекла. Войдите снова."));
  db.prepare("UPDATE sessions SET last_used_at = ? WHERE token_hash = ?").run(now(), tokenHash);
  req.user = user;
  req.tokenHash = tokenHash;
  next();
}

const authLimiter = rateLimit({ windowMs: 60_000, max: 10 });

export const authRouter = Router();

authRouter.post("/auth/register", authLimiter, (req, res) => {
  const name = String(req.body?.name ?? "").trim();
  const email = String(req.body?.email ?? "").trim().toLowerCase();
  const password = String(req.body?.password ?? "");

  if (!name || name.length > 50) throw badRequest("Введите имя (до 50 символов)", "invalid_name");
  if (!EMAIL_RE.test(email)) throw badRequest("Введите корректный email", "invalid_email");
  if (password.length < 8) throw badRequest("Пароль должен быть не короче 8 символов", "weak_password");

  if (db.prepare("SELECT 1 FROM users WHERE email = ?").get(email)) {
    throw new HttpError(409, "email_taken", "Аккаунт с таким email уже существует");
  }

  const id = newId();
  db.prepare("INSERT INTO users (id, email, name, password_hash, created_at) VALUES (?, ?, ?, ?, ?)").run(
    id,
    email,
    name,
    hashPassword(password),
    now(),
  );
  const token = createSession(id, req.body?.device);
  const user = db.prepare("SELECT * FROM users WHERE id = ?").get(id);
  res.status(201).json({ token, ...accountJSON(user) });
});

authRouter.post("/auth/login", authLimiter, (req, res) => {
  const email = String(req.body?.email ?? "").trim().toLowerCase();
  const password = String(req.body?.password ?? "");
  const user = db.prepare("SELECT * FROM users WHERE email = ?").get(email);
  if (!user || !verifyPassword(password, user.password_hash)) {
    throw new HttpError(401, "invalid_credentials", "Неверный email или пароль");
  }
  const token = createSession(user.id, req.body?.device);
  res.json({ token, ...accountJSON(user) });
});

authRouter.post("/auth/logout", requireAuth, (req, res) => {
  db.prepare("DELETE FROM sessions WHERE token_hash = ?").run(req.tokenHash);
  res.json({ ok: true });
});

authRouter.get("/me", requireAuth, (req, res) => {
  res.json(accountJSON(req.user));
});

authRouter.patch("/me", requireAuth, (req, res) => {
  const body = req.body ?? {};
  const user = req.user;

  if (body.name !== undefined) {
    const name = String(body.name).trim();
    if (!name || name.length > 50) throw badRequest("Введите имя (до 50 символов)", "invalid_name");
    user.name = name;
  }
  const prefs = body.preferences ?? {};
  if (prefs.responseStyle !== undefined) {
    if (!STYLES.includes(prefs.responseStyle)) throw badRequest("Неизвестный стиль ответов");
    user.response_style = prefs.responseStyle;
  }
  if (prefs.language !== undefined) {
    if (!LANGUAGES.includes(prefs.language)) throw badRequest("Неизвестный язык");
    user.language = prefs.language;
  }
  db.prepare("UPDATE users SET name = ?, response_style = ?, language = ? WHERE id = ?").run(
    user.name,
    user.response_style,
    user.language,
    user.id,
  );
  res.json(accountJSON(user));
});

authRouter.post("/me/password", requireAuth, authLimiter, (req, res) => {
  const current = String(req.body?.currentPassword ?? "");
  const next = String(req.body?.newPassword ?? "");
  if (!verifyPassword(current, req.user.password_hash)) {
    throw new HttpError(403, "invalid_credentials", "Текущий пароль указан неверно");
  }
  if (next.length < 8) throw badRequest("Новый пароль должен быть не короче 8 символов", "weak_password");
  transaction(() => {
    db.prepare("UPDATE users SET password_hash = ? WHERE id = ?").run(hashPassword(next), req.user.id);
    // Sign out every other device.
    db.prepare("DELETE FROM sessions WHERE user_id = ? AND token_hash <> ?").run(req.user.id, req.tokenHash);
  });
  res.json({ ok: true });
});

authRouter.delete("/me", requireAuth, (req, res) => {
  const fileIds = db.prepare("SELECT id FROM files WHERE user_id = ?").all(req.user.id).map((r) => r.id);
  db.prepare("DELETE FROM users WHERE id = ?").run(req.user.id);
  removeFilesFromDisk(fileIds);
  res.json({ ok: true });
});
