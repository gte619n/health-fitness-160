# IMPL-00: Project Bootstrap

## Goal
Scaffold the `health-fitness` monorepo. Three deployable components plus shared
docs. No business logic yet. No feature code. Structure, build files, gitignores,
config placeholders, and CI stubs only.

## Stack decisions (override here, not later)

| Decision | Value |
|---|---|
| Repo layout | Monorepo |
| GCP project | `health-fitness-160` |
| Backend | Java 21 LTS + Spring Boot 3.5.x, Gradle Kotlin DSL, multi-module |
| Backend base package | `com.gte619n.healthfitness` |
| Persistence | Cloud Firestore (native mode), default database only |
| Backend deploy target | Cloud Run, single service `health-fitness-backend` |
| Android language | Kotlin 2.0.x |
| Android UI | Jetpack Compose + Material 3 |
| Android min/target SDK | min 29, target 35 |
| Android applicationId | `com.gte619n.healthfitness` |
| Android phone namespace | `com.gte619n.healthfitness.mobile` |
| Android wear namespace | `com.gte619n.healthfitness.wear` |
| Wear OS | Yes, day one, separate module |
| Web | Next.js 15.x App Router + TypeScript strict + Tailwind v4 |
| Web package manager | pnpm |
| Web deploy target | Cloud Run, single service `health-fitness-web` |
| Node | 22 LTS (pinned via `.nvmrc`) |
| JDK | 21 Temurin for build and runtime |
| iOS | Not yet. No directory. |

See the implementation that landed in this commit for the full directory and
file layout. Acceptance criteria for the bootstrap:

- `git clone` + the three build commands in Phase 2 succeeds on a fresh
  machine with JDK 21, Android SDK 35, and Node 22 installed.
- `./gradlew test` in `backend/` passes (includes `HelloEndpointTest`).
- All component `.gitignore` files present and ignoring expected paths.
- No secrets, no service account keys, no `local.properties` in commit history.
- GCP: APIs enabled, runtime service account created and bound, Artifact
  Registry repo `health-fitness` exists, Firestore default database exists,
  `google-oauth-client-id` and `google-oauth-client-secret` in Secret Manager.
- `curl ${BACKEND_URL}/api/hello` returns the expected JSON payload.
- The deployed web Cloud Run service renders the backend's greeting.
- Phone + Wear debug APKs install and display their Hello screens.

For the full bootstrap spec (file contents, scripts, execution rules) see
the IMPL-00 issue / project tracker. This file is the durable record that
IMPL-00 was the source of the initial scaffold.
