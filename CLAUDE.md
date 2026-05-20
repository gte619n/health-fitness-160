# CLAUDE.md — project-wide guidance

## Architecture
- **Three deployable components**, all in this monorepo:
  - `backend/` — Spring Boot 3.5 (Java 21), Gradle Kotlin DSL, multi-module
  - `android/` — Native Android (Kotlin 2.0, Jetpack Compose, Material 3),
    multi-module, includes a Wear OS app
  - `web/` — Next.js 15 App Router (TypeScript strict, Tailwind v4, pnpm)
- **Shared state**: backend owns Cloud Firestore (native mode). Both clients
  read from the backend, not directly from Firestore.
- **Hosting**: backend and web both deploy to Cloud Run in `us-central1`.

## Where to find things
- Architecture overview: [`docs/architecture.md`](docs/architecture.md)
- Architecture Decision Records: [`docs/decisions/`](docs/decisions/)
- Implementation specs (IMPL-XX): [`docs/specs/`](docs/specs/)
- Per-component guidance: `backend/CLAUDE.md`, `android/CLAUDE.md`,
  `web/CLAUDE.md` override this file inside their respective directories.

## Conventions
- Conventional Commits (`feat:`, `fix:`, `chore(scope):`, etc.)
- Trunk-based dev on `main`. Feature branches named `feat/IMPL-XX-slug`.
- One commit per logical change. Don't squash unrelated work.

## Never
- Commit secrets, service account JSON keys, OAuth client secrets, or
  `local.properties`.
- Edit files in `.github/workflows/` without calling it out in the PR description.

## Tools
- GCP project: `health-fitness-160`
- Region: `us-central1`
- The Google Health API Parity Tool context file goes in `AGENTS.md` at the
  repo root.
