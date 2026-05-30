# Android IMPL — Round 2 Plan

**Date:** 2026-05-27
**Branch:** `feature/android_impl` (already pushed with Round 1 commits)
**Predecessor:** `docs/plans/android-impl-questions.md` — all Round 1 decisions are captured there with **Resolved** markers.

Round 2 lands the user-driven decisions from the Round 1 review. Four
sequential sub-agent stages, each verified (build + tests green) before
the next launches.

---

## Decisions driving this round

| Topic | Decision |
|---|---|
| Bottom nav | **4 primary + More overflow** — `Today / Body / Meds / More`. More sheet lists `Blood`, `Workouts`, `Settings`, `Sign out`. Foldable sidebar unchanged (keeps all destinations). |
| Body composition canonical types | **New `domain.bodycomposition.*` is canonical.** Retire `domain.dashboard.WeightSummary` + its mapper / repo / dashboard ViewModel plumbing. |
| Snapshot test framework | **Paparazzi.** Wired across feature modules + dashboard. |
| Cloud Build release variant | **prod (no change)** — Stage 00 left it on `:app:assembleProdRelease`. |
| Local debug keystore | **Check it in.** Loosen `.gitignore` so `android/debug.keystore` is tracked; every dev + CI gets the same SHA-1. |
| Gym hours UX | **Keep `TextField` (HH:mm)** — no rebuild needed. |
| Backend `BloodMarker.TESTOSTERONE` | **Add now.** Single backend slice: enum + reference range. |

---

## Stage A — Housekeeping + foundation

Independent of stages B/C/D; lands the cross-cutting infra so the next
stages can call into it.

### Deliverables

1. **Check in `android/debug.keystore`**
   - Generate a fresh keystore via `keytool -genkeypair ... -alias androiddebugkey -keypass android -storepass android` (or use the existing local one if its SHA-1 is already registered with Google OAuth).
   - Loosen `android/.gitignore` (or the root `.gitignore`) so `debug.keystore` is tracked but other `*.keystore` files remain ignored.
   - Document the keystore + SHA-1 in `android/README.md`.

2. **Wire Paparazzi**
   - Add `app.cash.paparazzi` plugin version to `gradle/libs.versions.toml`.
   - Apply the plugin to modules that will host snapshot tests:
     `:app`, `:feature-medical`, `:feature-blood`, `:feature-body-composition`, `:feature-workouts`, `:feature-settings`, `:core-ui`.
   - Add a one-shot smoke snapshot test in `:core-ui` covering the existing `EditableNumber` composable to confirm CI wiring works.
   - Update `android/README.md` with the `./gradlew recordPaparazzi` and `./gradlew verifyPaparazzi` workflow.

3. **Backend: add `TESTOSTERONE` to the `BloodMarker` enum**
   - Add `TESTOSTERONE` to `backend/core/.../blood/BloodMarker.java`.
   - Add an adult-male reference range (e.g., 264–916 ng/dL — verify with the existing marker config schema) so the dashboard's reference-bar geometry has data.
   - Update any reference-range-loading test fixtures.

4. **Mark resolved questions** in `docs/plans/android-impl-questions.md`.

### Verification

- `./gradlew :app:assembleDevDebug :wear:assembleDevDebug test` — green.
- `./gradlew :core-ui:verifyPaparazzi` — green (after recording the smoke snapshot).
- Backend: `./gradlew :app:test` against the blood marker module — green.

### Commit shape (examples)

- `chore(android): check in shared debug keystore`
- `chore(android): wire Paparazzi for snapshot testing`
- `feat(backend): add TESTOSTERONE to BloodMarker enum with adult-male reference range`
- `docs(android): mark Round 1 questions resolved`

---

## Stage B — Bottom nav restructure (4 primary + More overflow)

Depends on Stage A's housekeeping landing first only because Paparazzi
gets used by Stage D — B itself only touches nav code.

### Deliverables

1. **`MoreScreen` (or `MoreSheet`)**
   - New screen at `feature-settings/.../more/MoreScreen.kt` (lives next to `SettingsScreen` since it's a navigation hub).
   - List items for: `Blood`, `Workouts`, `Settings`, `Sign out`.
   - Each row uses the existing `core-ui` list-item style; tap navigates to the destination's route (or, for Sign out, invokes the `onSignedOut` lambda already threaded through `AppRoot`).
   - Material Design pattern: top-level destinations listed as a vertical menu, not a bottom-sheet (better for one-handed reach on tall screens).

2. **Rebuild `BottomNavDestinations`**
   - `android/app/src/main/java/com/gte619n/healthfitness/mobile/navigation/NavDestinations.kt`
   - 4 entries: `Today`, `Body`, `Meds`, `More`.
   - Icons per Material 3 outlined/filled pattern (use Material icon set).

3. **Update `AppNavGraph`**
   - Add `MoreRoute` as a typed nav route.
   - Wire it into the `NavHost`.
   - Confirm Blood and Workouts feature routes are still reachable through MoreScreen.

4. **Foldable sidebar** — no changes (still shows all destinations).

5. **Cleanup**
   - Drop the `Route.MedicationDetail` / `Route.BloodReportDetail` redirect shims if no longer needed.

### Verification

- `./gradlew :app:assembleDevDebug :wear:assembleDevDebug test` — green.
- Manual: navigate Today → Body → Meds → More → Blood, More → Workouts, More → Settings → Sign out.
- Add Paparazzi snapshot for `MoreScreen` (uses Stage A's wiring).

### Commit shape

- `feat(android): add MoreScreen as bottom-nav overflow`
- `refactor(android): bottom nav to Today / Body / Meds / More`

---

## Stage C — Body composition canonical consolidation

Retires the IMPL-AND-01 dashboard-local body-composition types; the
new IMPL-AND-05 types become canonical.

### Deliverables

1. **Migrate downsampler + xLabels logic**
   - Move `BodyCompositionMapper.buildXLabels` + downsampler from `core-data/dashboard/` to `core-data/bodycomposition/` (or wherever the new mapper lives).
   - Ensure the result is reachable via `BodyCompositionRepository.observeSnapshot()` (or equivalent) on the new type.

2. **Replace dashboard hero data feed**
   - `DashboardViewModel` consumes the new `BodyCompositionRepository` (already injected for the feature module).
   - `WeightSummary` → `BodyCompositionSnapshot` in the dashboard's `DashboardUiState`.
   - Foldable hero composable + Phone today composable updated to read snapshot fields.

3. **Delete legacy types**
   - `domain.dashboard.WeightSummary`
   - `domain.dashboard.BodyMetric` (if only used by WeightSummary)
   - `domain.dashboard.BodyCompositionPoint` (if duplicated)
   - `core-data/dashboard/BodyCompositionMapper`
   - `core-data/dashboard/BodyCompositionRepositoryImpl`
   - Their tests.

4. **Reuse where possible**
   - Existing `BodyCompositionMapperTest` should largely port to the snapshot mapper. Keep the math coverage.

### Verification

- `./gradlew :app:assembleDevDebug :wear:assembleDevDebug test` — green.
- `DashboardViewModelTest` still green (against the new repo).
- New snapshot mapper tests cover the downsample + xLabels math at parity with the deleted tests.

### Commit shape

- `refactor(android): consolidate body-composition domain types under domain.bodycomposition`
- `refactor(android): route dashboard hero through BodyCompositionRepository`
- `test(android): port body-composition mapper coverage to snapshot pipeline`

---

## Stage D — Paparazzi snapshot tests

Adds the deferred visual coverage from Round 1 stages 01, 04, 05, 06.

### Deliverables

For each, add a `*PaparazziTest.kt` under the appropriate module's
`src/test/`:

1. **`DashboardScreen`** (`:app`)
   - Loading, Loaded (sample data), Error — 3 snapshots per layout
     class (Phone Compact + Foldable Medium).

2. **`MarkerReferenceBar`** (`:feature-blood`)
   - In-range, low, high — 3 snapshots.

3. **`DexaRegionGrid` + `EditableNumberCell`** (`:feature-body-composition`)
   - Read mode + edit mode + saving mode — 3 snapshots each.

4. **`LocationCard`** (`:feature-workouts`)
   - With cover photo, without cover photo, default badge — 3 snapshots.

5. **`EquipmentSpecForm`** (`:feature-workouts`)
   - One snapshot per category: Selectorized, PlateLoaded, Cable, Cardio, Bodyweight, WeightSet (6).

6. **`MoreScreen`** (`:feature-settings`) — added in Stage B but the
   snapshot lives here.

### Verification

- `./gradlew recordPaparazzi` — generates baselines.
- `./gradlew verifyPaparazzi` — green.
- Snapshots committed to the repo (text-only PNGs by convention).

### Commit shape

- `test(android): add Paparazzi snapshots for dashboard, blood, body comp`
- `test(android): add Paparazzi snapshots for workouts and settings`

---

## Out of scope for Round 2

- Phase 7 (active workout logging) — needs ADR.
- Phase 8 (wear surfaces) — explicit roadmap deferral.
- Phase 9 (nutrition, sleep, push notifications, theme) — explicit
  roadmap deferral.
- Any redesign of Blood / Workouts surfaces beyond the nav placement
  change.
- Backend changes beyond the single `TESTOSTERONE` enum addition.

---

## Append-only questions file usage in Round 2

Sub-agents continue to append questions, blockers, and notes to
`docs/plans/android-impl-questions.md` — but under a new **"Round 2 —
{Stage}"** section per stage. Round 1 sections stay frozen with their
Resolved markers.
