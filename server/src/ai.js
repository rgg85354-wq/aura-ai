import fs from "node:fs";
import Anthropic from "@anthropic-ai/sdk";
import { config } from "./config.js";
import { filePath } from "./db.js";

let client = null;

// Created lazily so the server still starts (and reports a clear error) without a key.
function getClient() {
  if (!client) client = new Anthropic();
  return client;
}

export const MODES = ["chat", "code", "ideas"];

const BASE_PROMPT = `Ты — Aura, ИИ-ассистент в мобильном приложении Aura AI.
Отвечай на языке пользователя, если ниже не указан другой язык.
Пиши понятно и по делу. Используй Markdown: заголовки, списки, **жирный** текст и блоки кода с указанием языка.
Ответ читают на экране телефона, поэтому избегай широких таблиц и очень длинных строк.
Если пользователь прислал изображение или файл, опирайся на их содержимое.
Если чего-то не знаешь или не уверен, так и скажи.`;

const MODE_PROMPTS = {
  chat: "Режим: общение. Будь дружелюбным собеседником и помощником в любых вопросах.",
  code: `Режим: программирование. Ты опытный инженер-программист.
Когда пишешь код, давай полный рабочий фрагмент в блоке \`\`\`язык, затем коротко объясняй ключевые решения.
Когда объясняешь чужой код, разбирай его по шагам и указывай на ошибки и возможные улучшения.`,
  ideas: `Режим: генерация идей. Предлагай разнообразные, конкретные и необычные идеи.
По умолчанию дай 5–8 идей пронумерованным списком: короткое название жирным и 1–2 предложения описания.
В конце предложи, какую идею развить дальше.`,
};

const STYLE_PROMPTS = {
  concise: "Стиль ответов: кратко, только самое важное.",
  balanced: "Стиль ответов: сбалансированно, без лишней воды.",
  detailed: "Стиль ответов: подробно, с примерами и пояснениями.",
};

const LANGUAGE_NAMES = {
  ru: "русском",
  en: "английском",
  uk: "украинском",
  kk: "казахском",
  de: "немецком",
  es: "испанском",
  fr: "французском",
};

export function systemPrompt(mode, user) {
  const parts = [BASE_PROMPT, MODE_PROMPTS[mode] ?? MODE_PROMPTS.chat, STYLE_PROMPTS[user.response_style] ?? ""];
  if (LANGUAGE_NAMES[user.language]) parts.push(`Всегда отвечай на ${LANGUAGE_NAMES[user.language]} языке.`);
  return parts.filter(Boolean).join("\n\n");
}

const IMAGE_MIME = new Set(["image/jpeg", "image/png", "image/gif", "image/webp"]);
const TEXT_EXT = /\.(txt|md|csv|tsv|json|xml|ya?ml|html?|css|js|jsx|ts|tsx|py|java|kt|kts|swift|c|h|cpp|hpp|cs|go|rs|rb|php|sh|sql|ini|toml|gradle|log)$/i;
const MAX_TEXT_FILE_CHARS = 150_000;
// Attachments are sent in full only for the most recent user turns to keep requests small.
const FULL_ATTACHMENT_TURNS = 6;

function isTextFile(file) {
  return file.mime.startsWith("text/") || file.mime === "application/json" || TEXT_EXT.test(file.name);
}

function attachmentBlocks(file, full) {
  if (!full) return [{ type: "text", text: `[Ранее приложенный файл: ${file.name}]` }];
  let data;
  try {
    data = fs.readFileSync(filePath(file.id));
  } catch {
    return [{ type: "text", text: `[Файл ${file.name} недоступен]` }];
  }
  if (IMAGE_MIME.has(file.mime)) {
    return [{ type: "image", source: { type: "base64", media_type: file.mime, data: data.toString("base64") } }];
  }
  if (file.mime === "application/pdf") {
    return [
      {
        type: "document",
        title: file.name,
        source: { type: "base64", media_type: "application/pdf", data: data.toString("base64") },
      },
    ];
  }
  if (isTextFile(file)) {
    let text = data.toString("utf8");
    const cut = text.length > MAX_TEXT_FILE_CHARS;
    if (cut) text = text.slice(0, MAX_TEXT_FILE_CHARS);
    return [
      {
        type: "text",
        text: `Файл «${file.name}»${cut ? " (показано начало, файл очень большой)" : ""}:\n\`\`\`\n${text}\n\`\`\``,
      },
    ];
  }
  return [{ type: "text", text: `[Файл ${file.name} (${file.mime}) — этот формат нельзя прочитать]` }];
}

/**
 * Turn stored chat history into Claude messages.
 * @param {Array<{id:string, role:string, content:string, status:string}>} history oldest first
 * @param {Map<string, Array<{id:string,name:string,mime:string}>>} filesByMessage
 * @returns {Anthropic.Beta.BetaMessageParam[]}
 */
export function buildMessages(history, filesByMessage) {
  const usable = history.filter(
    (m) => m.role === "user" || (m.role === "assistant" && m.content.trim() && m.status !== "error" && m.status !== "refused"),
  );
  // Claude conversations must start with a user turn.
  while (usable.length && usable[0].role !== "user") usable.shift();

  const userIndexes = usable.map((m, i) => (m.role === "user" ? i : -1)).filter((i) => i >= 0);
  const fullFrom = userIndexes.length > FULL_ATTACHMENT_TURNS ? userIndexes[userIndexes.length - FULL_ATTACHMENT_TURNS] : 0;

  return usable.map((m, i) => {
    if (m.role === "assistant") return { role: "assistant", content: m.content };
    const files = filesByMessage.get(m.id) ?? [];
    const content = files.flatMap((f) => attachmentBlocks(f, i >= fullFrom));
    content.push({ type: "text", text: m.content.trim() || "Посмотри на вложение." });
    return { role: "user", content };
  });
}

const EFFORT = {
  free: { chat: "low", ideas: "low", code: "medium" },
  pro: { chat: "medium", ideas: "medium", code: "high" },
};

/**
 * Stream a reply from Claude. Calls onText for each text fragment and returns the final message.
 */
export async function streamReply({ messages, mode, user, pro, onText, signal }) {
  const stream = getClient().beta.messages.stream(
    {
      model: config.model,
      max_tokens: 64000,
      // On a safety decline the API retries on Anthropic's recommended fallback model.
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
      output_config: { effort: EFFORT[pro ? "pro" : "free"][mode] ?? "medium" },
      system: [{ type: "text", text: systemPrompt(mode, user), cache_control: { type: "ephemeral" } }],
      messages,
    },
    { signal },
  );

  for await (const event of stream) {
    if (event.type === "content_block_delta" && event.delta.type === "text_delta") {
      onText(event.delta.text);
    }
  }
  return stream.finalMessage();
}

/** Map SDK errors to a user-facing code and Russian message. */
export function describeAiError(err) {
  if (err instanceof Anthropic.APIUserAbortError) return { code: "cancelled", message: "Генерация остановлена" };
  if (err instanceof Anthropic.AuthenticationError || err instanceof Anthropic.PermissionDeniedError) {
    return { code: "ai_not_configured", message: "ИИ не настроен на сервере: проверьте ANTHROPIC_API_KEY." };
  }
  if (err instanceof Anthropic.RateLimitError) {
    return { code: "ai_busy", message: "ИИ сейчас перегружен. Попробуйте через минуту." };
  }
  if (err instanceof Anthropic.BadRequestError) {
    return { code: "ai_bad_request", message: `ИИ не принял запрос: ${err.message}` };
  }
  if (err instanceof Anthropic.APIConnectionError) {
    return { code: "ai_unreachable", message: "Сервер не может связаться с ИИ. Проверьте интернет на сервере." };
  }
  if (err instanceof Anthropic.APIError) {
    if (err.status === 529 || (err.status ?? 0) >= 500) {
      return { code: "ai_busy", message: "ИИ временно недоступен. Попробуйте ещё раз." };
    }
    return { code: "ai_error", message: `Ошибка ИИ (${err.status ?? "?"}): ${err.message}` };
  }
  if (err instanceof Anthropic.AnthropicError) {
    // Thrown by the constructor when no credentials are configured.
    return { code: "ai_not_configured", message: "ИИ не настроен на сервере: задайте ANTHROPIC_API_KEY в файле .env." };
  }
  console.error(err);
  return { code: "server_error", message: "Не удалось получить ответ ИИ" };
}
