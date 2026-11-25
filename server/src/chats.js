import { Router } from "express";
import { MODES, buildMessages, describeAiError, streamReply } from "./ai.js";
import { requireAuth } from "./auth.js";
import {
  addUsage,
  db,
  fileJSON,
  getUsage,
  isPro,
  limitsFor,
  newId,
  now,
  removeFilesFromDisk,
  transaction,
} from "./db.js";
import { badRequest, limitReached, notFound } from "./http.js";

export const DEFAULT_TITLE = "Новый чат";
const MAX_ATTACHMENTS = 5;
const MAX_MESSAGE_CHARS = 32_000;
const HISTORY_LIMIT = 60;

export const chatsRouter = Router();
chatsRouter.use(requireAuth);

function chatJSON(row) {
  return {
    id: row.id,
    title: row.title,
    mode: row.mode,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    preview: row.preview ? String(row.preview).slice(0, 140) : "",
    messageCount: row.message_count ?? 0,
  };
}

function messageJSON(row, files = []) {
  return {
    id: row.id,
    chatId: row.chat_id,
    role: row.role,
    content: row.content,
    status: row.status,
    createdAt: row.created_at,
    attachments: files.map(fileJSON),
  };
}

const CHAT_SELECT = `
  SELECT c.*,
    (SELECT content FROM messages m WHERE m.chat_id = c.id AND m.content <> '' ORDER BY created_at DESC LIMIT 1) AS preview,
    (SELECT COUNT(*) FROM messages m WHERE m.chat_id = c.id) AS message_count
  FROM chats c`;

function loadChat(userId, chatId) {
  const row = db.prepare(`${CHAT_SELECT} WHERE c.id = ? AND c.user_id = ?`).get(chatId, userId);
  if (!row) throw notFound("Чат не найден");
  return row;
}

function filesByMessage(messageIds) {
  const map = new Map();
  if (!messageIds.length) return map;
  const placeholders = messageIds.map(() => "?").join(",");
  const rows = db
    .prepare(`SELECT * FROM files WHERE message_id IN (${placeholders}) ORDER BY created_at`)
    .all(...messageIds);
  for (const f of rows) {
    if (!map.has(f.message_id)) map.set(f.message_id, []);
    map.get(f.message_id).push(f);
  }
  return map;
}

function titleFrom(text, files) {
  const clean = text.replace(/\s+/g, " ").trim();
  if (clean) return clean.length > 48 ? `${clean.slice(0, 47).trimEnd()}…` : clean;
  return files.length ? files[0].name.slice(0, 48) : DEFAULT_TITLE;
}

// ---------- chats ----------

chatsRouter.get("/chats", (req, res) => {
  const q = String(req.query.q ?? "").trim();
  const rows = q
    ? db
        .prepare(
          `${CHAT_SELECT} WHERE c.user_id = ? AND (c.title LIKE ? OR EXISTS (
             SELECT 1 FROM messages m WHERE m.chat_id = c.id AND m.content LIKE ?))
           ORDER BY c.updated_at DESC`,
        )
        .all(req.user.id, `%${q}%`, `%${q}%`)
    : db.prepare(`${CHAT_SELECT} WHERE c.user_id = ? ORDER BY c.updated_at DESC`).all(req.user.id);
  res.json({ chats: rows.map(chatJSON) });
});

chatsRouter.post("/chats", (req, res) => {
  const mode = MODES.includes(req.body?.mode) ? req.body.mode : "chat";
  const title = String(req.body?.title ?? "").trim().slice(0, 80) || DEFAULT_TITLE;
  const id = newId();
  const t = now();
  db.prepare("INSERT INTO chats (id, user_id, title, mode, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)").run(
    id,
    req.user.id,
    title,
    mode,
    t,
    t,
  );
  res.status(201).json({ chat: chatJSON(loadChat(req.user.id, id)) });
});

chatsRouter.patch("/chats/:id", (req, res) => {
  const chat = loadChat(req.user.id, req.params.id);
  const title = String(req.body?.title ?? "").trim().slice(0, 80);
  if (!title) throw badRequest("Введите название чата");
  db.prepare("UPDATE chats SET title = ? WHERE id = ?").run(title, chat.id);
  res.json({ chat: chatJSON(loadChat(req.user.id, chat.id)) });
});

function deleteChats(userId, chatIds) {
  if (!chatIds.length) return;
  const placeholders = chatIds.map(() => "?").join(",");
  const fileIds = db
    .prepare(
      `SELECT f.id FROM files f JOIN messages m ON m.id = f.message_id
       WHERE m.chat_id IN (${placeholders}) AND f.user_id = ?`,
    )
    .all(...chatIds, userId)
    .map((r) => r.id);
  transaction(() => {
    if (fileIds.length) {
      db.prepare(`DELETE FROM files WHERE id IN (${fileIds.map(() => "?").join(",")})`).run(...fileIds);
    }
    db.prepare(`DELETE FROM chats WHERE user_id = ? AND id IN (${placeholders})`).run(userId, ...chatIds);
  });
  removeFilesFromDisk(fileIds);
}

chatsRouter.delete("/chats/:id", (req, res) => {
  const chat = loadChat(req.user.id, req.params.id);
  deleteChats(req.user.id, [chat.id]);
  res.json({ ok: true });
});

chatsRouter.delete("/chats", (req, res) => {
  const ids = db.prepare("SELECT id FROM chats WHERE user_id = ?").all(req.user.id).map((r) => r.id);
  deleteChats(req.user.id, ids);
  res.json({ ok: true });
});

// ---------- messages ----------

chatsRouter.get("/chats/:id/messages", (req, res) => {
  const chat = loadChat(req.user.id, req.params.id);
  const rows = db.prepare("SELECT * FROM messages WHERE chat_id = ? ORDER BY created_at, rowid").all(chat.id);
  const files = filesByMessage(rows.map((r) => r.id));
  res.json({ chat: chatJSON(chat), messages: rows.map((r) => messageJSON(r, files.get(r.id))) });
});

chatsRouter.post("/chats/:id/messages", async (req, res) => {
  const chat = loadChat(req.user.id, req.params.id);
  const text = String(req.body?.text ?? "").trim();
  const attachmentIds = Array.isArray(req.body?.attachmentIds) ? req.body.attachmentIds.map(String) : [];

  if (!text && !attachmentIds.length) throw badRequest("Сообщение пустое");
  if (text.length > MAX_MESSAGE_CHARS) throw badRequest("Сообщение слишком длинное");
  if (attachmentIds.length > MAX_ATTACHMENTS) throw badRequest(`Можно приложить не больше ${MAX_ATTACHMENTS} файлов`);
  checkQuota(req.user);

  const files = attachmentIds.map((id) => {
    const f = db.prepare("SELECT * FROM files WHERE id = ? AND user_id = ? AND kind = 'upload'").get(id, req.user.id);
    if (!f) throw badRequest("Вложение не найдено. Загрузите файл ещё раз.");
    return f;
  });

  const messageId = newId();
  const t = now();
  transaction(() => {
    db.prepare("INSERT INTO messages (id, chat_id, role, content, status, created_at) VALUES (?, ?, 'user', ?, 'complete', ?)").run(
      messageId,
      chat.id,
      text,
      t,
    );
    for (const f of files) db.prepare("UPDATE files SET message_id = ? WHERE id = ?").run(messageId, f.id);
    if (chat.title === DEFAULT_TITLE) {
      db.prepare("UPDATE chats SET title = ? WHERE id = ?").run(titleFrom(text, files), chat.id);
    }
    db.prepare("UPDATE chats SET updated_at = ? WHERE id = ?").run(t, chat.id);
  });

  const userMessage = db.prepare("SELECT * FROM messages WHERE id = ?").get(messageId);
  await streamAssistantTurn(req, res, chat.id, messageJSON(userMessage, files));
});

// Re-run the answer to the last user message (used by "Повторить").
chatsRouter.post("/chats/:id/regenerate", async (req, res) => {
  const chat = loadChat(req.user.id, req.params.id);
  checkQuota(req.user);
  const last = db.prepare("SELECT * FROM messages WHERE chat_id = ? ORDER BY created_at DESC, rowid DESC LIMIT 1").get(chat.id);
  if (!last) throw badRequest("В чате нет сообщений");
  if (last.role === "assistant") db.prepare("DELETE FROM messages WHERE id = ?").run(last.id);
  await streamAssistantTurn(req, res, chat.id, null);
});

function checkQuota(user) {
  const usage = getUsage(user.id);
  if (usage.messages >= limitsFor(user).messages) throw limitReached("messages");
}

async function streamAssistantTurn(req, res, chatId, userMessage) {
  const user = req.user;
  const history = db
    .prepare(
      `SELECT * FROM (SELECT *, rowid AS rid FROM messages WHERE chat_id = ? ORDER BY created_at DESC, rowid DESC LIMIT ?)
       ORDER BY created_at, rid`,
    )
    .all(chatId, HISTORY_LIMIT);
  const chat = db.prepare("SELECT * FROM chats WHERE id = ?").get(chatId);
  const messages = buildMessages(history, filesByMessage(history.map((m) => m.id)));
  if (!messages.length) throw badRequest("Нет сообщения для ответа");

  addUsage(user.id, "messages");

  const assistantId = newId();
  const startedAt = now();
  db.prepare("INSERT INTO messages (id, chat_id, role, content, status, created_at) VALUES (?, ?, 'assistant', '', 'streaming', ?)").run(
    assistantId,
    chatId,
    Math.max(startedAt, (userMessage?.createdAt ?? 0) + 1),
  );

  res.status(200);
  res.set({
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache, no-transform",
    Connection: "keep-alive",
    "X-Accel-Buffering": "no",
  });
  res.flushHeaders();

  const send = (event, data) => {
    if (!res.writableEnded) res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  };
  const heartbeat = setInterval(() => !res.writableEnded && res.write(": ping\n\n"), 15_000);
  const abort = new AbortController();
  res.on("close", () => {
    if (!res.writableEnded) abort.abort();
  });

  if (userMessage) send("user", userMessage);
  send("assistant", { id: assistantId });

  let text = "";
  let status = "complete";
  let errorInfo = null;
  // Save partial text every few seconds so another device sees progress after a crash.
  let lastSave = Date.now();
  const save = () =>
    db.prepare("UPDATE messages SET content = ?, status = ? WHERE id = ?").run(text, status, assistantId);

  try {
    const final = await streamReply({
      messages,
      mode: chat.mode,
      user,
      pro: isPro(user),
      signal: abort.signal,
      onText: (delta) => {
        text += delta;
        send("delta", { text: delta });
        if (Date.now() - lastSave > 3000) {
          status = "streaming";
          save();
          lastSave = Date.now();
        }
      },
    });
    if (final.stop_reason === "refusal") {
      status = "refused";
      errorInfo = { code: "refusal", message: "Aura не может ответить на этот запрос. Попробуйте переформулировать." };
    } else if (final.stop_reason === "max_tokens") {
      status = "truncated";
    } else {
      status = "complete";
    }
  } catch (err) {
    const info = describeAiError(err);
    status = info.code === "cancelled" ? "stopped" : "error";
    if (status === "error") errorInfo = info;
  } finally {
    clearInterval(heartbeat);
  }

  save();
  db.prepare("UPDATE chats SET updated_at = ? WHERE id = ?").run(now(), chatId);

  const saved = db.prepare("SELECT * FROM messages WHERE id = ?").get(assistantId);
  if (errorInfo) send("error", { code: errorInfo.code, message: errorInfo.message, messageId: assistantId });
  send("done", {
    message: messageJSON(saved),
    chat: chatJSON(loadChat(user.id, chatId)),
    usage: getUsage(user.id),
  });
  res.end();
}
