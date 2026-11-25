import fs from "node:fs";
import { Router } from "express";
import { requireAuth } from "./auth.js";
import { config } from "./config.js";
import { addUsage, db, fileJSON, filePath, getUsage, limitsFor, newId, now, removeFilesFromDisk, transaction } from "./db.js";
import { HttpError, badRequest, limitReached, notFound } from "./http.js";

const SIZES = ["1024x1024", "1024x1536", "1536x1024"];

// Style presets are appended to the prompt in English, which image models follow best.
const STYLES = {
  none: "",
  photo: "photorealistic photo, natural light, high detail, 35mm",
  "3d": "3D render, soft studio lighting, glossy materials, octane render",
  anime: "anime illustration, clean line art, vibrant colors",
  art: "digital painting, expressive brush strokes, artstation quality",
  minimal: "minimalist flat illustration, simple shapes, limited palette",
  neon: "neon glow, dark background, purple and violet light, cinematic",
};

export const imagesRouter = Router();
imagesRouter.use(requireAuth);

function imageJSON(row) {
  return {
    id: row.id,
    prompt: row.prompt,
    style: row.style,
    size: row.size,
    createdAt: row.created_at,
    url: fileJSON({ id: row.file_id, name: "", mime: "image/png", size: 0 }).url,
  };
}

async function generate(prompt, size) {
  const { apiKey, apiBase, model } = config.image;
  if (!apiKey) {
    throw new HttpError(503, "images_not_configured", "Генерация изображений не настроена на сервере: задайте IMAGE_API_KEY.");
  }
  let response;
  try {
    response = await fetch(`${apiBase}/images/generations`, {
      method: "POST",
      headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model, prompt, size, n: 1 }),
      signal: AbortSignal.timeout(180_000),
    });
  } catch {
    throw new HttpError(502, "images_unreachable", "Сервис изображений не отвечает. Попробуйте позже.");
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const reason = body?.error?.message || `код ${response.status}`;
    const code = response.status === 400 ? "image_rejected" : "images_error";
    throw new HttpError(response.status === 400 ? 400 : 502, code, `Не удалось создать изображение: ${reason}`);
  }
  const item = body?.data?.[0];
  if (item?.b64_json) return Buffer.from(item.b64_json, "base64");
  if (item?.url) {
    const img = await fetch(item.url, { signal: AbortSignal.timeout(60_000) });
    if (!img.ok) throw new HttpError(502, "images_error", "Не удалось скачать готовое изображение");
    return Buffer.from(await img.arrayBuffer());
  }
  throw new HttpError(502, "images_error", "Сервис изображений вернул пустой ответ");
}

function mimeOf(buf) {
  if (buf[0] === 0x89 && buf[1] === 0x50) return "image/png";
  if (buf[0] === 0xff && buf[1] === 0xd8) return "image/jpeg";
  if (buf.subarray(8, 12).toString() === "WEBP") return "image/webp";
  return "image/png";
}

imagesRouter.get("/images", (req, res) => {
  const rows = db.prepare("SELECT * FROM images WHERE user_id = ? ORDER BY created_at DESC LIMIT 200").all(req.user.id);
  res.json({ images: rows.map(imageJSON) });
});

imagesRouter.post("/images", async (req, res) => {
  const prompt = String(req.body?.prompt ?? "").trim();
  const size = SIZES.includes(req.body?.size) ? req.body.size : "1024x1024";
  const style = Object.hasOwn(STYLES, req.body?.style) ? req.body.style : "none";
  if (!prompt) throw badRequest("Опишите, что нарисовать");
  if (prompt.length > 2000) throw badRequest("Описание слишком длинное (до 2000 символов)");
  if (getUsage(req.user.id).images >= limitsFor(req.user).images) throw limitReached("images");

  const fullPrompt = STYLES[style] ? `${prompt}. Style: ${STYLES[style]}` : prompt;
  const data = await generate(fullPrompt, size);
  addUsage(req.user.id, "images");

  const fileId = newId();
  const imageId = newId();
  const t = now();
  fs.writeFileSync(filePath(fileId), data);
  transaction(() => {
    db.prepare(
      "INSERT INTO files (id, user_id, kind, name, mime, size, created_at) VALUES (?, ?, 'generated', ?, ?, ?, ?)",
    ).run(fileId, req.user.id, `aura-${imageId.slice(0, 8)}.png`, mimeOf(data), data.length, t);
    db.prepare("INSERT INTO images (id, user_id, file_id, prompt, style, size, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)").run(
      imageId,
      req.user.id,
      fileId,
      prompt,
      style,
      size,
      t,
    );
  });
  const row = db.prepare("SELECT * FROM images WHERE id = ?").get(imageId);
  res.status(201).json({ image: imageJSON(row), usage: getUsage(req.user.id) });
});

imagesRouter.delete("/images/:id", (req, res) => {
  const row = db.prepare("SELECT * FROM images WHERE id = ? AND user_id = ?").get(req.params.id, req.user.id);
  if (!row) throw notFound("Изображение не найдено");
  db.prepare("DELETE FROM files WHERE id = ?").run(row.file_id); // cascades to images
  removeFilesFromDisk([row.file_id]);
  res.json({ ok: true });
});
