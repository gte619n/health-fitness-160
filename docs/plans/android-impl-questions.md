# Android IMPL — Open Questions for Evan

This file collects questions, blockers, and assumptions surfaced by the
unattended overnight implementation of the IMPL-AND-00 .. IMPL-AND-06 specs
on the `feature/android_impl` branch.

Each implementing sub-agent appends to the relevant stage section below.
Review tomorrow morning.

---

## How to read this file

- **Question** — something that needs a decision before the work can be
  considered fully done. The agent made a best-guess assumption and the
  text of the assumption is recorded so you can confirm or reverse it.
- **Blocker** — something the agent could not work around. The work was
  partially landed or skipped. Marked with **BLOCKER** prefix.
- **Note** — useful context surfaced during implementation that doesn't
  need a decision (deferred follow-ups, surprising discoveries, etc).

When an item is resolved, replace its **Status:** line with `Resolved
{date} — {short note}`.

---

## Stage 00 — Foundations

### Note — `android/cloudbuild.yaml` updated for flavored build paths

**Status:** open — please review.

The IMPL-AND-00 spec adds `dev`/`staging`/`prod` flavors to `app/`, which
moves the release APK out from
`app/build/outputs/apk/release/app-release.apk` to
`app/build/outputs/apk/prod/release/app-prod-release.apk`, and replaces
the `:app:assembleRelease` umbrella task (which now assembles every
flavor's release variant) with `:app:assembleProdRelease` (only prod).

Updated `android/cloudbuild.yaml` so Cloud Build:
  - runs `:app:assembleProdRelease` instead of `:app:assembleRelease`;
  - pulls the APK from the flavored path for the Firebase App
    Distribution step and the artifacts upload.

If you'd rather Cloud Build distribute the `staging` variant during pre-
release weeks (so internal testers exercise the staging backend), flip
the task and path to `:app:assembleStagingRelease` /
`app-staging-release.apk`. No other CI files changed.

### Note — generated local `android/debug.keystore`

**Status:** informational, no action needed.

The repo gitignores `*.keystore` and ships no local debug keystore, so a
clean checkout doesn't have one. The implementing agent ran
`keytool -genkeypair ... -alias androiddebugkey -storepass android` to
unblock `:app:assembleDevDebug` (which references
`../debug.keystore` in the IMPL-02 signing config). The keystore stays
local and gitignored. If you want every developer to start from the same
SHA-1, either:
  - check the dev keystore into the repo (loosen `.gitignore`), or
  - document the `keytool` invocation in `android/README.md`.

### Note — `feature-*` modules now activate Hilt + KSP

**Status:** informational.

Per spec section "Feature modules": each empty placeholder module gains
Hilt + KSP plugins and an `implementation(project(":core-network"))`
line even though the modules contain no Kotlin code yet. This stabilises
Hilt's aggregating annotation-processor module list now so the first
real `@HiltViewModel` per feature doesn't re-trigger a full graph rescan.

### Note — `DispatcherModule` provider function names

**Status:** informational.

The spec sample uses provider names `fun io()`, `fun default()`,
`fun main()`. Kotlin allows `default` as an identifier in a function
position but Hilt's KSP processor errors with
`not a valid name: default`. Renamed to
`provideIoDispatcher` / `provideDefaultDispatcher` /
`provideMainDispatcher` — same `@Qualifier` annotations, same return
types. No behaviour change.

### Note — `AuthUiState.toLegacyAuthState()` shim

**Status:** informational, deferred to IMPL-AND-02.

Per spec, this IMPL keeps the existing `SignInScreen` untouched. The
screen still consumes the IMPL-02 `AuthState`. `AuthViewModel` exposes
`AuthUiState`; a small `toLegacyAuthState()` extension bridges the two
inside `app/`. The extension passes `idToken = ""` because
`SignInScreen` doesn't display it. Drop the extension once IMPL-AND-02
lifts the screen's signature to `AuthUiState`.

### Note — `:wear` does not depend on `:core-network` yet

**Status:** informational.

The wear app gains Hilt + the dev/staging/prod flavor scaffolding and
the `BACKEND_BASE_URL` BuildConfig field, but it does *not* take an
`implementation(project(":core-network"))` line in this IMPL. Wear has
nothing to call yet; the network module drops in for AND-08 when the
first wear-side surface needs the backend.

---

## Stage 01 — Dashboard live data

### Note — TESTOSTERONE absent from backend `BloodMarker` enum

**Status:** open — flag forwarded to the backend blood-testing work.

The spec calls for the dashboard blood panel to show Testosterone, LDL,
ApoB, HbA1c. The backend `BloodMarker` enum (in `core/blood/BloodMarker.java`)
covers lipids + glycemic + inflammation but **does not** include
`TESTOSTERONE`. The Android mapper's `DISPLAY_ORDER` still references it
so that:
  - the moment a user has a TESTOSTERONE reading (either from a future
    backend enum addition or from extracted markers via IMPL-AND-04's
    `/api/me/blood/reports` endpoint), it renders in the correct slot;
  - markers with no reading are transparently omitted, so today's
    users just see LDL + ApoB + HbA1c without an empty row.

No Android-side workaround is added. The one-line backend enum
addition + reference-range entry should live with backend blood work.

### Note — `lifecycle-runtime-compose` added to `:app`

**Status:** informational.

IMPL-AND-01 needs `collectAsStateWithLifecycle` and `LifecycleEventEffect`
in the two dashboard screens (resume-only refresh per spec). IMPL-AND-00
only depended on `lifecycle-runtime-ktx`, so the implementing agent
added `androidx-lifecycle-runtime-compose` to `gradle/libs.versions.toml`
and wired it into `:app`'s dependencies. Same `lifecycle` version
(2.8.7). Drop-in addition, no behaviour change for existing screens.

### Note — `core-network` now exposes Retrofit + Moshi via `api()`

**Status:** informational.

Per the spec, feature modules (here just `core-data` for the dashboard
slice) declare their own Retrofit interfaces (`DashboardApi`) and
Moshi-annotated DTOs. That requires Retrofit + Moshi to be on
downstream modules' compile classpath. The implementing agent flipped
`core-network`'s Retrofit/Moshi declarations from `implementation()` to
`api()`. OkHttp stays internal — the auth-aware client is wholly owned
by `core-network`.

### Note — `Instant` / `LocalDate` Moshi adapters added in `core-network`

**Status:** informational.

The backend serialises `java.time.Instant` and `java.time.LocalDate` as
ISO-8601 strings. Moshi has no built-in adapters for either; the
implementing agent added `InstantJsonAdapter` and `LocalDateJsonAdapter`
in `NetworkModule.kt` and registered them on the shared `Moshi`
instance. This means all future feature DTOs can declare `Instant` /
`LocalDate` fields without `@Json(name = ...)` overrides.

### Note — DTOs use the reflective Moshi adapter, not codegen

**Status:** informational.

IMPL-AND-00 wired `moshi` + `moshi-kotlin` (reflective adapter) but did
**not** wire `moshi-kotlin-codegen` into KSP. The IMPL-AND-01 DTOs
therefore drop `generateAdapter = true` and rely on the
`KotlinJsonAdapterFactory` already registered in `NetworkModule.moshi`.
If we ever need codegen for hot-path serialization, add the KSP
processor in a follow-up — the DTO sources should be trivial to flip.

### Note — single `DashboardApiHttpTest` covers MockWebServer + Moshi contract

**Status:** informational.

The spec called for one MockWebServer test per repository plus a
separate `MoshiContractTest`. The implementing agent consolidated all
three repos + the contract round-trip checks into a single
`DashboardApiHttpTest` — same coverage, fewer setup blocks. Per-mapper
edge cases stay in the dedicated `BodyCompositionMapperTest` and
`BloodMarkerSummaryMapperTest`.

### Note — `DashboardScreenSnapshotTest` deferred

**Status:** open — please advise.

The spec asks for a Paparazzi-style preview snapshot test in
`androidTest/`. IMPL-AND-00 did not wire Paparazzi or any other
snapshot framework, and the test would need the full Compose UI test
harness. Skipped this IMPL — the three-state shape is covered by
`DashboardViewModelTest` (Loading / Loaded / Error transitions per
card) and the visual states are observable by running the dev variant.
If you want a snapshot test added, point at the framework you want
(Paparazzi vs. Roborazzi vs. plain Compose UI test bitmap diff) and
it can land in IMPL-AND-02 alongside settings.

### Note — x-axis chart labels uppercased to match web

**Status:** informational.

The web's `shortDate` helper uppercases the formatted month abbreviation
("MAY 20" rather than "May 20"). The Android `BodyCompositionMapper`
now does the same so the chart labels read identically across both
clients. Spec text says "MMM dd" without specifying casing; matched web
for parity.

---

## Stage 02 — Settings & profile

(no questions yet)

---

## Stage 03 — Medications

(no questions yet)

---

## Stage 04 — Blood testing

(no questions yet)

---

## Stage 05 — Body composition & DEXA

(no questions yet)

---

## Stage 06 — Gym & equipment

(no questions yet)

---

## Cross-cutting

(no items yet)
