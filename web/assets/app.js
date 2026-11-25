/* Aura web app: the same account, chats and images as the iOS and Android apps. */
(() => {
  "use strict";

  // ---------- helpers ----------
  const $ = (sel, el = document) => el.querySelector(sel);
  const esc = (s) =>
    String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

  const ICONS = {
    chat: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    image: '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21"/>',
    code: '<path d="m16 18 6-6-6-6M8 6l-6 6 6 6"/>',
    idea: '<path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5M9 18h6M10 22h4"/>',
    home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    clip: '<path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48"/>',
    up: '<path d="M12 19V5M6 11l6-6 6 6"/>',
    stop: '<rect x="7" y="7" width="10" height="10" rx="2" fill="currentColor"/>',
    copy: '<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
    check: '<path d="M20 6 9 17l-5-5"/>',
    retry: '<path d="M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5"/>',
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    close: '<path d="M18 6 6 18M6 6l12 12"/>',
    more: '<circle cx="5" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="19" cy="12" r="1.2"/>',
    file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h5"/>',
    arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    server: '<rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 7.5h.01M7 16.5h.01"/>',
    sparkles: '<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z"/>',
    download: '<path d="M12 3v12M7 10l5 5 5-5M5 21h14"/>',
    trash: '<path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    sync: '<path d="M21 12a9 9 0 0 1-15.7 6M3 12a9 9 0 0 1 15.7-6M21 3v5h-5M3 21v-5h5"/>',
    lock: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
    globe: '<circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20"/>',
    sliders: '<path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/>',
    edit: '<path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
    crown: '<path d="M2 18h20M3 8l4 4 5-7 5 7 4-4-2 10H5z"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
  };
  const icon = (name, size) =>
    `<svg class="icon" viewBox="0 0 24 24"${size ? ` style="width:${size}px;height:${size}px"` : ""}>${ICONS[name] || ""}</svg>`;
  const spark = (size) => `<span class="spark" style="width:${size}px;height:${size}px"></span>`;

  const MODES = {
    chat: {
      title: "Чат", icon: "chat", placeholder: "Спросите что-нибудь…", empty: "Чем могу помочь?",
      suggestions: ["Объясни простыми словами, как работает нейросеть", "Составь план тренировок на неделю", "Помоги написать вежливое письмо преподавателю"],
    },
    code: {
      title: "Код", icon: "code", placeholder: "Опишите задачу или вставьте код…", empty: "Напишу, объясню и исправлю код",
      suggestions: ["Напиши функцию на JavaScript, которая проверяет палиндром", "Объясни, что такое async/await, с примером", "Как сделать REST API на Python за 10 минут?"],
    },
    ideas: {
      title: "Идеи", icon: "idea", placeholder: "Для чего нужны идеи?", empty: "Придумаем что-нибудь классное",
      suggestions: ["Идеи для мобильного приложения", "Чем заняться на выходных с друзьями", "Идеи подарка на день рождения"],
    },
  };
  const IMAGE_STYLES = [["none", "Без стиля"], ["photo", "Фото"], ["3d", "3D"], ["anime", "Аниме"], ["art", "Арт"], ["minimal", "Минимализм"], ["neon", "Неон"]];
  const IMAGE_SIZES = [["1024x1024", "1:1"], ["1024x1536", "2:3"], ["1536x1024", "3:2"]];
  const RESPONSE_STYLES = [["concise", "Кратко"], ["balanced", "Баланс"], ["detailed", "Подробно"]];
  const LANGUAGES = [["auto", "Как в вопросе"], ["ru", "Русский"], ["en", "English"], ["uk", "Українська"], ["kk", "Қазақша"], ["de", "Deutsch"], ["es", "Español"], ["fr", "Français"]];
  const MAX_FILES = 5;

  const store = {
    get: (k) => { try { return JSON.parse(localStorage.getItem(`aura.${k}`)); } catch { return null; } },
    set: (k, v) => { try { v == null ? localStorage.removeItem(`aura.${k}`) : localStorage.setItem(`aura.${k}`, JSON.stringify(v)); } catch {} },
  };

  // ---------- state ----------
  const S = {
    server: store.get("server") || "",
    token: store.get("token") || "",
    account: store.get("account"),
    chats: store.get("chats") || [],
    images: store.get("images") || [],
    lastSync: store.get("lastSync") || 0,
    view: "home",
    chat: null,
    mode: "chat",
    messages: [],
    loadingChat: false,
    draft: "",
    pending: [],
    streaming: false,
    abort: null,
    error: null,
    limit: false,
    query: "",
    searchResults: null,
    sideOpen: false,
    syncing: false,
    authMode: "login",
    authError: null,
    authBusy: false,
    img: { prompt: "", style: "none", size: "1024x1024", busy: false, error: null, limit: false, latest: null },
  };

  // ---------- API ----------
  class ApiError extends Error {
    constructor(code, message, status = 0) { super(message); this.code = code; this.status = status; }
  }
  const NETWORK = () => new ApiError("network", "Нет связи с сервером Aura. Проверьте интернет и адрес сервера.");

  async function errorFrom(res) {
    let info = null;
    try { info = (await res.json()).error; } catch {}
    if (res.status === 401 && S.token) setTimeout(signOutLocally);
    return new ApiError(info?.code || `http_${res.status}`, info?.message || `Ошибка сервера (${res.status})`, res.status);
  }

  function authHeaders(extra = {}) {
    return { ...(S.token ? { Authorization: `Bearer ${S.token}` } : {}), ...extra };
  }

  async function api(method, path, body) {
    if (!S.server) throw new ApiError("no_server", "Укажите адрес сервера Aura");
    let res;
    try {
      res = await fetch(`${S.server}/api${path}`, {
        method,
        headers: authHeaders(body !== undefined ? { "Content-Type": "application/json" } : {}),
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch { throw NETWORK(); }
    if (!res.ok) throw await errorFrom(res);
    return res.json();
  }

  /** Reads the server's SSE stream: each event is "event: name" + one "data: {...}" line. */
  async function stream(path, body, onEvent, signal) {
    let res;
    try {
      res = await fetch(`${S.server}/api${path}`, {
        method: "POST",
        headers: authHeaders({ Accept: "text/event-stream", ...(body ? { "Content-Type": "application/json" } : {}) }),
        body: body ? JSON.stringify(body) : undefined,
        signal,
      });
    } catch (e) {
      if (e.name === "AbortError") throw e;
      throw NETWORK();
    }
    if (!res.ok) throw await errorFrom(res);
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let cut;
        while ((cut = buffer.indexOf("\n\n")) >= 0) {
          const block = buffer.slice(0, cut);
          buffer = buffer.slice(cut + 2);
          let event = "";
          let data = "";
          for (const line of block.split("\n")) {
            if (line.startsWith("event:")) event = line.slice(6).trim();
            else if (line.startsWith("data:")) data += line.slice(5).trim();
          }
          if (event && data) {
            try { onEvent(event, JSON.parse(data)); } catch (err) { console.error(err); }
          }
        }
      }
    } catch (e) {
      if (e.name === "AbortError") throw e;
      throw new ApiError("network", "Соединение прервалось во время ответа. Нажмите «Повторить».");
    }
  }

  async function uploadFile(blob, mime, name) {
    let res;
    try {
      res = await fetch(`${S.server}/api/uploads`, {
        method: "POST",
        headers: authHeaders({ "Content-Type": mime, "X-File-Name": encodeURIComponent(name) }),
        body: blob,
      });
    } catch { throw NETWORK(); }
    if (!res.ok) throw await errorFrom(res);
    return (await res.json()).file;
  }

  const fileUrl = (path) => (path?.startsWith("http") ? path : `${S.server}${path}`);

  // ---------- data ----------
  function setAccount(acc) { S.account = acc; store.set("account", acc); }
  function setChats(list) { S.chats = [...list].sort((a, b) => b.updatedAt - a.updatedAt); store.set("chats", S.chats); }
  function setImages(list) { S.images = list; store.set("images", list); }
  function upsertChat(chat) { setChats([chat, ...S.chats.filter((c) => c.id !== chat.id)]); }
  function bumpUsage(messages = 0, images = 0) {
    if (!S.account) return;
    const u = S.account.usage;
    setAccount({ ...S.account, usage: { messagesToday: u.messagesToday + messages, imagesToday: u.imagesToday + images } });
  }

  async function sync({ quiet = true } = {}) {
    if (!S.token) return;
    S.syncing = true;
    if (!quiet) render();
    try {
      const [me, chats, images] = await Promise.all([api("GET", "/me"), api("GET", "/chats"), api("GET", "/images")]);
      setAccount(me);
      setChats(chats.chats);
      setImages(images.images);
      S.lastSync = Date.now();
      store.set("lastSync", S.lastSync);
    } catch (e) {
      if (!quiet) toast(e.message, true);
    } finally {
      S.syncing = false;
      render();
    }
  }

  function signOutLocally() {
    if (S.abort) S.abort.abort();
    S.token = "";
    store.set("token", null);
    setAccount(null);
    setChats([]);
    setImages([]);
    Object.assign(S, { view: "home", chat: null, messages: [], pending: [], draft: "", error: null, limit: false, searchResults: null, query: "" });
    render();
  }

  // ---------- formatting ----------
  const DAY = 86400000;
  function sameDay(a, b) { const x = new Date(a), y = new Date(b); return x.toDateString() === y.toDateString(); }
  function relative(ms) {
    if (!ms) return "";
    const min = Math.floor((Date.now() - ms) / 60000);
    if (min < 1) return "только что";
    if (min < 60) return `${min} мин назад`;
    if (min < 1440) return `${Math.floor(min / 60)} ч назад`;
    if (sameDay(ms, Date.now() - DAY)) return "вчера";
    return new Date(ms).toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
  }
  function dayGroup(ms) {
    if (sameDay(ms, Date.now())) return "Сегодня";
    if (sameDay(ms, Date.now() - DAY)) return "Вчера";
    if (Date.now() - ms < 7 * DAY) return "На этой неделе";
    return "Ранее";
  }
  const fullDate = (ms) => new Date(ms).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" });
  function fileSize(b) {
    if (b < 1024) return `${b} Б`;
    if (b < 1048576) return `${Math.round(b / 1024)} КБ`;
    return `${(b / 1048576).toFixed(1)} МБ`;
  }

  // ---------- markdown (escape first, then format) ----------
  function inline(text) {
    const codes = [];
    let s = String(text).replace(/`([^`\n]+)`/g, (_, c) => { codes.push(c); return `\u0000${codes.length - 1}\u0000`; });
    s = esc(s);
    s = s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
    s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>").replace(/__([^_]+)__/g, "<strong>$1</strong>");
    s = s.replace(/(^|[^*\w])\*([^*\s][^*]*?)\*(?!\*)/g, "$1<em>$2</em>");
    return s.replace(/\u0000(\d+)\u0000/g, (_, i) => `<code>${esc(codes[+i])}</code>`);
  }
  function codeBlock(lang, code) {
    return `<div class="code"><div class="code-head"><span>${esc(lang || "code")}</span>
      <button data-action="copy-code">${icon("copy", 14)}Копировать</button></div><pre><code>${esc(code)}</code></pre></div>`;
  }
  function md(src) {
    const lines = String(src).replace(/\r\n/g, "\n").split("\n");
    let html = "";
    let para = [];
    let list = null;
    const flushPara = () => { if (para.length) { html += `<p>${inline(para.join("\n")).replace(/\n/g, "<br>")}</p>`; para = []; } };
    const flushList = () => {
      if (!list) return;
      const start = list.type === "ol" && list.start !== "1" ? ` start="${esc(list.start)}"` : "";
      html += `<${list.type}${start}>${list.items.map((i) => `<li>${inline(i)}</li>`).join("")}</${list.type}>`;
      list = null;
    };
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const t = line.trim();
      let m;
      if (t.startsWith("```")) {
        flushPara(); flushList();
        const lang = t.slice(3).trim();
        const code = [];
        i++;
        while (i < lines.length && !lines[i].trim().startsWith("```")) code.push(lines[i++]);
        html += codeBlock(lang, code.join("\n"));
      } else if (!t) {
        flushPara(); flushList();
      } else if ((m = t.match(/^(#{1,6})\s+(.*)$/))) {
        flushPara(); flushList();
        const level = Math.min(m[1].length, 4);
        html += `<h${level}>${inline(m[2])}</h${level}>`;
      } else if (/^(-{3,}|\*{3,})$/.test(t)) {
        flushPara(); flushList(); html += "<hr>";
      } else if (t.startsWith(">")) {
        flushPara(); flushList(); html += `<blockquote>${inline(t.replace(/^>\s?/, ""))}</blockquote>`;
      } else if ((m = t.match(/^[-*•+]\s+(.*)$/))) {
        flushPara();
        if (!list || list.type !== "ul") { flushList(); list = { type: "ul", items: [] }; }
        list.items.push(m[1]);
      } else if ((m = t.match(/^(\d+)[.)]\s+(.*)$/))) {
        flushPara();
        if (!list || list.type !== "ol") { flushList(); list = { type: "ol", start: m[1], items: [] }; }
        list.items.push(m[2]);
      } else {
        flushList();
        para.push(line);
      }
    }
    flushPara(); flushList();
    return html;
  }

  // ---------- UI primitives ----------
  function toast(text, error = false) {
    const el = document.createElement("div");
    el.className = `toast glass strong${error ? " error" : ""}`;
    el.style.color = error ? "var(--danger)" : "var(--text)";
    el.textContent = text;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 3200);
  }

  /** Small modal. Resolves with the field values (or true) on confirm, null on cancel. */
  function modal({ title, text = "", fields = [], confirm = "OK", danger = false, cancel = "Отмена", extra = "" }) {
    return new Promise((resolve) => {
      const back = document.createElement("div");
      back.className = "modal-back";
      back.innerHTML = `<form class="modal glass strong">
        <h3>${esc(title)}</h3>${text ? `<p>${esc(text)}</p>` : ""}
        ${fields.map((f) => `<input class="field" name="${f.name}" type="${f.type || "text"}" placeholder="${esc(f.placeholder || "")}" value="${esc(f.value || "")}" ${f.autocomplete ? `autocomplete="${f.autocomplete}"` : ""}>`).join("")}
        ${extra}
        <div class="actions">
          ${cancel ? `<button type="button" class="btn btn-glass btn-sm" data-cancel>${esc(cancel)}</button>` : ""}
          <button type="submit" class="btn ${danger ? "btn-danger" : "btn-primary"} btn-sm">${esc(confirm)}</button>
        </div></form>`;
      const close = (value) => { back.remove(); resolve(value); };
      back.addEventListener("click", (e) => { if (e.target === back || e.target.closest("[data-cancel]")) close(null); });
      back.querySelector("form").addEventListener("submit", (e) => {
        e.preventDefault();
        if (!fields.length) return close(true);
        const values = Object.fromEntries(fields.map((f) => [f.name, back.querySelector(`[name="${f.name}"]`).value]));
        close(values);
      });
      document.body.appendChild(back);
      (back.querySelector("input") || back.querySelector("[type=submit]")).focus();
    });
  }

  function lightbox(image, { deletable = false } = {}) {
    const el = document.createElement("div");
    el.className = "lightbox";
    el.innerHTML = `<button class="icon-btn close" data-x>${icon("close")}</button>
      <img src="${esc(fileUrl(image.url))}" alt="">
      ${image.prompt ? `<p>${esc(image.prompt)}</p>` : ""}
      <div class="bar-actions">
        <a class="btn btn-glass btn-sm" href="${esc(fileUrl(image.url))}" download="aura-${esc(image.id.slice(0, 8))}.png" target="_blank" rel="noopener">${icon("download", 18)}Скачать</a>
        ${deletable ? `<button class="btn btn-danger btn-sm" data-del>${icon("trash", 18)}Удалить</button>` : ""}
      </div>`;
    el.addEventListener("click", async (e) => {
      if (e.target === el || e.target.closest("[data-x]")) el.remove();
      if (e.target.closest("[data-del]")) {
        const ok = await modal({ title: "Удалить изображение?", text: "Оно исчезнет из галереи на всех устройствах.", confirm: "Удалить", danger: true });
        if (!ok) return;
        try {
          await api("DELETE", `/images/${image.id}`);
          setImages(S.images.filter((i) => i.id !== image.id));
          if (S.img.latest?.id === image.id) S.img.latest = null;
          el.remove();
          render();
        } catch (err) { toast(err.message, true); }
      }
    });
    document.body.appendChild(el);
  }

  async function serverDialog() {
    const insecure = location.protocol === "https:";
    const values = await modal({
      title: "Адрес сервера Aura",
      text: "Например https://140-238-1-2.sslip.io или http://192.168.1.20:8787" + (insecure ? ". Эта страница открыта по HTTPS, поэтому сервер тоже должен быть https://." : ""),
      fields: [{ name: "url", placeholder: "https://…", value: S.server || "" }],
      confirm: "Сохранить",
    });
    if (!values) return;
    let url = values.url.trim().replace(/\/+$/, "");
    if (!/^https?:\/\//.test(url)) url = `https://${url}`;
    try {
      const res = await fetch(`${url}/api/health`);
      if (!res.ok || !(await res.json()).ok) throw new Error();
    } catch {
      toast("Сервер не отвечает. Проверьте адрес.", true);
      return;
    }
    const changed = url !== S.server;
    S.server = url;
    store.set("server", url);
    if (changed && S.token) signOutLocally();
    toast("Сервер сохранён");
    render();
  }

  // ---------- render: auth ----------
  function renderAuth() {
    const reg = S.authMode === "register";
    return `<div class="auth"><div class="auth-card glass strong">
      <div class="auth-logo glass strong">${spark(52)}</div>
      <h1>Aura</h1>
      <p class="sub">${reg ? "Создайте аккаунт, чтобы история чатов была на всех устройствах" : "Войдите, чтобы продолжить общение с вашим ИИ-ассистентом"}</p>
      <div class="seg glass">
        <button data-action="auth-mode" data-mode="login" class="${reg ? "" : "on"}">Вход</button>
        <button data-action="auth-mode" data-mode="register" class="${reg ? "on" : ""}">Регистрация</button>
      </div>
      <form class="form" id="auth-form">
        ${reg ? '<input class="field" name="name" placeholder="Имя" autocomplete="name" required maxlength="50">' : ""}
        <input class="field" name="email" type="email" placeholder="Email" autocomplete="email" required>
        <input class="field" name="password" type="password" placeholder="Пароль (от 8 символов)" autocomplete="${reg ? "new-password" : "current-password"}" required minlength="8">
        ${S.authError ? `<div class="banner error">${esc(S.authError)}</div>` : ""}
        <button class="btn btn-primary" type="submit" ${S.authBusy ? "disabled" : ""}>${S.authBusy ? '<span class="spinner"></span>' : reg ? "Создать аккаунт" : "Войти"}</button>
      </form>
      <button class="link-btn" data-action="server" style="margin-top:18px">${icon("server", 15)}${S.server ? `Сервер: ${esc(S.server.replace(/^https?:\/\//, ""))}` : "Укажите адрес сервера Aura"}</button>
    </div></div>`;
  }

  async function submitAuth(form) {
    const data = Object.fromEntries(new FormData(form));
    S.authError = null;
    if (!S.server) { S.authError = "Сначала укажите адрес сервера Aura"; render(); return serverDialog(); }
    S.authBusy = true;
    render();
    try {
      const path = S.authMode === "register" ? "/auth/register" : "/auth/login";
      const res = await api("POST", path, { ...data, device: "Web" });
      S.token = res.token;
      store.set("token", res.token);
      setAccount({ user: res.user, usage: res.usage, limits: res.limits });
      S.authBusy = false;
      render();
      sync();
    } catch (e) {
      S.authBusy = false;
      S.authError = e.message;
      render();
    }
  }

  // ---------- render: shell ----------
  function chatListHTML() {
    const list = S.searchResults ?? S.chats;
    if (!list.length) return `<div class="side-empty">${S.query ? "Ничего не найдено" : "Здесь появятся ваши разговоры"}</div>`;
    let html = "";
    let group = "";
    for (const c of list) {
      const g = dayGroup(c.updatedAt);
      if (g !== group) { group = g; html += `<div class="chat-group">${g}</div>`; }
      html += `<div class="chat-item ${S.chat?.id === c.id && S.view === "chat" ? "on" : ""}" data-action="open-chat" data-id="${esc(c.id)}" role="button" tabindex="0">
        ${icon(MODES[c.mode]?.icon || "chat")}<span class="t">${esc(c.title)}</span>
        <button class="more" data-action="chat-menu" data-id="${esc(c.id)}" title="Действия">${icon("more", 16)}</button></div>`;
    }
    return html;
  }

  function renderApp() {
    const user = S.account?.user;
    const nav = (view, label, ic) => `<button data-action="view" data-view="${view}" class="${S.view === view ? "on" : ""}">${icon(ic)}${label}</button>`;
    return `<div class="app ${S.sideOpen ? "side-open" : ""}">
      <aside class="sidebar glass strong">
        <div class="side-brand"><span class="logo-tile glass strong">${spark(22)}</span><b>Aura</b>
          <button class="icon-btn close" data-action="side-close" title="Закрыть">${icon("close")}</button></div>
        <button class="btn btn-primary" data-action="new-chat" data-mode="chat">${icon("plus")}Новый чат</button>
        <nav class="side-nav">${nav("home", "Главная", "home")}${nav("images", "Изображения", "image")}${nav("settings", "Настройки", "settings")}</nav>
        <div class="side-search"><input class="field" id="search" placeholder="Поиск по чатам" value="${esc(S.query)}" autocomplete="off"></div>
        <div class="chat-list" id="chat-list">${chatListHTML()}</div>
        <button class="side-user" data-action="view" data-view="settings">
          <span class="avatar ${user?.isPro ? "pro" : ""}">${esc((user?.name || "A").trim().charAt(0).toUpperCase())}</span>
          <span><b>${esc(user?.name || "")}</b><small>${user?.isPro ? "Aura Pro" : "Бесплатный план"}</small></span>
        </button>
      </aside>
      <div class="scrim" data-action="side-close"></div>
      <main class="main">${renderMain()}</main>
    </div>`;
  }

  function topbar(title, actions = "") {
    return `<header class="topbar">
      <button class="icon-btn menu-btn" data-action="side-open" title="Меню">${icon("menu")}</button>
      <h2>${esc(title)}</h2>${actions}</header>`;
  }

  function renderMain() {
    switch (S.view) {
      case "chat": return renderChat();
      case "images": return renderImages();
      case "settings": return renderSettings();
      default: return renderHome();
    }
  }

  // ---------- render: home ----------
  function renderHome() {
    const user = S.account?.user;
    const first = (user?.name || "").split(" ")[0];
    const feature = (mode, title, sub, ic, action = "new-chat") =>
      `<button class="feature glass" data-action="${action}" data-mode="${mode}" data-view="images"><span class="icon-tile">${icon(ic, 22)}</span><span><b>${title}</b><br><small>${sub}</small></span></button>`;
    const recent = S.chats.slice(0, 4).map((c) => `<button class="row-card glass" data-action="open-chat" data-id="${esc(c.id)}">
        <span class="icon-tile" style="width:40px;height:40px">${icon(MODES[c.mode]?.icon || "chat")}</span>
        <div><b>${esc(c.title)}</b><small>${relative(c.updatedAt)}</small></div>${icon("arrow", 18)}</button>`).join("");
    return `${topbar("Главная", `<button class="pro-chip" data-action="view" data-view="settings"><span class="gold">♛</span>${user?.isPro ? "Pro ✓" : "Pro"}</button>`)}
      <div class="content"><div class="page">
        <div class="hero-card" data-action="new-chat" data-mode="chat">
          <div class="glow"></div>
          <h1>${first ? `Привет, ${esc(first)}!` : "Привет!"}</h1>
          <p>Чем я могу помочь тебе сегодня?</p>
          <span class="go">${icon("arrow")}</span>
          <div class="tile glass strong">${spark(62)}</div>
        </div>
        <div class="features">
          ${feature("chat", "Чат", "Общайся с ИИ", "chat")}
          ${feature("", "Изображения", "Создавай картинки", "image", "view")}
          ${feature("code", "Код", "Пиши и разбирай", "code")}
          ${feature("ideas", "Идеи", "Получай идеи", "idea")}
        </div>
        <div class="section-title"><h3>Недавние чаты</h3></div>
        ${recent || '<div class="row-card glass"><small>Здесь появятся ваши разговоры</small></div>'}
      </div></div>
      ${composerHTML()}`;
  }

  // ---------- render: chat ----------
  function messageHTML(m, isLast) {
    if (m.role === "user") {
      const atts = (m.attachments || []).map((a) => a.mime?.startsWith("image/")
        ? `<img src="${esc(fileUrl(a.url))}" alt="${esc(a.name)}" data-action="zoom" data-url="${esc(a.url)}">`
        : `<span class="file-chip glass">${icon("file")}<span><b>${esc(a.name)}</b><small>${fileSize(a.size || 0)}</small></span></span>`).join("");
      return `<div class="msg-user" data-mid="${esc(m.id)}">
        ${atts ? `<div class="attachments">${atts}</div>` : ""}
        ${m.content ? `<div class="bubble-user">${esc(m.content)}</div>` : ""}</div>`;
    }
    const streamingNow = m.status === "streaming";
    const note = { stopped: "Генерация остановлена", error: "Ответ не получен", refused: "Aura не может ответить на этот запрос", truncated: "Ответ получился слишком длинным и был обрезан" }[m.status];
    const body = !m.content && streamingNow
      ? '<div class="typing"><span class="dots"><i></i><i></i><i></i></span>Aura думает…</div>'
      : `<div class="md">${md(m.content)}</div>`;
    const actions = !streamingNow && !S.streaming
      ? `<div class="msg-actions">${m.content ? `<button data-action="copy-msg" data-id="${esc(m.id)}">${icon("copy")}Копировать</button>` : ""}
         ${isLast ? `<button data-action="retry">${icon("retry")}Повторить</button>` : ""}</div>`
      : "";
    return `<div class="msg-ai" data-mid="${esc(m.id)}">
      <span class="ai-avatar glass">${spark(17)}</span>
      <div><div class="bubble-ai glass">${body}${note ? `<div class="msg-note ${m.status === "error" ? "err" : ""}">${note}</div>` : ""}</div>${actions}</div></div>`;
  }

  function renderChat() {
    const mode = MODES[S.chat?.mode || S.mode] || MODES.chat;
    const title = S.chat?.title || `Новый чат · ${mode.title}`;
    const actions = S.chat ? `<button class="icon-btn" data-action="chat-menu" data-id="${esc(S.chat.id)}" title="Действия">${icon("more")}</button>` : "";
    let body;
    if (S.loadingChat && !S.messages.length) {
      body = '<div class="empty-chat"><span class="spinner"></span></div>';
    } else if (!S.messages.length) {
      body = `<div class="empty-chat"><div class="inner">
        <span class="logo-tile glass strong">${spark(46)}</span>
        <h2>${mode.empty}</h2>
        ${mode.suggestions.map((s) => `<button class="suggest glass" data-action="suggest" data-text="${esc(s)}">${esc(s)}</button>`).join("")}
      </div></div>`;
    } else {
      const last = S.messages[S.messages.length - 1]?.id;
      body = `<div class="messages" id="messages">${S.messages.map((m) => messageHTML(m, m.id === last)).join("")}</div>`;
    }
    return `${topbar(title, actions)}<div class="content" id="scroller">${body}</div>${composerHTML()}`;
  }

  function composerHTML() {
    const mode = MODES[S.chat?.mode || S.mode] || MODES.chat;
    const canSend = !S.streaming && !S.pending.some((p) => p.uploading) && (S.draft.trim() || S.pending.some((p) => p.attachment));
    const pending = S.pending.map((p) => `<div class="pending-item ${p.error ? "err" : ""}" title="${esc(p.error || p.name)}">
        ${p.preview ? `<img src="${p.preview}" alt="">` : `<span class="file-chip glass">${icon("file")}<span><b>${esc(p.name)}</b><small>${esc(p.error || fileSize(p.size))}</small></span></span>`}
        ${p.uploading ? '<span class="spin"><span class="spinner"></span></span>' : ""}
        <button class="x" data-action="remove-file" data-id="${p.lid}" title="Убрать">×</button></div>`).join("");
    const notices = [
      S.limit ? `<div class="notice"><div class="limit glass strong"><span class="gold">♛</span><div><b>Дневной лимит исчерпан</b><small>С Aura Pro можно общаться почти без ограничений. Оформить подписку можно в приложении для iPhone или Android.</small></div><button class="btn btn-glass btn-sm" data-action="dismiss-limit">OK</button></div></div>` : "",
      S.error ? `<div class="notice"><div class="banner error">${esc(S.error)}<button class="btn btn-glass btn-sm" style="margin-left:auto" data-action="dismiss-error">Закрыть</button></div></div>` : "",
    ].join("");
    return `<div class="composer-wrap">${notices}
      <div class="composer glass strong">
        ${pending ? `<div class="pending">${pending}</div>` : ""}
        <div class="row">
          <button class="icon-btn" data-action="attach" title="Прикрепить фото или файл">${icon("clip")}</button>
          <textarea id="input" rows="1" placeholder="${esc(mode.placeholder)}">${esc(S.draft)}</textarea>
          ${S.streaming
            ? `<button class="icon-btn filled" data-action="stop" title="Остановить">${icon("stop")}</button>`
            : `<button class="icon-btn ${canSend ? "filled" : ""}" id="send" data-action="send" title="Отправить" ${canSend ? "" : "disabled"}>${icon("up")}</button>`}
        </div>
      </div>
      <input type="file" id="file-input" multiple hidden accept="image/*,.pdf,.txt,.md,.csv,.json,.xml,.yaml,.yml,.html,.css,.js,.ts,.jsx,.tsx,.py,.java,.kt,.swift,.c,.h,.cpp,.cs,.go,.rs,.rb,.php,.sh,.sql">
    </div>`;
  }

  // ---------- render: images ----------
  function renderImages() {
    const acc = S.account;
    const left = acc ? Math.max(0, acc.limits.dailyImages - acc.usage.imagesToday) : null;
    const [w, h] = S.img.size.split("x").map(Number);
    const result = S.img.busy
      ? `<div class="generating" style="aspect-ratio:${w}/${h}"><div>${icon("sparkles")}<br>Рисую… обычно это 20–60 секунд</div></div>`
      : S.img.latest ? `<button data-action="open-image" data-id="${esc(S.img.latest.id)}"><img src="${esc(fileUrl(S.img.latest.url))}" alt=""></button>` : "";
    return `${topbar("Генерация изображений")}
      <div class="content"><div class="page">
        <textarea class="field" id="img-prompt" placeholder="Опишите картинку: «уютная кофейня в дождливый вечер, тёплый свет»">${esc(S.img.prompt)}</textarea>
        <div class="label">Стиль</div>
        <div class="chips">${IMAGE_STYLES.map(([id, l]) => `<button class="chip ${S.img.style === id ? "active" : ""}" data-action="img-style" data-id="${id}">${l}</button>`).join("")}</div>
        <div class="label">Формат</div>
        <div class="chips">${IMAGE_SIZES.map(([id, l]) => `<button class="chip ${S.img.size === id ? "active" : ""}" data-action="img-size" data-id="${id}">${l}</button>`).join("")}</div>
        <div style="display:flex;align-items:center;gap:14px;margin-top:20px;flex-wrap:wrap">
          <button class="btn btn-primary" data-action="generate" ${S.img.busy ? "disabled" : ""}>${S.img.busy ? '<span class="spinner"></span>' : icon("sparkles")}${S.img.busy ? "Создаю…" : "Создать"}</button>
          ${left !== null ? `<span class="muted" style="font-size:14px">Осталось сегодня: ${left} из ${acc.limits.dailyImages}</span>` : ""}
        </div>
        ${S.img.limit ? '<div class="banner error" style="margin-top:14px">Дневной лимит изображений исчерпан. С Aura Pro — 100 изображений в день.</div>' : ""}
        ${S.img.error ? `<div class="banner error" style="margin-top:14px">${esc(S.img.error)}</div>` : ""}
        ${result ? `<div class="gen-result" style="max-width:560px">${result}</div>` : ""}
        ${S.images.length ? `<div class="section-title"><h3>Галерея</h3></div>
          <div class="gallery">${S.images.map((im) => `<button data-action="open-image" data-id="${esc(im.id)}" title="${esc(im.prompt)}"><img src="${esc(fileUrl(im.url))}" alt="" loading="lazy"></button>`).join("")}</div>` : ""}
      </div></div>`;
  }

  // ---------- render: settings ----------
  function renderSettings() {
    const acc = S.account;
    const user = acc?.user;
    const usage = (label, used, limit) => `<div><div style="display:flex;justify-content:space-between"><span class="muted">${label}</span><b>${used} / ${limit}</b></div>
      <div class="bar"><i style="width:${limit ? Math.min(100, (used / limit) * 100) : 0}%"></i></div></div>`;
    return `${topbar("Настройки")}
      <div class="content"><div class="page">
        <div class="profile-head glass">
          <span class="avatar lg ${user?.isPro ? "pro" : ""}">${esc((user?.name || "A").charAt(0).toUpperCase())}</span>
          <div><h2>${esc(user?.name || "")}</h2><div class="muted">${esc(user?.email || "")}</div>
            <span class="badge ${user?.isPro ? "pro" : ""}">${user?.isPro ? `Aura Pro до ${fullDate(user.proUntil)}` : "Бесплатный план"}</span></div>
        </div>
        ${acc ? `<div class="usage glass"><b>Сегодня</b>${usage("Сообщения", acc.usage.messagesToday, acc.limits.dailyMessages)}${usage("Изображения", acc.usage.imagesToday, acc.limits.dailyImages)}</div>` : ""}
        ${user?.isPro ? "" : `<div class="pro-card"><span class="crown gold">♛</span><div><b>Aura Pro</b><div style="color:var(--violet-light);font-size:14px">До 1000 сообщений и 100 изображений в день, более глубокие ответы. Оформляется в приложении для iPhone или Android и действует во всём аккаунте.</div></div></div>`}

        <div class="group-title">Ответы ИИ</div>
        <div class="group glass">
          <div class="set-row">${icon("sliders")}Стиль ответов
            <div class="segmented">${RESPONSE_STYLES.map(([id, l]) => `<button class="${user?.preferences?.responseStyle === id ? "on" : ""}" data-action="pref-style" data-id="${id}">${l}</button>`).join("")}</div></div>
          <div class="set-row">${icon("globe")}Язык ответов
            <select class="field" id="pref-lang">${LANGUAGES.map(([id, l]) => `<option value="${id}" ${user?.preferences?.language === id ? "selected" : ""}>${l}</option>`).join("")}</select></div>
        </div>

        <div class="group-title">Аккаунт</div>
        <div class="group glass">
          <button class="set-row" data-action="edit-name">${icon("edit")}Имя<span class="v">${esc(user?.name || "")}</span></button>
          <div class="set-row">${icon("user")}Email<span class="v">${esc(user?.email || "")}</span></div>
          <button class="set-row" data-action="password">${icon("lock")}Сменить пароль</button>
          ${user ? `<div class="set-row">${icon("sparkles")}С нами с<span class="v">${fullDate(user.createdAt)}</span></div>` : ""}
        </div>

        <div class="group-title">Данные</div>
        <div class="group glass">
          <button class="set-row" data-action="sync">${icon("sync")}Синхронизация<span class="v">${S.syncing ? "Обновляю…" : S.lastSync ? `Обновлено ${relative(S.lastSync)}` : "Не выполнялась"}</span></button>
          <button class="set-row" data-action="server">${icon("server")}Сервер<span class="v">${esc(S.server.replace(/^https?:\/\//, ""))}</span></button>
          <button class="set-row danger" data-action="clear-history">${icon("trash")}Очистить историю</button>
        </div>

        <div class="group-title">Выход</div>
        <div class="group glass">
          <button class="set-row danger" data-action="logout">${icon("logout")}Выйти из аккаунта</button>
          <button class="set-row danger" data-action="delete-account">${icon("trash")}Удалить аккаунт</button>
        </div>
      </div></div>`;
  }

  // ---------- render loop ----------
  function render() {
    const root = $("#root");
    const active = document.activeElement;
    const focusId = active?.id;
    const sel = focusId && "selectionStart" in active ? [active.selectionStart, active.selectionEnd] : null;
    const scroller = $("#scroller");
    const nearBottom = scroller ? scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight < 120 : true;
    const prevTop = scroller?.scrollTop;

    root.innerHTML = S.token ? renderApp() : renderAuth();

    if (focusId) {
      const el = document.getElementById(focusId);
      if (el) { el.focus(); if (sel) try { el.setSelectionRange(sel[0], sel[1]); } catch {} }
    }
    autosize();
    const sc = $("#scroller");
    if (sc) {
      if (nearBottom || prevTop == null) sc.scrollTop = sc.scrollHeight;
      else sc.scrollTop = prevTop;
    }
  }

  function autosize() {
    const ta = $("#input");
    if (!ta) return;
    ta.style.height = "auto";
    ta.style.height = `${Math.min(ta.scrollHeight, 200)}px`;
  }

  function updateSendButton() {
    const btn = $("#send");
    if (!btn) return;
    const can = !S.streaming && !S.pending.some((p) => p.uploading) && (S.draft.trim() || S.pending.some((p) => p.attachment));
    btn.disabled = !can;
    btn.classList.toggle("filled", Boolean(can));
  }

  // Patch one message in place while it streams (cheaper than a full render).
  let patchQueued = new Set();
  function patchMessage(oldId, newId = oldId) {
    const el = document.querySelector(`[data-mid="${CSS.escape(oldId)}"]`);
    const m = S.messages.find((x) => x.id === newId);
    if (!el || !m) return;
    const sc = $("#scroller");
    const nearBottom = sc ? sc.scrollHeight - sc.scrollTop - sc.clientHeight < 120 : false;
    el.outerHTML = messageHTML(m, m.id === S.messages[S.messages.length - 1]?.id);
    if (sc && nearBottom) sc.scrollTop = sc.scrollHeight;
  }
  function schedulePatch(id) {
    if (patchQueued.has(id)) return;
    patchQueued.add(id);
    requestAnimationFrame(() => { patchQueued.delete(id); patchMessage(id); });
  }

  // ---------- actions: chat ----------
  function newChat(mode = "chat", draft = "") {
    if (S.abort) S.abort.abort();
    Object.assign(S, { view: "chat", chat: null, mode, messages: [], draft, pending: [], error: null, limit: false, sideOpen: false });
    render();
    $("#input")?.focus();
  }

  async function openChat(id) {
    if (S.abort) S.abort.abort();
    const chat = S.chats.find((c) => c.id === id) || { id, title: "Чат", mode: "chat" };
    Object.assign(S, { view: "chat", chat, mode: chat.mode, messages: [], pending: [], error: null, limit: false, loadingChat: true, sideOpen: false });
    render();
    try {
      const res = await api("GET", `/chats/${id}/messages`);
      if (S.chat?.id !== id) return;
      S.chat = res.chat;
      S.messages = res.messages;
      upsertChat(res.chat);
    } catch (e) {
      if (S.chat?.id === id) S.error = e.message;
    } finally {
      S.loadingChat = false;
      render();
    }
  }

  function limitReachedLocally() {
    const a = S.account;
    return a && a.limits.dailyMessages > 0 && a.usage.messagesToday >= a.limits.dailyMessages;
  }

  async function send(textOverride) {
    if (S.streaming) return;
    if (textOverride !== undefined) S.draft = textOverride;
    if (S.pending.some((p) => p.uploading)) return toast("Дождитесь загрузки файлов");
    const text = S.draft.trim();
    const files = S.pending.filter((p) => p.attachment).map((p) => p.attachment);
    if (!text && !files.length) return;
    if (limitReachedLocally()) { S.limit = true; render(); return; }
    if (S.view !== "chat") Object.assign(S, { view: "chat", chat: null, messages: [], mode: S.mode || "chat" });
    S.draft = "";
    S.pending = [];
    S.error = null;
    S.limit = false;
    const now = Date.now();
    const temp = { id: `local-u-${now}`, role: "user", content: text, attachments: files, status: "complete", createdAt: now };
    S.messages.push(temp);
    await runTurn(text, files, temp.id);
  }

  async function retry() {
    if (S.streaming || !S.messages.length) return;
    if (S.messages[S.messages.length - 1].role !== "user") S.messages.pop();
    S.error = null;
    await runTurn(null, [], null);
  }

  async function runTurn(text, files, tempId) {
    let sid = `local-a-${Date.now()}`;
    S.messages.push({ id: sid, role: "assistant", content: "", status: "streaming" });
    S.streaming = true;
    const ctrl = new AbortController();
    S.abort = ctrl;
    render();
    const setStatus = (status) => { const m = S.messages.find((x) => x.id === sid); if (m) m.status = status; };
    try {
      if (!S.chat) {
        const { chat } = await api("POST", "/chats", { mode: S.mode });
        S.chat = chat;
        upsertChat(chat);
      }
      const chatId = S.chat.id;
      const path = text === null ? `/chats/${chatId}/regenerate` : `/chats/${chatId}/messages`;
      const body = text === null ? null : { text, attachmentIds: files.map((f) => f.id) };
      await stream(path, body, (event, data) => {
        if (S.chat?.id !== chatId) return;
        if (event === "user" && tempId) {
          const i = S.messages.findIndex((m) => m.id === tempId);
          if (i >= 0) S.messages[i] = data;
        } else if (event === "assistant") {
          const m = S.messages.find((x) => x.id === sid);
          if (m) m.id = data.id;
          patchMessage(sid, data.id);
          sid = data.id;
        } else if (event === "delta") {
          const m = S.messages.find((x) => x.id === sid);
          if (m) { m.content += data.text; schedulePatch(sid); }
        } else if (event === "error") {
          S.error = data.message;
        } else if (event === "done") {
          const i = S.messages.findIndex((m) => m.id === sid);
          if (i >= 0) S.messages[i] = data.message;
          S.chat = data.chat;
          upsertChat(data.chat);
          bumpUsage(1);
        }
      }, ctrl.signal);
    } catch (e) {
      if (e.name === "AbortError") {
        setStatus("stopped");
      } else if (e.code === "limit_reached") {
        S.limit = true;
        S.messages = S.messages.filter((m) => m.id !== sid && m.id !== tempId);
        if (text) S.draft = text;
      } else {
        setStatus("error");
        S.error = e.message;
      }
    } finally {
      if (S.abort === ctrl) S.abort = null;
      S.streaming = false;
      render();
    }
  }

  async function chatMenu(id) {
    const chat = S.chats.find((c) => c.id === id) || S.chat;
    if (!chat) return;
    const choice = await new Promise((resolve) => {
      modal({
        title: chat.title,
        extra: `<div style="display:grid;gap:8px;margin-bottom:6px">
          <button type="button" class="btn btn-glass" data-pick="rename">${icon("edit", 18)}Переименовать</button>
          <button type="button" class="btn btn-danger" data-pick="delete">${icon("trash", 18)}Удалить чат</button></div>`,
        confirm: "Закрыть",
        cancel: "",
      }).then(() => resolve(null));
      setTimeout(() => {
        document.querySelectorAll("[data-pick]").forEach((b) => b.addEventListener("click", () => {
          b.closest(".modal-back").remove();
          resolve(b.dataset.pick);
        }));
      });
    });
    if (choice === "rename") {
      const v = await modal({ title: "Название чата", fields: [{ name: "title", value: chat.title, placeholder: "Название" }], confirm: "Сохранить" });
      if (!v || !v.title.trim()) return;
      try {
        const { chat: updated } = await api("PATCH", `/chats/${id}`, { title: v.title.trim() });
        upsertChat(updated);
        if (S.chat?.id === id) S.chat = updated;
        render();
      } catch (e) { toast(e.message, true); }
    } else if (choice === "delete") {
      const ok = await modal({ title: "Удалить чат?", text: "Чат удалится на всех ваших устройствах. Это действие нельзя отменить.", confirm: "Удалить", danger: true });
      if (!ok) return;
      try {
        await api("DELETE", `/chats/${id}`);
        setChats(S.chats.filter((c) => c.id !== id));
        if (S.searchResults) S.searchResults = S.searchResults.filter((c) => c.id !== id);
        if (S.chat?.id === id) Object.assign(S, { view: "home", chat: null, messages: [] });
        render();
      } catch (e) { toast(e.message, true); }
    }
  }

  // ---------- attachments ----------
  async function prepareFile(file) {
    if (file.size > 20 * 1048576) throw new ApiError("too_large", "Файл больше 20 МБ");
    if (file.type.startsWith("image/")) {
      try {
        const bmp = await createImageBitmap(file);
        const scale = Math.min(1, 2048 / Math.max(bmp.width, bmp.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(bmp.width * scale));
        canvas.height = Math.max(1, Math.round(bmp.height * scale));
        canvas.getContext("2d").drawImage(bmp, 0, 0, canvas.width, canvas.height);
        const blob = await new Promise((r) => canvas.toBlob(r, "image/jpeg", 0.88));
        if (blob) return { blob, mime: "image/jpeg", name: file.name.replace(/\.[^.]+$/, "") + ".jpg" };
      } catch {}
      if (!/^image\/(jpeg|png|gif|webp)$/.test(file.type)) throw new ApiError("bad_image", "Не удалось открыть изображение");
    }
    let mime = file.type || "application/octet-stream";
    if (!mime.startsWith("image/") && mime !== "application/pdf" && !mime.startsWith("text/") && mime !== "application/json") {
      mime = /\.(pdf)$/i.test(file.name) ? "application/pdf" : "text/plain";
    }
    return { blob: file, mime, name: file.name };
  }

  function addFiles(fileList) {
    for (const file of fileList) {
      if (S.pending.length >= MAX_FILES) { toast(`Можно приложить не больше ${MAX_FILES} файлов`, true); break; }
      const p = { lid: Math.random().toString(36).slice(2), name: file.name, size: file.size, uploading: true, attachment: null, error: null,
        preview: file.type.startsWith("image/") ? URL.createObjectURL(file) : null };
      S.pending.push(p);
      (async () => {
        try {
          const prepared = await prepareFile(file);
          p.attachment = await uploadFile(prepared.blob, prepared.mime, prepared.name);
        } catch (e) {
          p.error = e.message;
        } finally {
          p.uploading = false;
          render();
        }
      })();
    }
    render();
  }

  // ---------- actions: images ----------
  async function generate() {
    const prompt = S.img.prompt.trim();
    if (!prompt) { toast("Опишите, что нарисовать"); return; }
    Object.assign(S.img, { busy: true, error: null, limit: false });
    render();
    try {
      const { image } = await api("POST", "/images", { prompt, style: S.img.style, size: S.img.size });
      S.img.latest = image;
      setImages([image, ...S.images]);
      bumpUsage(0, 1);
    } catch (e) {
      if (e.code === "limit_reached") S.img.limit = true;
      else S.img.error = e.message;
    } finally {
      S.img.busy = false;
      render();
    }
  }

  // ---------- actions: settings ----------
  async function updateProfile(body) {
    try {
      setAccount(await api("PATCH", "/me", body));
      render();
    } catch (e) { toast(e.message, true); }
  }

  // ---------- events ----------
  document.addEventListener("click", async (e) => {
    const el = e.target.closest("[data-action]");
    if (!el || !$("#root").contains(el)) return;
    const { action, id, mode, view } = el.dataset;
    switch (action) {
      case "auth-mode": S.authMode = mode; S.authError = null; render(); break;
      case "server": serverDialog(); break;
      case "side-open": S.sideOpen = true; render(); break;
      case "side-close": S.sideOpen = false; render(); break;
      case "view":
        if (S.abort && view !== "chat") S.abort.abort();
        Object.assign(S, { view, sideOpen: false });
        render();
        break;
      case "new-chat": newChat(mode || "chat"); break;
      case "open-chat":
        if (e.target.closest('[data-action="chat-menu"]')) break;
        openChat(id);
        break;
      case "chat-menu": e.stopPropagation(); chatMenu(id); break;
      case "suggest": send(el.dataset.text); break;
      case "send": send(); break;
      case "stop": S.abort?.abort(); break;
      case "retry": retry(); break;
      case "attach": $("#file-input")?.click(); break;
      case "remove-file": S.pending = S.pending.filter((p) => p.lid !== id); render(); break;
      case "dismiss-limit": S.limit = false; render(); break;
      case "dismiss-error": S.error = null; render(); break;
      case "copy-msg": {
        const m = S.messages.find((x) => x.id === id);
        if (m) { await navigator.clipboard?.writeText(m.content); toast("Скопировано"); }
        break;
      }
      case "copy-code": {
        const code = el.closest(".code")?.querySelector("code")?.textContent || "";
        await navigator.clipboard?.writeText(code);
        el.innerHTML = `${icon("check", 14)}Скопировано`;
        setTimeout(() => { el.innerHTML = `${icon("copy", 14)}Копировать`; }, 1500);
        break;
      }
      case "zoom": lightbox({ id: "file", url: el.dataset.url, prompt: "" }); break;
      case "img-style": S.img.style = id; render(); break;
      case "img-size": S.img.size = id; render(); break;
      case "generate": generate(); break;
      case "open-image": {
        const im = S.images.find((x) => x.id === id) || S.img.latest;
        if (im) lightbox(im, { deletable: true });
        break;
      }
      case "pref-style": updateProfile({ preferences: { responseStyle: id } }); break;
      case "edit-name": {
        const v = await modal({ title: "Ваше имя", fields: [{ name: "name", value: S.account?.user?.name || "", placeholder: "Имя" }], confirm: "Сохранить" });
        if (v && v.name.trim()) updateProfile({ name: v.name.trim() });
        break;
      }
      case "password": {
        const v = await modal({
          title: "Смена пароля",
          fields: [
            { name: "currentPassword", type: "password", placeholder: "Текущий пароль", autocomplete: "current-password" },
            { name: "newPassword", type: "password", placeholder: "Новый пароль (от 8 символов)", autocomplete: "new-password" },
          ],
          confirm: "Сменить",
        });
        if (!v) break;
        if (v.newPassword.length < 8) { toast("Новый пароль должен быть не короче 8 символов", true); break; }
        try {
          await api("POST", "/me/password", v);
          toast("Пароль изменён. Другие устройства вышли из аккаунта.");
        } catch (err) { toast(err.message, true); }
        break;
      }
      case "sync": sync({ quiet: false }); break;
      case "clear-history": {
        const ok = await modal({ title: "Очистить историю?", text: "Все чаты будут удалены на всех ваших устройствах. Это нельзя отменить.", confirm: "Очистить", danger: true });
        if (!ok) break;
        try {
          await api("DELETE", "/chats");
          setChats([]);
          Object.assign(S, { chat: null, messages: [], searchResults: null });
          render();
          toast("История очищена");
        } catch (err) { toast(err.message, true); }
        break;
      }
      case "logout": {
        const ok = await modal({ title: "Выйти из аккаунта?", text: "История останется на сервере и вернётся после входа.", confirm: "Выйти", danger: true });
        if (!ok) break;
        try { await api("POST", "/auth/logout"); } catch {}
        signOutLocally();
        break;
      }
      case "delete-account": {
        const ok = await modal({ title: "Удалить аккаунт?", text: "Все чаты, файлы и изображения будут удалены навсегда. Подписку в App Store или Google Play нужно отменить отдельно.", confirm: "Удалить навсегда", danger: true });
        if (!ok) break;
        try {
          await api("DELETE", "/me");
          signOutLocally();
        } catch (err) { toast(err.message, true); }
        break;
      }
    }
  });

  document.addEventListener("submit", (e) => {
    if (e.target.id === "auth-form") { e.preventDefault(); submitAuth(e.target); }
  });

  let searchTimer;
  document.addEventListener("input", (e) => {
    const t = e.target;
    if (t.id === "input") { S.draft = t.value; autosize(); updateSendButton(); }
    else if (t.id === "img-prompt") S.img.prompt = t.value;
    else if (t.id === "search") {
      S.query = t.value;
      clearTimeout(searchTimer);
      const q = S.query.trim();
      if (!q) { S.searchResults = null; $("#chat-list").innerHTML = chatListHTML(); return; }
      searchTimer = setTimeout(async () => {
        try { S.searchResults = (await api("GET", `/chats?q=${encodeURIComponent(q)}`)).chats; }
        catch { S.searchResults = S.chats.filter((c) => c.title.toLowerCase().includes(q.toLowerCase())); }
        const list = $("#chat-list");
        if (list) list.innerHTML = chatListHTML();
      }, 300);
    }
  });

  document.addEventListener("change", (e) => {
    if (e.target.id === "file-input") { addFiles([...e.target.files]); e.target.value = ""; }
    if (e.target.id === "pref-lang") updateProfile({ preferences: { language: e.target.value } });
  });

  document.addEventListener("keydown", (e) => {
    if (e.target.id === "input" && e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); send(); }
    if (e.key === "Escape" && S.sideOpen) { S.sideOpen = false; render(); }
    if ((e.key === "Enter" || e.key === " ") && e.target.matches?.(".chat-item")) { e.preventDefault(); openChat(e.target.dataset.id); }
  });

  // Paste images and drop files straight into the chat.
  document.addEventListener("paste", (e) => {
    if (!S.token || !(S.view === "chat" || S.view === "home")) return;
    const files = [...(e.clipboardData?.files || [])];
    if (files.length) { e.preventDefault(); addFiles(files); }
  });
  let dragDepth = 0;
  document.addEventListener("dragenter", (e) => {
    if (!S.token || !e.dataTransfer?.types?.includes("Files") || !(S.view === "chat" || S.view === "home")) return;
    dragDepth++;
    if (!$(".drop-hint")) { const h = document.createElement("div"); h.className = "drop-hint"; h.textContent = "Отпустите, чтобы прикрепить"; document.body.appendChild(h); }
  });
  document.addEventListener("dragleave", () => { if (--dragDepth <= 0) { dragDepth = 0; $(".drop-hint")?.remove(); } });
  document.addEventListener("dragover", (e) => { if (e.dataTransfer?.types?.includes("Files")) e.preventDefault(); });
  document.addEventListener("drop", (e) => {
    dragDepth = 0;
    $(".drop-hint")?.remove();
    if (!e.dataTransfer?.files?.length || !S.token) return;
    e.preventDefault();
    addFiles([...e.dataTransfer.files]);
  });

  // Refresh history when the tab comes back to the foreground.
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && S.token && !S.streaming) sync();
  });

  // ---------- boot ----------
  async function boot() {
    // When the page is served by the Aura server itself, use it automatically.
    if (!S.server && /^https?:$/.test(location.protocol)) {
      try {
        const res = await fetch(`${location.origin}/api/health`);
        if (res.ok && (await res.json()).ok) { S.server = location.origin; store.set("server", S.server); }
      } catch {}
    }
    render();
    if (S.token) sync();
  }
  boot();
})();
