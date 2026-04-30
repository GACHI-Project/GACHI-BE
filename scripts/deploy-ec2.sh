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

to_abs_path() {
  local rel_or_abs="$1"
  local candidate
  if [ "${rel_or_abs#/}" = "$rel_or_abs" ]; then
    candidate="$DEPLOY_PATH/${rel_or_abs#./}"
  else
    candidate="$rel_or_abs"
  fi
  realpath -m "$candidate"
}

DEPLOY_PATH_INPUT="${1:-${EC2_DEPLOY_PATH:-/home/ubuntu/GACHI-BE/deploy}}"
EC2_HOST_INPUT="${2:-${EC2_HOST:-}}"
DOCKERHUB_USERNAME_INPUT="${DOCKERHUB_USERNAME:-${3:-}}"
DOCKERHUB_TOKEN_INPUT="${DOCKERHUB_TOKEN:-}"

DEPLOY_PATH="$(echo "$DEPLOY_PATH_INPUT" | xargs)"
EC2_HOST_INPUT="$(echo "$EC2_HOST_INPUT" | xargs)"
DOCKERHUB_USERNAME_INPUT="${DOCKERHUB_USERNAME_INPUT%$'\r'}"
DOCKERHUB_TOKEN_INPUT="${DOCKERHUB_TOKEN_INPUT%$'\r'}"

if [ -z "$DOCKERHUB_USERNAME_INPUT" ]; then
  echo "DOCKERHUB_USERNAME is required."
  exit 1
fi

echo "[1/7] Move to deploy path: $DEPLOY_PATH"
if [ ! -d "$DEPLOY_PATH" ]; then
  echo "Deploy path not found: $DEPLOY_PATH"
  exit 1
fi
cd "$DEPLOY_PATH"
echo "[debug] host=$(hostname) user=$(whoami) pwd=$(pwd)"
ls -al

echo "[2/7] Validate server-side .env preservation"
if [ ! -f .env ]; then
  echo ".env not found in $DEPLOY_PATH. Create .env on EC2 before deploy."
  exit 1
fi

JWT_SECRET_VALUE="$(read_env_value JWT_SECRET)"
JWT_SECRET_LENGTH=${#JWT_SECRET_VALUE}
if [ "$JWT_SECRET_LENGTH" -lt 32 ]; then
  echo "JWT_SECRET must be configured with at least 32 characters. current length=$JWT_SECRET_LENGTH"
  exit 1
fi

echo "[3/7] Pin latest backend image tag in .env"
if grep -q "^BACKEND_IMAGE=" .env; then
  sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${DOCKERHUB_USERNAME_INPUT}/gachi-be:latest|" .env
else
  echo "BACKEND_IMAGE=${DOCKERHUB_USERNAME_INPUT}/gachi-be:latest" >> .env
fi

echo "[4/7] Validate SMTP/Swagger secrets and TLS files"
SECRET_FILE_REL="$(read_env_value SPRING_MAIL_PASSWORD_SECRET_FILE)"
SECRET_FILE_REL="${SECRET_FILE_REL:-./secrets/spring_mail_password.example.txt}"
SECRET_FILE_PATH="$(to_abs_path "$SECRET_FILE_REL")"
echo "[debug] secret file rel path: $SECRET_FILE_REL"
echo "[debug] secret file absolute path: $SECRET_FILE_PATH"
if [ -e "$SECRET_FILE_PATH" ]; then
  if [ ! -r "$SECRET_FILE_PATH" ]; then
    echo "SMTP secret file exists but is not readable: $SECRET_FILE_PATH"
    exit 1
  fi
  if [ ! -s "$SECRET_FILE_PATH" ]; then
    echo "SMTP secret file is empty: $SECRET_FILE_PATH (fallback env SPRING_MAIL_PASSWORD will be used if set)"
  fi
else
  echo "SMTP secret file not found: $SECRET_FILE_PATH (fallback env SPRING_MAIL_PASSWORD will be used if set)"
fi

SWAGGER_ENABLED_VALUE="$(to_lower "$(read_env_value SWAGGER_ENABLED)")"
echo "[debug] SWAGGER_ENABLED normalized value: ${SWAGGER_ENABLED_VALUE:-<empty>}"
if [ "$SWAGGER_ENABLED_VALUE" = "true" ]; then
  SWAGGER_AUTH_FILE_REL="$(read_env_value SWAGGER_BASIC_AUTH_FILE)"
  SWAGGER_AUTH_FILE_REL="${SWAGGER_AUTH_FILE_REL:-./secrets/swagger_htpasswd.txt}"
  SWAGGER_AUTH_FILE_PATH="$(to_abs_path "$SWAGGER_AUTH_FILE_REL")"
  EXPECTED_SWAGGER_AUTH_FILE_PATH="$(realpath -m "$DEPLOY_PATH/secrets/swagger_htpasswd.txt")"
  if [ "$SWAGGER_AUTH_FILE_PATH" != "$EXPECTED_SWAGGER_AUTH_FILE_PATH" ]; then
    echo "SWAGGER_BASIC_AUTH_FILE must point to ./secrets/swagger_htpasswd.txt for nginx auth_basic_user_file compatibility."
    exit 1
  fi
  echo "[debug] swagger auth file absolute path: $SWAGGER_AUTH_FILE_PATH"
  if [ ! -s "$SWAGGER_AUTH_FILE_PATH" ]; then
    echo "Swagger auth file missing or empty: $SWAGGER_AUTH_FILE_PATH"
    exit 1
  fi
  if [ ! -r "$SWAGGER_AUTH_FILE_PATH" ]; then
    echo "Swagger auth file is not readable: $SWAGGER_AUTH_FILE_PATH"
    exit 1
  fi

  TLS_CERT_FILE_REL="$(read_env_value SWAGGER_TLS_CERT_FILE)"
  TLS_KEY_FILE_REL="$(read_env_value SWAGGER_TLS_KEY_FILE)"
  TLS_CERT_FILE_REL="${TLS_CERT_FILE_REL:-./secrets/swagger_tls.crt}"
  TLS_KEY_FILE_REL="${TLS_KEY_FILE_REL:-./secrets/swagger_tls.key}"
  TLS_CERT_FILE_PATH="$(to_abs_path "$TLS_CERT_FILE_REL")"
  TLS_KEY_FILE_PATH="$(to_abs_path "$TLS_KEY_FILE_REL")"
  EXPECTED_TLS_CERT_FILE_PATH="$(realpath -m "$DEPLOY_PATH/secrets/swagger_tls.crt")"
  EXPECTED_TLS_KEY_FILE_PATH="$(realpath -m "$DEPLOY_PATH/secrets/swagger_tls.key")"
  if [ "$TLS_CERT_FILE_PATH" != "$EXPECTED_TLS_CERT_FILE_PATH" ] || [ "$TLS_KEY_FILE_PATH" != "$EXPECTED_TLS_KEY_FILE_PATH" ]; then
    echo "SWAGGER_TLS_CERT_FILE and SWAGGER_TLS_KEY_FILE must point to ./secrets/swagger_tls.crt and ./secrets/swagger_tls.key."
    exit 1
  fi

  SWAGGER_TLS_MODE="$(to_lower "$(read_env_value SWAGGER_TLS_MODE)")"
  SWAGGER_TLS_MODE="${SWAGGER_TLS_MODE:-letsencrypt_ip}"
  SWAGGER_TLS_COMMON_NAME="$(read_env_value SWAGGER_TLS_COMMON_NAME)"
  if [ "$SWAGGER_TLS_MODE" = "letsencrypt_ip" ]; then
    SWAGGER_TLS_IP="$(read_env_value SWAGGER_TLS_IP)"
    SWAGGER_TLS_IP="${SWAGGER_TLS_IP:-$EC2_HOST_INPUT}"
    SWAGGER_TLS_IP="$(echo "$SWAGGER_TLS_IP" | xargs)"
    if [ -z "$SWAGGER_TLS_IP" ]; then
      echo "SWAGGER_TLS_IP is empty. Set SWAGGER_TLS_IP in .env or EC2_HOST secret."
      exit 1
    fi

    CERTBOT_EMAIL_VALUE="$(read_env_value CERTBOT_EMAIL)"

    if [ ! -s "$TLS_CERT_FILE_PATH" ] || [ ! -s "$TLS_KEY_FILE_PATH" ]; then
      BOOTSTRAP_CN="${SWAGGER_TLS_COMMON_NAME:-$SWAGGER_TLS_IP}"
      echo "::warning::Bootstrap self-signed TLS cert is generated to make nginx available for ACME challenge."
      mkdir -p "$(dirname "$TLS_CERT_FILE_PATH")"
      mkdir -p "$(dirname "$TLS_KEY_FILE_PATH")"
      openssl req -x509 -nodes -newkey rsa:2048 -days 7 \
        -keyout "$TLS_KEY_FILE_PATH" \
        -out "$TLS_CERT_FILE_PATH" \
        -subj "/CN=$BOOTSTRAP_CN"
      chmod 644 "$TLS_CERT_FILE_PATH"
      chmod 600 "$TLS_KEY_FILE_PATH"
    fi

    echo "[debug] issue/renew Let's Encrypt IP cert for $SWAGGER_TLS_IP"
    set +e
    docker compose --env-file .env up -d --no-deps nginx
    NGINX_UP_EXIT_CODE=$?
    set -e
    echo "[debug] nginx up exit code: $NGINX_UP_EXIT_CODE"
    if [ "$NGINX_UP_EXIT_CODE" -ne 0 ]; then
      echo "[error] failed to ensure nginx is running for ACME challenge."
      docker compose --env-file .env logs --tail=120 nginx || true
      exit "$NGINX_UP_EXIT_CODE"
    fi

    set +e
    docker compose --env-file .env --profile tls rm -f certbot >/dev/null 2>&1
    CERTBOT_RM_EXIT_CODE=$?
    set -e
    echo "[debug] certbot rm exit code: $CERTBOT_RM_EXIT_CODE"

    CERTBOT_EXIT_CODE=0
    if [ -n "${CERTBOT_EMAIL_VALUE:-}" ]; then
      set +e
      echo "[debug] running certbot certonly with email mode"
      docker compose --env-file .env --profile tls run --rm --entrypoint certbot certbot certonly --non-interactive --agree-tos --keep-until-expiring --preferred-profile shortlived --webroot -w /var/www/certbot --cert-name swagger-ip --ip-address "$SWAGGER_TLS_IP" --email "$CERTBOT_EMAIL_VALUE" -v
      CERTBOT_EXIT_CODE=$?
      set -e
    else
      echo "::warning::CERTBOT_EMAIL is empty. Certificate account will be registered without email."
      set +e
      echo "[debug] running certbot certonly without email mode"
      docker compose --env-file .env --profile tls run --rm --entrypoint certbot certbot certonly --non-interactive --agree-tos --keep-until-expiring --preferred-profile shortlived --webroot -w /var/www/certbot --cert-name swagger-ip --ip-address "$SWAGGER_TLS_IP" --register-unsafely-without-email -v
      CERTBOT_EXIT_CODE=$?
      set -e
    fi
    echo "[debug] certbot certonly exit code: $CERTBOT_EXIT_CODE"

    if [ "$CERTBOT_EXIT_CODE" -ne 0 ]; then
      echo "[error] certbot certonly failed (exit=$CERTBOT_EXIT_CODE)."
      docker compose --env-file .env logs --tail=120 nginx || true
      exit "$CERTBOT_EXIT_CODE"
    fi

    docker compose --env-file .env --profile tls run --rm --entrypoint /bin/sh certbot -c \
      "set -eu; \
      cp /etc/letsencrypt/live/swagger-ip/fullchain.pem /workspace/secrets/swagger_tls.crt; \
      cp /etc/letsencrypt/live/swagger-ip/privkey.pem /workspace/secrets/swagger_tls.key; \
      chmod 644 /workspace/secrets/swagger_tls.crt; \
      chmod 600 /workspace/secrets/swagger_tls.key"
  fi

  if [ ! -s "$TLS_CERT_FILE_PATH" ] || [ ! -s "$TLS_KEY_FILE_PATH" ]; then
    echo "::warning::TLS cert/key missing for Swagger HTTPS. Generating self-signed cert. This is NOT recommended for production!"
    mkdir -p "$(dirname "$TLS_CERT_FILE_PATH")"
    mkdir -p "$(dirname "$TLS_KEY_FILE_PATH")"
    openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
      -keyout "$TLS_KEY_FILE_PATH" \
      -out "$TLS_CERT_FILE_PATH" \
      -subj "/CN=${SWAGGER_TLS_IP:-$(hostname)}"
    chmod 644 "$TLS_CERT_FILE_PATH"
    chmod 600 "$TLS_KEY_FILE_PATH"
  fi

  if ! docker compose --env-file .env run --rm --no-deps --entrypoint /bin/sh nginx -c "test -r /etc/nginx/secrets/swagger_tls.crt && test -r /etc/nginx/secrets/swagger_tls.key"; then
    echo "TLS cert/key are not readable from nginx container path."
    ls -l "$TLS_CERT_FILE_PATH" "$TLS_KEY_FILE_PATH" || true
    exit 1
  fi
else
  echo "[debug] SWAGGER_ENABLED is not true. Swagger auth/tls validation skipped."
fi

echo "[5/7] Pull latest backend image"
if [ -n "${DOCKERHUB_TOKEN_INPUT:-}" ]; then
  echo "${DOCKERHUB_TOKEN_INPUT}" | docker login -u "${DOCKERHUB_USERNAME_INPUT}" --password-stdin
else
  echo "::warning::DOCKERHUB_TOKEN is empty on instance. Skip docker login and try pull with current daemon credentials."
fi
docker compose --env-file .env pull backend

echo "[6/7] Recreate services with synced compose/nginx settings"
docker compose --env-file .env up -d --remove-orphans backend
BACKEND_HEALTH="starting"
for _ in $(seq 1 36); do
  BACKEND_CID="$(docker compose --env-file .env ps -q backend)"
  if [ -z "$BACKEND_CID" ]; then
    echo "backend container not found via compose ps."
    docker compose --env-file .env ps
    exit 1
  fi
  BACKEND_HEALTH="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$BACKEND_CID" 2>/dev/null || echo "missing")"
  echo "[debug] backend health: $BACKEND_HEALTH"
  if [ "$BACKEND_HEALTH" = "healthy" ]; then
    break
  fi
  sleep 5
done
if [ "$BACKEND_HEALTH" != "healthy" ]; then
  echo "backend health check timed out. Check backend logs."
  docker compose --env-file .env logs --tail=120 backend || true
  exit 1
fi
docker compose --env-file .env up -d --remove-orphans --force-recreate --no-deps nginx

echo "[7/7] Print deploy status"
docker image prune -f
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=80 backend nginx || true
