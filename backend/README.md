# health-fitness-backend

Spring Boot 3.5 on Java 21. Multi-module Gradle (Kotlin DSL). Persists to
Cloud Firestore. Deploys to Cloud Run as `health-fitness-backend` in
`us-central1`.

## Run locally
```bash
./gradlew :app:bootRun
```

Liveness: <http://localhost:8080/actuator/health>
Hello: <http://localhost:8080/api/hello>

## Test
```bash
./gradlew test
```

## Build for container
```bash
./gradlew :app:bootJar
docker build -t health-fitness-backend .
```

## Modules
- `app` — boot entrypoint, wires modules, holds `application.yml`
- `api` — REST controllers, request/response DTOs
- `core` — domain models and services
- `persistence` — Firestore repositories
- `integrations` — Google Health API client + webhook receiver
