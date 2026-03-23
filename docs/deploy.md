# BE Deployment (Docker)

## 1. Build image
```bash
docker build -t gachi-be:latest .
```

## 2. Run with env file
```bash
docker run --env-file .env -p 8080:8080 gachi-be:latest
```

## 3. Health check
- `GET /actuator/health`