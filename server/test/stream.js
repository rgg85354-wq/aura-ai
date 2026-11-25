// Checks the streaming path end to end without spending money: a local stand-in for the
// Claude API answers in the real SSE format, and we inspect what the Aura server sent to it.
import { spawn } from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "aura-stream-"));
const requests = [];

// --- stand-in for the Claude Messages API ---
const mock = http.createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    requests.push({ url: req.url, headers: req.headers, body: JSON.parse(body) });
    res.writeHead(200, { "Content-Type": "text/event-stream" });
    const send = (type, data) => res.write(`event: ${type}\ndata: ${JSON.stringify({ type, ...data })}\n\n`);
    send("message_start", {
      message: { id: "msg_test", type: "message", role: "assistant", model: "claude-opus-5-5", content: [], stop_reason: null, stop_sequence: null, usage: { input_tokens: 12, output_tokens: 0 } },
    });
    send("content_block_start", { index: 0, content_block: { type: "text", text: "" } });
    for (const piece of ["Привет", "! Это ", "**Aura**."]) send("content_block_delta", { index: 0, delta: { type: "text_delta", text: piece } });
    send("content_block_stop", { index: 0 });
    send("message_delta", { delta: { stop_reason: "end_turn", stop_sequence: null }, usage: { output_tokens: 7 } });
    send("message_stop", {});
    res.end();
  });
});
await new Promise((r) => mock.listen(0, "127.0.0.1", r));
const mockUrl = `http://127.0.0.1:${mock.address().port}`;

// --- Aura server pointed at the stand-in ---
const port = 19000 + Math.floor(Math.random() * 1000);
const base = `http://127.0.0.1:${port}`;
const env = { ...process.env, PORT: String(port), DATA_DIR: dataDir, ANTHROPIC_API_KEY: "test-key", ANTHROPIC_BASE_URL: mockUrl };
delete env.ANTHROPIC_AUTH_TOKEN;
const server = spawn(process.execPath, ["src/index.js"], { cwd: root, env, stdio: ["ignore", "pipe", "pipe"] });
let logs = "";
server.stdout.on("data", (d) => (logs += d));
server.stderr.on("data", (d) => (logs += d));

try {
  for (let i = 0; i < 300; i++) {
    try { if ((await fetch(`${base}/api/health`)).ok) break; } catch {}
    await new Promise((r) => setTimeout(r, 100));
  }
  const post = (url, body, token, headers = {}) =>
    fetch(base + url, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      body: body instanceof Uint8Array ? body : JSON.stringify(body),
    });

  const { token } = await (await post("/api/auth/register", { name: "Тест", email: "s@example.com", password: "password123" })).json();
  await fetch(`${base}/api/me`, { method: "PATCH", headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` }, body: JSON.stringify({ preferences: { language: "en" } }) });
  const { chat } = await (await post("/api/chats", { mode: "code" }, token)).json();

  // 1x1 PNG
  const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", "base64");
  const up = await (await post("/api/uploads", new Uint8Array(png), token, { "Content-Type": "image/png", "X-File-Name": "dot.png" })).json();

  const res = await post(`/api/chats/${chat.id}/messages`, { text: "Что на картинке?", attachmentIds: [up.file.id] }, token);
  const text = await res.text();
  const events = text.split("\n\n").filter((b) => b.startsWith("event:")).map((b) => {
    const [e, d] = b.split("\n");
    return { event: e.slice(7), data: JSON.parse(d.slice(6)) };
  });

  assert.deepEqual(events.map((e) => e.event), ["user", "assistant", "delta", "delta", "delta", "done"]);
  assert.equal(events.filter((e) => e.event === "delta").map((e) => e.data.text).join(""), "Привет! Это **Aura**.");
  assert.equal(events.at(-1).data.message.status, "complete");
  assert.equal(events.at(-1).data.message.content, "Привет! Это **Aura**.");
  console.log("✓ app receives user → assistant → deltas → done");

  const sent = requests[0];
  assert.equal(sent.body.model, "claude-opus-5-5");
  assert.equal(sent.body.stream, true);
  assert.equal(sent.body.fallbacks, "default");
  assert.match(String(sent.headers["anthropic-beta"]), /server-side-fallback-2026-07-01/);
  assert.equal(sent.body.output_config.effort, "medium"); // free plan, code mode
  assert.equal(sent.body.thinking, undefined);
  assert.equal(sent.body.temperature, undefined);
  assert.match(sent.body.system[0].text, /программирование/);
  assert.match(sent.body.system[0].text, /английском/);
  assert.deepEqual(sent.body.system[0].cache_control, { type: "ephemeral" });
  const blocks = sent.body.messages[0].content;
  assert.equal(blocks[0].type, "image");
  assert.equal(blocks[0].source.media_type, "image/png");
  assert.equal(blocks[1].text, "Что на картинке?");
  console.log("✓ request to Claude: model, streaming, fallbacks beta, effort, system prompt, image block");

  // History is saved and replayed on the next turn.
  await (await post(`/api/chats/${chat.id}/messages`, { text: "А ещё?" }, token)).text();
  const second = requests[1].body.messages;
  assert.deepEqual(second.map((m) => m.role), ["user", "assistant", "user"]);
  assert.equal(second[1].content, "Привет! Это **Aura**.");
  console.log("✓ second turn includes the saved conversation");

  console.log("\nAll streaming checks passed.");
} catch (err) {
  console.error("\nStreaming test FAILED:", err);
  console.error("Server log:\n" + logs);
  process.exitCode = 1;
} finally {
  server.kill();
  mock.close();
  setTimeout(() => fs.rmSync(dataDir, { recursive: true, force: true }), 300);
}
