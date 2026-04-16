# HTTPS Setup (when domain is ready)

## 1) Keep HTTP stack running
```bash
docker compose --env-file .env up -d
```

## 2) Issue certificate (replace values)
```bash
docker compose --env-file .env run --rm --no-deps certbot certonly \
  --webroot -w /var/www/certbot \
  -d your-domain.com -d www.your-domain.com \
  --email your-email@example.com --agree-tos --no-eff-email
```

## 3) Switch nginx to HTTPS config
```bash
cp nginx/nginx.https.template.conf nginx/nginx.https.conf
sed -i "s/__DOMAIN__/your-domain.com/g" nginx/nginx.https.conf
cp nginx/nginx.https.conf nginx/nginx.conf
```

## 4) Reload nginx
```bash
docker compose --env-file .env up -d nginx
```

## 5) Start certbot auto-renew loop
```bash
docker compose --env-file .env --profile tls up -d certbot
```

## 6) Validate
- https://your-domain.com/api/v1/users/me
- https://your-domain.com/ai/health