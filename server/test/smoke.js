// End-to-end check of the API without real AI keys.
// Starts its own server on a temporary data folder, then exercises every route.
import { spawn } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "aura-smoke-"));
const port = 18000 + Math.floor(Math.random() * 1000);
const base = `http://127.0.0.1:${port}`;

const env = { ...process.env, PORT: String(port), DATA_DIR: dataDir, PRO_TEST_MODE: "true", FREE_DAILY_MESSAGES: "3" };
delete env.ANTHROPIC_API_KEY;
delete env.ANTHROPIC_AUTH_TOKEN;
delete env.IMAGE_API_KEY;
const server = spawn(process.execPath, ["src/index.js"], { cwd: root, env, stdio: ["ignore", "pipe", "pipe"] });
let logs = "";
server.stdout.on("data", (d) => (logs += d));
server.stderr.on("data", (d) => (logs += d));

async function waitUp() {
  for (let i = 0; i < 300; i++) {
    try {
      if ((await fetch(`${base}/api/health`)).ok) return;
    } catch {}
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error(`server did not start:\n${logs}`);
}

let token = "";
async function api(method, url, body, extra = {}) {
  const res = await fetch(base + url, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined && !(body instanceof Uint8Array) ? { "Content-Type": "application/json" } : {}),
      ...extra,
    },
    body: body instanceof Uint8Array ? body : body !== undefined ? JSON.stringify(body) : undefined,
  });
  const type = res.headers.get("content-type") || "";
  const data = type.includes("json") ? await res.json() : await res.text();
  return { status: res.status, data };
}

function parseSse(text) {
  return text
    .split("\n\n")
    .filter((b) => b.startsWith("event:"))
    .map((b) => {
      const [e, d] = b.split("\n");
      return { event: e.slice(7), data: JSON.parse(d.slice(6)) };
    });
}

const step = (name) => console.log(`✓ ${name}`);

try {
  await waitUp();
  const health = await api("GET", "/api/health");
  assert.equal(health.data.ok, true);
  step("health");

  let r = await api("POST", "/api/auth/register", { name: "Тест", email: "Test@Example.com", password: "short" });
  assert.equal(r.status, 400);
  assert.equal(r.data.error.code, "weak_password");
  r = await api("POST", "/api/auth/register", { name: "Тест", email: "test@example.com", password: "password123" });
  assert.equal(r.status, 201);
  assert.ok(r.data.token);
  assert.equal(r.data.user.isPro, false);
  r = await api("POST", "/api/auth/register", { name: "Тест", email: "test@example.com", password: "password123" });
  assert.equal(r.status, 409);
  step("register + validation");

  r = await api("POST", "/api/auth/login", { email: "test@example.com", password: "wrong-pass" });
  assert.equal(r.status, 401);
  r = await api("POST", "/api/auth/login", { email: "TEST@example.com", password: "password123" });
  assert.equal(r.status, 200);
  token = r.data.token;
  step("login");

  r = await api("PATCH", "/api/me", { name: "Кира", preferences: { responseStyle: "detailed", language: "ru" } });
  assert.equal(r.data.user.name, "Кира");
  assert.equal(r.data.user.preferences.responseStyle, "detailed");
  step("profile update");

  r = await api("POST", "/api/chats", { mode: "code" });
  assert.equal(r.status, 201);
  const chat = r.data.chat;
  assert.equal(chat.mode, "code");
  step("create chat");

  const fileBytes = new TextEncoder().encode("print('hello')\n");
  r = await api("POST", "/api/uploads", fileBytes, { "Content-Type": "text/x-python", "X-File-Name": encodeURIComponent("привет.py") });
  assert.equal(r.status, 201);
  const file = r.data.file;
  assert.equal(file.name, "привет.py");
  const dl = await fetch(base + file.url);
  assert.equal(await dl.text(), "print('hello')\n");
  assert.equal((await fetch(base + file.url.replace(/.$/, "x"))).status, 404);
  r = await api("POST", "/api/uploads", new Uint8Array([1, 2, 3]), { "Content-Type": "application/x-msdownload" });
  assert.equal(r.status, 400);
  step("upload + signed download + type check");

  // Without an API key the stream must end with a clear error and keep the user's message.
  let res = await fetch(`${base}/api/chats/${chat.id}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ text: "Объясни этот код", attachmentIds: [file.id] }),
  });
  assert.equal(res.headers.get("content-type").split(";")[0], "text/event-stream");
  let events = parseSse(await res.text());
  assert.deepEqual(events.map((e) => e.event), ["user", "assistant", "error", "done"]);
  assert.equal(events[0].data.attachments.length, 1);
  assert.equal(events[2].data.code, "ai_not_configured");
  assert.equal(events[3].data.message.status, "error");
  step("send message → AI-not-configured error event");

  r = await api("GET", `/api/chats/${chat.id}/messages`);
  assert.equal(r.data.messages.length, 2);
  assert.equal(r.data.chat.title, "Объясни этот код");
  step("history saved + auto title");

  res = await fetch(`${base}/api/chats/${chat.id}/regenerate`, { method: "POST", headers: { Authorization: `Bearer ${token}` } });
  events = parseSse(await res.text());
  assert.deepEqual(events.map((e) => e.event), ["assistant", "error", "done"]);
  r = await api("GET", `/api/chats/${chat.id}/messages`);
  assert.equal(r.data.messages.length, 2);
  step("regenerate replaces failed answer");

  r = await api("POST", `/api/chats/${chat.id}/messages`, { text: "ещё" });
  // third message of the day still allowed (limit 3), consume it
  r = await api("POST", `/api/chats/${chat.id}/messages`, { text: "и ещё" });
  assert.equal(r.status, 402);
  assert.equal(r.data.error.code, "limit_reached");
  step("daily limit → 402 limit_reached");

  r = await api("POST", "/api/images", { prompt: "кот" });
  assert.equal(r.status, 503);
  assert.equal(r.data.error.code, "images_not_configured");
  step("images without key → clear error");

  const fakeJws = ["e30", Buffer.from(JSON.stringify({ productId: "aura_pro_monthly", originalTransactionId: "1000", expiresDate: Date.now() + 86400000 })).toString("base64url"), "sig"].join(".");
  r = await api("POST", "/api/subscription/verify", { platform: "apple", signedTransaction: fakeJws });
  assert.equal(r.status, 200);
  assert.equal(r.data.user.isPro, true);
  assert.equal(r.data.limits.dailyMessages, 1000);
  step("subscription verify (test mode) → Pro");

  r = await api("GET", "/api/chats?q=код");
  assert.equal(r.data.chats.length, 1);
  r = await api("PATCH", `/api/chats/${chat.id}`, { title: "Python" });
  assert.equal(r.data.chat.title, "Python");
  r = await api("DELETE", `/api/chats/${chat.id}`);
  r = await api("GET", "/api/chats");
  assert.equal(r.data.chats.length, 0);
  assert.equal((await fetch(base + file.url)).status, 404);
  step("search, rename, delete chat (files removed)");

  r = await api("POST", "/api/auth/logout");
  r = await api("GET", "/api/me");
  assert.equal(r.status, 401);
  step("logout invalidates token");

  console.log("\nAll smoke checks passed.");
} catch (err) {
  console.error("\nSmoke test FAILED:", err);
  console.error("Server log:\n" + logs);
  process.exitCode = 1;
} finally {
  server.kill();
  setTimeout(() => fs.rmSync(dataDir, { recursive: true, force: true }), 300);
}
