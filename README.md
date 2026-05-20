# Health & Fitness

A three-component health and fitness platform: a native Android app (phone +
Wear OS), a Next.js web app, and a Spring Boot backend. Backend is the source
of truth and reads/writes Cloud Firestore. Health data flows in from Fitbit
hardware via the Google Health API into the backend, then out to both clients.

See [`docs/architecture.md`](docs/architecture.md) for the full picture and
[`docs/decisions/`](docs/decisions/) for ADRs.

## Quickstart

```bash
# Backend (Spring Boot, Java 21)
cd backend && ./gradlew :app:bootRun

# Web (Next.js 15, Node 22, pnpm)
cd web && pnpm install && pnpm dev

# Android phone debug
cd android && ./gradlew :app:assembleDebug

# Wear OS debug
cd android && ./gradlew :wear:assembleDebug
```

GCP one-time provisioning lives under [`infra/`](infra/). See its README for
the setup sequence.

## License

TBD. See [`LICENSE`](LICENSE).
