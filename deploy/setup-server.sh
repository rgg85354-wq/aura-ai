#!/usr/bin/env bash
# Installs (or updates) the Aura AI server on an Ubuntu/Debian machine:
# Oracle Cloud Always Free, Google Cloud e2-micro or any VPS.
#
#   curl -fsSL https://raw.githubusercontent.com/rgg85354-wq/aura-ai/main/deploy/setup-server.sh | sudo bash
#
# What it does: installs Node.js 24 and Caddy, puts the server in /opt/aura, keeps data in
# /var/lib/aura, runs it as a systemd service that restarts by itself, and serves it over
# HTTPS with a free certificate on <your-ip>.sslip.io (or AURA_DOMAIN if you have a domain).
# Run it again later to update to the latest code; keys and data are kept.
set -euo pipefail

REPO="${AURA_REPO:-https://github.com/rgg85354-wq/aura-ai.git}"
APP_DIR=/opt/aura
DATA_DIR=/var/lib/aura/data
ENV_FILE=/etc/aura/aura.env
PORT=8787

say() { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "Запустите через sudo: curl -fsSL … | sudo bash" >&2
  exit 1
fi

# Reads a value from the terminal even when the script itself arrives through a pipe.
ask() {
  local var="$1" prompt="$2" secret="${3:-}" value="${!1:-}"
  # Only prompt when a terminal is really available (not in CI or without a tty).
  if [ -z "$value" ] && { : </dev/tty; } 2>/dev/null; then
    if [ "$secret" = secret ]; then
      read -rsp "$prompt: " value </dev/tty || true
      { echo >/dev/tty; } 2>/dev/null || true
    else
      read -rp "$prompt: " value </dev/tty || true
    fi
  fi
  printf -v "$var" '%s' "$value"
}

say "Устанавливаю системные пакеты"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl git gnupg debian-keyring debian-archive-keyring apt-transport-https

# Small machines (1 GB) need swap for npm.
if [ "$(free -m | awk '/^Mem:/{print $2}')" -lt 2000 ] && [ -z "$(swapon --show)" ]; then
  say "Добавляю 1 ГБ подкачки"
  fallocate -l 1G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=1024
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
fi

if ! command -v node >/dev/null || [ "$(node -p 'process.versions.node.split(".")[0]')" -lt 22 ]; then
  say "Устанавливаю Node.js 24"
  curl -fsSL https://deb.nodesource.com/setup_24.x | bash -
  apt-get install -y nodejs
fi

if ! command -v caddy >/dev/null; then
  say "Устанавливаю Caddy (HTTPS)"
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' >/etc/apt/sources.list.d/caddy-stable.list
  chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y
  apt-get install -y caddy
fi

say "Загружаю код Aura"
id aura >/dev/null 2>&1 || useradd --system --create-home --home-dir /var/lib/aura --shell /usr/sbin/nologin aura
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" fetch --depth 1 origin main
  git -C "$APP_DIR" reset --hard origin/main
else
  git clone --depth 1 "$REPO" "$APP_DIR"
fi
(cd "$APP_DIR/server" && npm ci --omit=dev --no-audit --no-fund)
mkdir -p "$DATA_DIR"
chown -R aura:aura /var/lib/aura

if [ ! -f "$ENV_FILE" ]; then
  say "Настройка ключей (их можно поменять позже в $ENV_FILE)"
  ask ANTHROPIC_API_KEY "Ключ Claude ANTHROPIC_API_KEY (ввод скрыт)" secret
  ask IMAGE_API_KEY "Ключ для картинок IMAGE_API_KEY (Enter — пропустить)" secret
  mkdir -p /etc/aura
  umask 027
  cat >"$ENV_FILE" <<EOF
ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
AURA_MODEL=claude-opus-5-5
IMAGE_API_KEY=${IMAGE_API_KEY}
IMAGE_API_BASE=https://api.openai.com/v1
IMAGE_MODEL=gpt-image-1
FREE_DAILY_MESSAGES=30
FREE_DAILY_IMAGES=5
PRO_DAILY_MESSAGES=1000
PRO_DAILY_IMAGES=100
PRO_TEST_MODE=false
APPLE_BUNDLE_ID=com.aura.ai
APPLE_ENVIRONMENT=Sandbox
GOOGLE_PACKAGE_NAME=com.aura.ai
EOF
  umask 022
  chown root:aura "$ENV_FILE"
  chmod 640 "$ENV_FILE"
fi

say "Создаю службу aura"
NODE_BIN="$(command -v node)"
cat >/etc/systemd/system/aura.service <<EOF
[Unit]
Description=Aura AI server
After=network-online.target
Wants=network-online.target

[Service]
User=aura
Group=aura
WorkingDirectory=$APP_DIR/server
EnvironmentFile=$ENV_FILE
Environment=HOST=127.0.0.1 PORT=$PORT DATA_DIR=$DATA_DIR NODE_ENV=production
ExecStart=$NODE_BIN src/index.js
Restart=always
RestartSec=3
NoNewPrivileges=true
ProtectSystem=full
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable aura >/dev/null
systemctl restart aura

# Domain: your own (AURA_DOMAIN) or a free name that points at this machine's IP.
DOMAIN="${AURA_DOMAIN:-}"
if [ -z "$DOMAIN" ]; then
  IP="$(curl -fsS4 --max-time 10 https://api.ipify.org || curl -fsS4 --max-time 10 https://ifconfig.me)"
  DOMAIN="$(echo "$IP" | tr . -).sslip.io"
fi

say "Настраиваю HTTPS для $DOMAIN"
cat >/etc/caddy/Caddyfile <<EOF
$DOMAIN {
	reverse_proxy 127.0.0.1:$PORT {
		# Send streamed AI replies to the phone immediately.
		flush_interval -1
	}
}
EOF
systemctl enable caddy >/dev/null
systemctl restart caddy

# Oracle Cloud Ubuntu images block everything except SSH in iptables.
if command -v iptables >/dev/null && iptables -S INPUT 2>/dev/null | grep -q -- '-j REJECT'; then
  for p in 80 443; do
    iptables -C INPUT -p tcp --dport "$p" -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p tcp --dport "$p" -j ACCEPT
  done
  command -v netfilter-persistent >/dev/null && netfilter-persistent save >/dev/null 2>&1 || true
fi
if command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q 'Status: active'; then
  ufw allow 80/tcp >/dev/null
  ufw allow 443/tcp >/dev/null
fi

say "Проверяю"
ok=0
for _ in $(seq 1 20); do
  if curl -fsS "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then ok=1; break; fi
  sleep 1
done
if [ "$ok" -ne 1 ]; then
  echo "Сервер Aura не запустился. Журнал: journalctl -u aura -n 50" >&2
  exit 1
fi

https_ok=0
for _ in $(seq 1 "${AURA_HTTPS_TRIES:-30}"); do
  if curl -fsS --max-time 5 "https://$DOMAIN/api/health" >/dev/null 2>&1; then https_ok=1; break; fi
  sleep 2
done

echo
echo "Готово. Сервер Aura работает и перезапускается сам."
echo "Адрес для приложения:  https://$DOMAIN"
if [ "$https_ok" -ne 1 ]; then
  echo
  echo "HTTPS пока не отвечает. Откройте порты 80 и 443 в облачной консоли:"
  echo "  Oracle: Networking → Virtual Cloud Networks → ваша сеть → Security Lists → Add Ingress Rules (0.0.0.0/0, TCP 80 и 443)"
  echo "  Google: VPC network → Firewall, или при создании VM отметьте Allow HTTP и Allow HTTPS"
  echo "После этого сертификат выпустится сам в течение минуты. Журнал: journalctl -u caddy -n 50"
fi
echo
echo "Полезные команды:"
echo "  sudo systemctl status aura      — состояние"
echo "  sudo journalctl -u aura -f      — журнал"
echo "  sudo nano $ENV_FILE  — ключи, затем: sudo systemctl restart aura"
echo "  повторный запуск этого скрипта — обновление до свежей версии"
