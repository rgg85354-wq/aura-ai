import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const bool = (v) => ["1", "true", "yes", "on"].includes(String(v ?? "").trim().toLowerCase());
const int = (v, fallback) => {
  const n = Number.parseInt(String(v ?? ""), 10);
  return Number.isFinite(n) ? n : fallback;
};

const dataDir = path.resolve(root, process.env.DATA_DIR || "data");
fs.mkdirSync(path.join(dataDir, "files"), { recursive: true });

// Secret used to sign file URLs. Generated once and kept in the data folder.
function loadSecret() {
  if (process.env.AURA_SECRET) return process.env.AURA_SECRET;
  const file = path.join(dataDir, "secret.key");
  if (!fs.existsSync(file)) fs.writeFileSync(file, crypto.randomBytes(32).toString("hex"));
  return fs.readFileSync(file, "utf8").trim();
}

export const config = {
  root,
  dataDir,
  filesDir: path.join(dataDir, "files"),
  port: int(process.env.PORT, 8787),
  host: process.env.HOST || "0.0.0.0",
  secret: loadSecret(),

  // Claude model used for chat, code and ideas.
  model: process.env.AURA_MODEL || "claude-opus-5-5",

  // Image generation: any endpoint compatible with POST {base}/images/generations.
  image: {
    apiKey: process.env.IMAGE_API_KEY || "",
    apiBase: (process.env.IMAGE_API_BASE || "https://api.openai.com/v1").replace(/\/+$/, ""),
    model: process.env.IMAGE_MODEL || "gpt-image-1",
  },

  limits: {
    free: { messages: int(process.env.FREE_DAILY_MESSAGES, 30), images: int(process.env.FREE_DAILY_IMAGES, 5) },
    pro: { messages: int(process.env.PRO_DAILY_MESSAGES, 1000), images: int(process.env.PRO_DAILY_IMAGES, 100) },
  },
  maxUploadBytes: int(process.env.MAX_UPLOAD_MB, 20) * 1024 * 1024,

  subscription: {
    productIds: ["aura_pro_monthly", "aura_pro_yearly"],
    // Only for testing purchases before store credentials are set up.
    testMode: bool(process.env.PRO_TEST_MODE),
    apple: {
      bundleId: process.env.APPLE_BUNDLE_ID || "com.aura.ai",
      appAppleId: process.env.APPLE_APP_ID ? Number(process.env.APPLE_APP_ID) : undefined,
      environment: process.env.APPLE_ENVIRONMENT || "Sandbox", // Sandbox | Production
      rootCertsDir: path.resolve(root, process.env.APPLE_ROOT_CERTS_DIR || "certs"),
    },
    google: {
      packageName: process.env.GOOGLE_PACKAGE_NAME || "com.aura.ai",
      serviceAccountFile: process.env.GOOGLE_SERVICE_ACCOUNT_FILE
        ? path.resolve(root, process.env.GOOGLE_SERVICE_ACCOUNT_FILE)
        : "",
    },
  },
};
