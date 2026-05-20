# CLAUDE.md — backend

- Multi-module Gradle (Kotlin DSL build files, Java source).
- Java 21 LTS, virtual threads enabled by default. Don't add
  `spring-boot-starter-webflux` without a real reason — reactive is heavier
  to read and maintain than synchronous-on-virtual-threads for this app.
- **No Lombok.** Explicit code only. Use Java records for DTOs.
- **No JPA, no SQL.** Cloud Firestore is the source of truth.
- Run locally: `./gradlew :app:bootRun`
- Test: `./gradlew test`

## Module layering
```
app  →  api  →  core
       persistence  →  core
       integrations →  core
```
- `app` is the only module with the Spring Boot plugin.
- `core` is a pure-Java library module. Don't drag Spring Web into it.
- `api` holds controllers and DTOs.
- `persistence` holds Firestore repository implementations.
- `integrations` holds Google Health API client + webhook receiver.

## Conventions
- DTOs are records.
- Controllers depend on services (in `core`), never on repositories directly.
- Configuration via `application.yml` and env vars. No `application-{profile}.yml`
  beyond `application-test.yml` for tests.
