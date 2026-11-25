import fs from "node:fs";
import crypto from "node:crypto";
import express, { Router } from "express";
import { requireAuth } from "./auth.js";
import { config } from "./config.js";
import { db, fileJSON, filePath, newId, now, signFile } from "./db.js";
import { badRequest } from "./http.js";

const ALLOWED = [
  /^image\/(jpeg|png|gif|webp)$/,
  /^application\/pdf$/,
  /^text\//,
  /^application\/(json|xml|x-yaml|yaml|javascript|x-sh|sql)$/,
  /^application\/octet-stream$/, // code files often arrive without a specific type
];

export const uploadRouter = Router();

// Raw body upload: Content-Type is the file type, X-File-Name the (URI-encoded) file name.
uploadRouter.post(
  "/uploads",
  requireAuth,
  express.raw({ type: () => true, limit: config.maxUploadBytes }),
  (req, res) => {
    const mime = String(req.get("content-type") || "application/octet-stream").split(";")[0].trim().toLowerCase();
    let name = "file";
    try {
      name = decodeURIComponent(req.get("x-file-name") || "file");
    } catch {
      /* keep default */
    }
    name = name.replace(/[\\/\r\n\0]/g, "_").slice(0, 120) || "file";

    if (!Buffer.isBuffer(req.body) || req.body.length === 0) throw badRequest("Пустой файл");
    if (!ALLOWED.some((re) => re.test(mime))) {
      throw badRequest("Этот тип файла не поддерживается. Можно: изображения, PDF и текстовые файлы.", "unsupported_file");
    }

    const id = newId();
    fs.writeFileSync(filePath(id), req.body);
    db.prepare(
      "INSERT INTO files (id, user_id, kind, name, mime, size, created_at) VALUES (?, ?, 'upload', ?, ?, ?, ?)",
    ).run(id, req.user.id, name, mime, req.body.length, now());
    res.status(201).json({ file: fileJSON({ id, name, mime, size: req.body.length }) });
  },
);

// Public, signed download link (so images load without auth headers).
export const fileDownloadRouter = Router();

fileDownloadRouter.get("/files/:id/:sig", (req, res) => {
  const { id, sig } = req.params;
  const expected = signFile(id);
  const ok = sig.length === expected.length && crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected));
  const row = ok ? db.prepare("SELECT * FROM files WHERE id = ?").get(id) : null;
  if (!row || !fs.existsSync(filePath(id))) {
    return res.status(404).json({ error: { code: "not_found", message: "Файл не найден" } });
  }
  res.set({
    "Content-Type": row.mime,
    "Cache-Control": "private, max-age=31536000, immutable",
    "Content-Disposition": `inline; filename*=UTF-8''${encodeURIComponent(row.name)}`,
  });
  fs.createReadStream(filePath(id)).pipe(res);
});
