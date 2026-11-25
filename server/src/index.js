import os from "node:os";
import express from "express";
import { authRouter } from "./auth.js";
import { chatsRouter } from "./chats.js";
import { config } from "./config.js";
import { cleanupOrphanFiles } from "./db.js";
import { fileDownloadRouter, uploadRouter } from "./files.js";
import { HttpError, sendError } from "./http.js";
import { imagesRouter } from "./images.js";
import { subscriptionRouter } from "./subscription.js";

const app = express();
app.disable("x-powered-by");
app.set("trust proxy", true);

app.get("/api/health", (req, res) => {
  res.json({
    ok: true,
    name: "Aura AI server",
    model: config.model,
    ai: Boolean(process.env.ANTHROPIC_API_KEY || process.env.ANTHROPIC_AUTH_TOKEN),
    images: Boolean(config.image.apiKey),
    subscriptionTestMode: config.subscription.testMode,
  });
});

// Uploads read the raw body, so they are mounted before the JSON parser.
app.use("/api", uploadRouter);
app.use(fileDownloadRouter);
app.use("/api", express.json({ limit: "1mb" }));
app.use("/api", authRouter);
app.use("/api", chatsRouter);
app.use("/api", imagesRouter);
app.use("/api", subscriptionRouter);

app.use((req, res, next) => next(new HttpError(404, "not_found", "Адрес не найден")));
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  if (res.headersSent) {
    console.error(err);
    return res.end();
  }
  sendError(res, err);
});

cleanupOrphanFiles();
setInterval(cleanupOrphanFiles, 6 * 60 * 60 * 1000).unref();

app.listen(config.port, config.host, () => {
  const lan = Object.values(os.networkInterfaces())
    .flat()
    .filter((i) => i && i.family === "IPv4" && !i.internal)
    .map((i) => `http://${i.address}:${config.port}`);
  console.log(`Aura AI server is running on port ${config.port}`);
  console.log(`  This computer:   http://localhost:${config.port}`);
  for (const url of lan) console.log(`  Phone (same Wi-Fi): ${url}`);
  if (!process.env.ANTHROPIC_API_KEY && !process.env.ANTHROPIC_AUTH_TOKEN) {
    console.warn("  ! ANTHROPIC_API_KEY is not set: chat replies will fail until you add it to .env");
  }
  if (!config.image.apiKey) console.warn("  ! IMAGE_API_KEY is not set: image generation is disabled");
  if (config.subscription.testMode) console.warn("  ! PRO_TEST_MODE is on: purchases are not verified with the stores");
});
