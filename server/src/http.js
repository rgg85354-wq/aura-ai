// Shared HTTP helpers: typed errors and a tiny in-memory rate limiter.

export class HttpError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export const badRequest = (message, code = "bad_request") => new HttpError(400, code, message);
export const notFound = (message = "Не найдено") => new HttpError(404, "not_found", message);

export function limitReached(kind) {
  const what = kind === "images" ? "генераций изображений" : "сообщений";
  return new HttpError(
    402,
    "limit_reached",
    `Дневной лимит ${what} исчерпан. Оформите Aura Pro, чтобы продолжить.`,
  );
}

export function sendError(res, err) {
  if (err instanceof HttpError) {
    return res.status(err.status).json({ error: { code: err.code, message: err.message } });
  }
  if (err?.type === "entity.too.large") {
    return res.status(413).json({ error: { code: "too_large", message: "Файл слишком большой" } });
  }
  if (err?.type === "entity.parse.failed") {
    return res.status(400).json({ error: { code: "bad_json", message: "Некорректный JSON" } });
  }
  console.error(err);
  return res.status(500).json({ error: { code: "server_error", message: "Внутренняя ошибка сервера" } });
}

export function rateLimit({ windowMs, max }) {
  const hits = new Map();
  setInterval(() => {
    const cutoff = Date.now() - windowMs;
    for (const [key, list] of hits) {
      const fresh = list.filter((t) => t > cutoff);
      if (fresh.length) hits.set(key, fresh);
      else hits.delete(key);
    }
  }, windowMs).unref();

  return (req, res, next) => {
    const key = req.ip || "unknown";
    const cutoff = Date.now() - windowMs;
    const list = (hits.get(key) || []).filter((t) => t > cutoff);
    if (list.length >= max) {
      return sendError(res, new HttpError(429, "too_many_requests", "Слишком много попыток. Подождите минуту."));
    }
    list.push(Date.now());
    hits.set(key, list);
    next();
  };
}
