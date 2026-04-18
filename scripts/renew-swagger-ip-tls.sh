#!/usr/bin/env bash
set -Eeuo pipefail

read_env_value() {
  local key="$1"
  local raw
  raw="$(sed -n "s/^${key}=//p" .env | tail -n1 || true)"
  printf "%s" "$raw" \
    | tr -d '\r' \
    | sed -E 's/^[[:space:]]+|[[:space:]]+$//g; s/^"//; s/"$//'
}

to_lower() {
  local value="$1"
  printf "%s" "$value" | tr '[:upper:]' '[:lower:]'
}

DEPLOY_PATH_INPUT="${1:-${EC2_DEPLOY_PATH:-/home/ubuntu/GACHI-BE/deploy}}"
EC2_HOST_INPUT="${2:-${EC2_HOST:-}}"
DEPLOY_PATH="$(echo "$DEPLOY_PATH_INPUT" | xargs)"

cd "$DEPLOY_PATH"

if [ ! -f .env ]; then
  echo ".env not found in $DEPLOY_PATH"
  exit 1
fi

SWAGGER_ENABLED="$(to_lower "$(read_env_value SWAGGER_ENABLED)")"
if [ "$SWAGGER_ENABLED" != "true" ]; then
  echo "SWAGGER_ENABLED is not true. Skip certificate renewal."
  exit 0
fi

SWAGGER_TLS_MODE="$(to_lower "$(read_env_value SWAGGER_TLS_MODE)")"
SWAGGER_TLS_MODE="${SWAGGER_TLS_MODE:-letsencrypt_ip}"
if [ "$SWAGGER_TLS_MODE" != "letsencrypt_ip" ]; then
  echo "SWAGGER_TLS_MODE is '$SWAGGER_TLS_MODE'. Skip Let's Encrypt IP renewal."
  exit 0
fi

SWAGGER_TLS_IP="$(read_env_value SWAGGER_TLS_IP)"
SWAGGER_TLS_IP="${SWAGGER_TLS_IP:-$EC2_HOST_INPUT}"
SWAGGER_TLS_IP="$(echo "$SWAGGER_TLS_IP" | xargs)"
if [ -z "$SWAGGER_TLS_IP" ]; then
  echo "SWAGGER_TLS_IP is empty. Set SWAGGER_TLS_IP in .env or EC2_HOST secret."
  exit 1
fi

CERTBOT_EMAIL_VALUE="$(read_env_value CERTBOT_EMAIL)"

NEEDS_BOOTSTRAP_CERT="false"
mkdir -p ./secrets
if [ ! -s ./secrets/swagger_tls.crt ] || [ ! -s ./secrets/swagger_tls.key ]; then
  NEEDS_BOOTSTRAP_CERT="true"
else
  CERT_SUBJECT="$(openssl x509 -in ./secrets/swagger_tls.crt -noout -subject -nameopt RFC2253 2>/dev/null | sed 's/^subject=//' || true)"
  CERT_ISSUER="$(openssl x509 -in ./secrets/swagger_tls.crt -noout -issuer -nameopt RFC2253 2>/dev/null | sed 's/^issuer=//' || true)"
  CERT_NOT_AFTER_RAW="$(openssl x509 -in ./secrets/swagger_tls.crt -noout -enddate 2>/dev/null | sed 's/^notAfter=//' || true)"
  CERT_NOT_AFTER_EPOCH="$(date -d "$CERT_NOT_AFTER_RAW" +%s 2>/dev/null || true)"
  THRESHOLD_EPOCH="$(( $(date +%s) + 7 * 24 * 60 * 60 ))"

  if [ -z "$CERT_SUBJECT" ] || [ -z "$CERT_ISSUER" ] || [ -z "$CERT_NOT_AFTER_EPOCH" ]; then
    NEEDS_BOOTSTRAP_CERT="true"
  elif [ "$CERT_SUBJECT" = "$CERT_ISSUER" ] && [ "$CERT_NOT_AFTER_EPOCH" -le "$THRESHOLD_EPOCH" ]; then
    NEEDS_BOOTSTRAP_CERT="true"
  fi
fi

if [ "$NEEDS_BOOTSTRAP_CERT" = "true" ]; then
  echo "[renew] Bootstrap temporary self-signed certificate for nginx startup"
  openssl req -x509 -nodes -newkey rsa:2048 -days 30 \
    -keyout ./secrets/swagger_tls.key \
    -out ./secrets/swagger_tls.crt \
    -subj "/CN=${SWAGGER_TLS_IP}"
  chmod 644 ./secrets/swagger_tls.crt
  chmod 600 ./secrets/swagger_tls.key
fi

echo "[renew] Ensure nginx is serving ACME challenge endpoint"
docker compose --env-file .env up -d --no-deps nginx
docker compose --env-file .env --profile tls rm -f certbot >/dev/null 2>&1 || true

echo "[renew] Request/renew Let's Encrypt IP certificate"
CERTBOT_EXIT_CODE=0
if [ -n "${CERTBOT_EMAIL_VALUE:-}" ]; then
  set +e
  echo "[renew][debug] running certbot certonly with email mode"
  docker compose --env-file .env --profile tls run --rm --entrypoint certbot certbot certonly --non-interactive --agree-tos --keep-until-expiring --preferred-profile shortlived --webroot -w /var/www/certbot --cert-name swagger-ip --ip-address "$SWAGGER_TLS_IP" --email "$CERTBOT_EMAIL_VALUE" -v
  CERTBOT_EXIT_CODE=$?
  set -e
else
  echo "::warning::CERTBOT_EMAIL is empty. Certificate account will be registered without email."
  set +e
  echo "[renew][debug] running certbot certonly without email mode"
  docker compose --env-file .env --profile tls run --rm --entrypoint certbot certbot certonly --non-interactive --agree-tos --keep-until-expiring --preferred-profile shortlived --webroot -w /var/www/certbot --cert-name swagger-ip --ip-address "$SWAGGER_TLS_IP" --register-unsafely-without-email -v
  CERTBOT_EXIT_CODE=$?
  set -e
fi
echo "[renew][debug] certbot certonly exit code: $CERTBOT_EXIT_CODE"

if [ "$CERTBOT_EXIT_CODE" -ne 0 ]; then
  echo "[renew][error] certbot certonly failed (exit=$CERTBOT_EXIT_CODE)."
  docker compose --env-file .env logs --tail=120 nginx || true
  exit "$CERTBOT_EXIT_CODE"
fi

echo "[renew] Sync renewed certificate to nginx secret path"
docker compose --env-file .env --profile tls run --rm --entrypoint /bin/sh certbot -c \
  "set -eu; \
  cp /etc/letsencrypt/live/swagger-ip/fullchain.pem /workspace/secrets/swagger_tls.crt; \
  cp /etc/letsencrypt/live/swagger-ip/privkey.pem /workspace/secrets/swagger_tls.key; \
  chmod 644 /workspace/secrets/swagger_tls.crt; \
  chmod 600 /workspace/secrets/swagger_tls.key"

echo "[renew] Reload nginx to apply new certificate (zero-downtime)"
if ! docker compose --env-file .env exec -T nginx nginx -s reload; then
  echo "[renew] Reload failed, falling back to recreate"
  docker compose --env-file .env up -d --force-recreate --no-deps nginx
fi

echo "[renew] Print certificate expiration"
openssl x509 -in ./secrets/swagger_tls.crt -noout -subject -issuer -dates
