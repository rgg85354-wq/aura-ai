import fs from "node:fs";
import path from "node:path";
import { Router } from "express";
import { Environment, SignedDataVerifier } from "@apple/app-store-server-library";
import { GoogleAuth } from "google-auth-library";
import { requireAuth } from "./auth.js";
import { config } from "./config.js";
import { accountJSON, db } from "./db.js";
import { HttpError, badRequest } from "./http.js";

const DAY = 24 * 60 * 60 * 1000;
const sub = config.subscription;

export const subscriptionRouter = Router();
subscriptionRouter.use(requireAuth);

subscriptionRouter.get("/subscription/products", (req, res) => {
  res.json({ productIds: sub.productIds, testMode: sub.testMode });
});

/**
 * The app calls this after a purchase and on every launch (restore).
 * Body: { platform: "apple", signedTransaction } or { platform: "google", productId, purchaseToken }.
 */
subscriptionRouter.post("/subscription/verify", async (req, res) => {
  const platform = req.body?.platform;
  let result;
  if (platform === "apple") result = await verifyApple(String(req.body?.signedTransaction ?? ""));
  else if (platform === "google") {
    result = await verifyGoogle(String(req.body?.productId ?? ""), String(req.body?.purchaseToken ?? ""));
  } else throw badRequest("Неизвестная платформа покупки");

  if (!sub.productIds.includes(result.productId)) throw badRequest("Неизвестный товар подписки");
  if (result.expiresAt <= Date.now()) {
    throw new HttpError(402, "subscription_expired", "Срок подписки истёк");
  }

  // One store subscription unlocks one Aura account.
  const owner = db
    .prepare("SELECT id FROM users WHERE pro_original_id = ? AND pro_platform = ? AND id <> ?")
    .get(result.originalId, platform, req.user.id);
  if (owner) {
    throw new HttpError(409, "subscription_in_use", "Эта подписка уже привязана к другому аккаунту Aura");
  }

  db.prepare(
    "UPDATE users SET pro_until = ?, pro_platform = ?, pro_product = ?, pro_original_id = ? WHERE id = ?",
  ).run(result.expiresAt, platform, result.productId, result.originalId, req.user.id);
  const user = db.prepare("SELECT * FROM users WHERE id = ?").get(req.user.id);
  res.json(accountJSON(user));
});

// ---------- Apple (StoreKit 2 signed transactions) ----------

let appleVerifier = null;

function getAppleVerifier() {
  if (appleVerifier) return appleVerifier;
  const dir = sub.apple.rootCertsDir;
  const certs = fs.existsSync(dir)
    ? fs.readdirSync(dir).filter((f) => /\.(cer|der)$/i.test(f)).map((f) => fs.readFileSync(path.join(dir, f)))
    : [];
  if (!certs.length) {
    throw new HttpError(
      503,
      "store_not_configured",
      "Проверка покупок Apple не настроена: положите AppleRootCA-G3.cer в папку server/certs.",
    );
  }
  const env = sub.apple.environment === "Production" ? Environment.PRODUCTION : Environment.SANDBOX;
  appleVerifier = new SignedDataVerifier(certs, true, env, sub.apple.bundleId, sub.apple.appAppleId);
  return appleVerifier;
}

function decodeJwsUnverified(jws) {
  const payload = jws.split(".")[1];
  if (!payload) throw badRequest("Некорректные данные покупки");
  return JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
}

async function verifyApple(signedTransaction) {
  if (!signedTransaction) throw badRequest("Нет данных покупки");
  let tx;
  if (sub.testMode) {
    // Test mode accepts Xcode StoreKit test transactions, which are not signed by Apple.
    tx = decodeJwsUnverified(signedTransaction);
  } else {
    try {
      tx = await getAppleVerifier().verifyAndDecodeTransaction(signedTransaction);
    } catch (err) {
      if (err instanceof HttpError) throw err;
      throw new HttpError(400, "invalid_purchase", "Покупка не прошла проверку Apple");
    }
  }
  if (tx.revocationDate) throw new HttpError(402, "subscription_revoked", "Подписка была отменена");
  return {
    productId: tx.productId,
    originalId: String(tx.originalTransactionId ?? tx.transactionId),
    expiresAt: Number(tx.expiresDate ?? Date.now() + 30 * DAY),
  };
}

// ---------- Google Play ----------

let googleAuth = null;

async function verifyGoogle(productId, purchaseToken) {
  if (!productId || !purchaseToken) throw badRequest("Нет данных покупки");
  if (sub.testMode) {
    return { productId, originalId: purchaseToken.slice(0, 120), expiresAt: Date.now() + 30 * DAY };
  }
  if (!sub.google.serviceAccountFile || !fs.existsSync(sub.google.serviceAccountFile)) {
    throw new HttpError(
      503,
      "store_not_configured",
      "Проверка покупок Google Play не настроена: укажите GOOGLE_SERVICE_ACCOUNT_FILE.",
    );
  }
  googleAuth ??= new GoogleAuth({
    keyFile: sub.google.serviceAccountFile,
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${encodeURIComponent(sub.google.packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;

  let data;
  try {
    const client = await googleAuth.getClient();
    data = (await client.request({ url })).data;
  } catch (err) {
    const status = err?.response?.status;
    if (status === 400 || status === 404 || status === 410) {
      throw new HttpError(400, "invalid_purchase", "Покупка не найдена в Google Play");
    }
    console.error("Google Play verification failed", err?.message);
    throw new HttpError(502, "store_unreachable", "Не удалось проверить покупку в Google Play");
  }

  const activeStates = ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"];
  const line = (data.lineItems ?? []).find((l) => l.productId === productId) ?? data.lineItems?.[0];
  if (!activeStates.includes(data.subscriptionState) || !line) {
    throw new HttpError(402, "subscription_expired", "Подписка неактивна");
  }
  return {
    productId: line.productId,
    originalId: String(data.latestOrderId ?? purchaseToken).replace(/\.\.\d+$/, ""),
    expiresAt: Date.parse(line.expiryTime),
  };
}
