# IMPL-AND-12: Android — Goals

## Goal

Bring the **goals** module to the Android phone app at parity with the web
client (`/me/goals`), consuming the existing backend (`IMPL-12-goals-module.md`)
verbatim. Users can browse goals as a card list, view a goal's phase/step
roadmap with metric-binding chips and auto-evaluated progress, create a goal
either manually or via the **AI goal-chat** (SSE) that streams an editable
proposal, commit that proposal, and edit/reorder/complete/delete phases and
steps. This populates a new `feature-goals` module.

> This spec was added in 2026-05 to close a parity gap: goals shipped on
> web/backend (IMPL-12) but had no Android spec. It adapts the IMPL-12 contract
> to the Android stack (Hilt, Retrofit/Moshi, Compose, the IMPL-AND-00 SSE
> helper). No backend changes are required.

## Scope

In scope:

- **Goal list screen** at the `goals` route — cards mirroring web's
  `GoalCard`: title, domain pill, status, and a phase-progress bar
  (`COMPLETED phases / total`). Sourced from `GET /api/me/goals` (shallow,
  no phases).
- **Create entry** (`goals/new`) — a choice screen: "Draft with AI"
  → chat, or "Manual" → form (`ManualGoalEditor` equivalent).
- **Goal-chat screen** (`goals/chat`) — conversational UI that POSTs to
  the **SSE** chat endpoint, streams `thinking` / `message` / `proposal` /
  `done` phase events, renders assistant prose (markdown) and a live
  **editable proposal card**; "Commit" persists the goal in one shot.
- **Goal detail screen** at `goal/{goalId}` — `GET /api/me/goals/{id}`
  (`GoalDeepResponse`): a phase accordion / roadmap timeline, steps with
  metric-binding chips, per-step and per-phase status, reorder, and
  edit/delete actions.
- **CRUD** for goals, phases, steps via the existing endpoints (create,
  update, delete, reorder).
- **Auto-evaluated progress display** — render each step's evaluated
  status from the deep response (the backend evaluates; Android only
  displays). No client-side metric evaluation.

Out of scope (explicitly deferred):

- **Client-side metric evaluation.** `StepEvaluationService` lives
  server-side; Android reads evaluated statuses only.
- **Wear goals UI.** Belongs to a later Wear expansion (IMPL-AND-08).
- **Notifications on goal/step completion.** Separate notifications IMPL.
- **Offline caching.** Online-only V1, per the roadmap (Room → AND-09).
- **Backend changes.** The IMPL-12 contract is consumed as-is.

## Decisions

| Topic | Decision |
|---|---|
| Module | New `feature-goals` module (add to `android/settings.gradle.kts` and `app/build.gradle.kts`). Goal-chat lives here, not in `feature-chat` (reserved for general chat if/when it lands). |
| Spec numbering | Numbered **IMPL-AND-12** (not the next AND-0X) to line up with the backend/web `IMPL-12` it mirrors. |
| Endpoint shapes | Reuse the web/backend shapes byte-for-byte (`GoalResponse`, `GoalDeepResponse`, `PhaseResponse`, `StepResponse`, `GoalProposalDto`, `ChatThreadResponse`). No new DTOs on the backend. |
| Goal-chat transport | **SSE** via the same OkHttp `EventSource` helper from IMPL-AND-00 / reused in IMPL-AND-03 (drug lookup). Phase events: `thinking`, `message`, `proposal` (partial JSON, replace-on-each), `done`. |
| Proposal editing | The streamed `GoalProposalDto` is held in `GoalChatUiState` and edited in place before commit (add/remove/reorder phases + steps, edit metric bindings). Mirrors web's `GoalProposalCard`. |
| Step kinds | Mirror the backend exactly: `MANUAL`, `THRESHOLD`, `SUSTAINED`, `COUNT`. Metric-tracked steps auto-check server-side; the user can manually override. |
| Metric bindings | Render-and-edit only. `metricKey` is a free string namespaced `blood.*` / `body.*` / `workout.*` / `vitals.*`; show a friendly label via a `MetricKeyLabels` map in `core-domain`, fall back to the raw key. Comparators + window mirror the backend. |
| AI model | The backend uses the project's configured Gemini model (per root `CLAUDE.md`); Android never calls the model directly — it only consumes the SSE stream. |
| Reorder UX | Long-press drag handles on phase/step rows; on drop, POST the new id order to the `/reorder` endpoints. Optimistic local reorder, revert + snackbar on failure. |
| Status toggles | Optimistic; PUT the step/phase with new `status`/`done`, revert on failure (matches the adherence pattern in IMPL-AND-03). |
| Date types | `startDate`/`targetDate` as `LocalDate` via the IMPL-AND-00 Moshi `LocalDateAdapter`; `Instant` for `createdAt`/`updatedAt`/`completedAt`. |

## Per-module deliverables

### `core-domain/.../goals/`

Pure-Kotlin domain models mirroring `IMPL-12-goals-module.md`:

```kotlin
package com.gte619n.healthfitness.domain.goals

import java.time.Instant
import java.time.LocalDate

enum class GoalDomain { CARDIOVASCULAR, BODY_COMPOSITION, STRENGTH, METABOLIC, SLEEP, LONGEVITY, OTHER }
enum class GoalStatus { ACTIVE, COMPLETED, ARCHIVED }
enum class GoalSource { MANUAL, AI_GENERATED, AI_ASSISTED }
enum class PhaseStatus { LOCKED, ACTIVE, COMPLETED }
enum class StepKind { MANUAL, THRESHOLD, SUSTAINED, COUNT }
enum class Comparator { LT, LTE, GT, GTE, EQ, BETWEEN }
enum class MetricWindow { LATEST, ROLLING_7D, ROLLING_30D, ROLLING_90D }

data class MetricBinding(
    val metricKey: String,
    val comparator: Comparator,
    val target: Double,
    val target2: Double? = null,   // BETWEEN
    val window: MetricWindow,
)

data class Step(
    val stepId: String,
    val title: String,
    val orderIndex: Int,
    val kind: StepKind,
    val done: Boolean,
    val doneAt: Instant? = null,
    val manualOverride: Boolean = false,
    val metricBinding: MetricBinding? = null,
)

data class Phase(
    val phaseId: String,
    val title: String,
    val description: String? = null,
    val orderIndex: Int,
    val status: PhaseStatus,
    val targetStartDate: LocalDate? = null,
    val targetEndDate: LocalDate? = null,
    val steps: List<Step> = emptyList(),
)

data class Goal(
    val goalId: String,
    val title: String,
    val description: String? = null,
    val domain: GoalDomain,
    val status: GoalStatus,
    val source: GoalSource,
    val startDate: LocalDate?,
    val targetDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val phases: List<Phase> = emptyList(),   // empty in the shallow list response
) {
    val phaseProgress: Pair<Int, Int>
        get() = phases.count { it.status == PhaseStatus.COMPLETED } to phases.size
}

/** Streamed/committed AI proposal — mirrors backend GoalProposalDto. */
data class GoalProposal(
    val title: String,
    val description: String? = null,
    val domain: GoalDomain,
    val targetDate: LocalDate?,
    val phases: List<ProposedPhase>,
) {
    data class ProposedPhase(val title: String, val steps: List<ProposedStep>)
    data class ProposedStep(
        val title: String,
        val kind: StepKind,
        val metricBinding: MetricBinding?,
    )
}

data class ChatMessage(val role: Role, val content: String) {
    enum class Role { USER, ASSISTANT }
}
```

Repository interface:

```kotlin
interface GoalRepository {
    suspend fun list(): List<Goal>                                  // shallow
    suspend fun get(goalId: String): Goal                           // deep
    suspend fun create(request: CreateGoalRequest): Goal
    suspend fun update(goalId: String, request: UpdateGoalRequest): Goal
    suspend fun delete(goalId: String)

    suspend fun addPhase(goalId: String, request: CreatePhaseRequest): Phase
    suspend fun updatePhase(goalId: String, phaseId: String, request: UpdatePhaseRequest): Phase
    suspend fun deletePhase(goalId: String, phaseId: String)
    suspend fun reorderPhases(goalId: String, orderedPhaseIds: List<String>)

    suspend fun addStep(goalId: String, phaseId: String, request: CreateStepRequest): Step
    suspend fun updateStep(goalId: String, phaseId: String, stepId: String, request: UpdateStepRequest): Step
    suspend fun deleteStep(goalId: String, phaseId: String, stepId: String)
    suspend fun reorderSteps(goalId: String, phaseId: String, orderedStepIds: List<String>)

    /** SSE goal-chat: emits thinking/message/proposal/done events; flow completes on done/error. */
    fun chat(messages: List<ChatMessage>, goalId: String? = null): Flow<GoalChatEvent>
    suspend fun commitProposal(proposal: GoalProposal): Goal
    suspend fun chatThread(goalId: String): List<ChatMessage>
}

sealed interface GoalChatEvent {
    data class Thinking(val note: String?) : GoalChatEvent
    data class Message(val markdown: String) : GoalChatEvent
    data class Proposal(val proposal: GoalProposal) : GoalChatEvent   // replace-on-each
    data object Done : GoalChatEvent
    data class Failed(val error: String) : GoalChatEvent
}

object MetricKeyLabels {
    /** "blood.ldl" -> "LDL", "body.bodyFatPct" -> "Body fat %", etc. Falls back to the raw key. */
    fun label(metricKey: String): String = /* map; default = metricKey */ metricKey
}
```

### `core-data/.../goals/`

- `GoalsApi` (Retrofit) mapping the IMPL-12 endpoint table 1:1
  (`GET/POST/PUT/DELETE /api/me/goals`, nested `phases` / `steps`,
  `/reorder`, `GET /{id}/chat`, chat-commit). The SSE chat POST is **not**
  a Retrofit method — it goes through a `GoalChatStreamClient` built on the
  `core-network` SSE helper (same pattern as `DrugLookupStreamClient` in
  IMPL-AND-03).
- DTOs with `@JsonClass(generateAdapter = true)` mirroring each response.
- `GoalMapper` (DTO ↔ domain) with `enumValueOf<T>()` + safe fallback.
- `GoalChatStreamClient.stream(messages, goalId)` parses each SSE event's
  `phase` into a `GoalChatEvent`; the final `proposal` is the editable card
  payload; closes on `done`/`failed`.
- Hilt `GoalsDataModule` binding `GoalRepository` → `DefaultGoalRepository`
  and providing `GoalsApi` + `GoalChatStreamClient`.

### `feature-goals/`

New module, package `com.gte619n.healthfitness.feature.goals`. `build.gradle.kts`
mirrors `feature-medical` (Compose, Hilt, navigation-compose, lifecycle,
test deps incl. Turbine + MockWebServer).

ViewModels + screens:

- `GoalsListViewModel` / `GoalsListScreen` — load shallow list; cards;
  FAB → `goals/new`.
- `GoalCreateScreen` — the "Draft with AI" vs "Manual" choice.
- `GoalChatViewModel` / `GoalChatScreen` — drives the SSE chat; holds
  `messages`, the streaming assistant `message`, and the editable
  `GoalProposal`; "Commit" → `commitProposal`.
- `ManualGoalEditorScreen` — form for title/domain/targetDate + phase/step
  builder (no AI).
- `GoalDetailViewModel` / `GoalDetailScreen` — deep load; roadmap
  timeline; step rows with metric chips; status toggles; reorder;
  edit/delete.

Composables (mirror web): `GoalCard`, `GoalProposalCard`,
`RoadmapTimeline`, `MetricBindingChip`, `PhaseAccordion`, `ChatMarkdown`
(reuse a `core-ui` markdown renderer if present, else a minimal one),
`GoalDetailActions`.

Navigation (type-safe `@Serializable`, per IMPL-AND-00):

```kotlin
@Serializable data object GoalsRoute
@Serializable data object GoalCreateRoute
@Serializable data object GoalChatRoute
@Serializable data class GoalDetailRoute(val goalId: String)
```

### `app/` changes

- `nav/AppNavHost.kt` — add `goalsGraph(...)`.
- Bottom nav / foldable sidebar — add a **Goals** destination.
- Optionally surface an active-goal summary on the dashboard (defer to a
  dashboard follow-up; not required for V1).

### Gradle wiring

- `settings.gradle.kts` — add `:feature-goals`.
- `app/build.gradle.kts` — `implementation(project(":feature-goals"))`.
- `core-data` already has Retrofit/Moshi/SSE from IMPL-AND-00.

## Tests

- `core-domain/.../goals/` — `MetricKeyLabelsTest`; `GoalProgressTest`
  (`phaseProgress` counts COMPLETED phases).
- `core-data/.../goals/` — `GoalMapperTest` (every enum round-trips, null
  `metricBinding`/`targetDate`); `GoalsApiTest` (MockWebServer GET/POST/
  PUT/DELETE + reorder shapes); `GoalChatStreamClientTest` (chunked
  `text/event-stream`: emits `Thinking`, `Message`, a `Proposal`, then
  `Done`; a second test emits `failed` → `Failed`).
- `feature-goals/.../` — `GoalsListViewModelTest`, `GoalDetailViewModelTest`
  (optimistic status toggle + reorder revert on failure),
  `GoalChatViewModelTest` (scripted `GoalChatEvent` flow → state carries
  streaming message + final editable proposal → commit invokes `onDone`),
  and a `GoalCardTest` Compose render check.

## Acceptance criteria

Manual on a real device with a connected account:

1. **List goals.** Open Goals → existing goals render as cards with domain
   pill, status, and phase-progress bar.
2. **Draft with AI.** Goals → "+" → "Draft with AI" → type "lower my LDL
   below 100 in 6 months" → SSE streams `thinking` then assistant prose
   then an editable proposal card (phases + metric-bound steps). Edit a
   step's target, then **Commit** → new goal appears in the list.
3. **Manual goal.** "+" → "Manual" → fill title/domain/targetDate, add a
   phase + a MANUAL step + a THRESHOLD step (`body.bodyFatPct LT 15
   ROLLING_30D`) → save → appears in the list; detail renders the metric
   chip.
4. **Auto-evaluation displays.** A goal whose bound metric is already
   satisfied shows the step as done on the detail screen (backend
   evaluated; no client computation).
5. **Reorder + status toggle.** On detail, drag a step to reorder
   (persists after reload); mark a phase's steps done → phase flips to
   COMPLETED and the next unlocks (optimistic; persists).
6. **Edit / delete.** Edit a goal title; delete a goal → removed from the
   list, backend 404 on subsequent fetch.
7. **SSE failure.** Airplane-mode mid-chat → `Failed` surfaces a snackbar;
   the screen stays usable.

Automated:

- `./gradlew :core-domain:test :core-data:test :feature-goals:test` passes
  (includes the Turbine + MockWebServer SSE tests above).
- `./gradlew :app:assembleDebug` succeeds; APK installs.

## Dependencies

- **IMPL-AND-00 (Foundations)** — required. Hilt, NavHost, `core-network`
  (auth-aware Retrofit + OkHttp `EventSource` SSE helper), `core-ui`
  (snackbar, confirm, state composables), ViewModel/repository
  conventions.
- **IMPL-AND-03 (Medications)** — recommended predecessor; establishes the
  reusable SSE consumer pattern (`DrugLookupStreamClient`) this spec mirrors
  for goal-chat, and the shared `DayOfWeek`/`LocalDate` Moshi adapters.
- **IMPL-AND-01/04/05 (Dashboard / Blood / Body-comp)** — recommended
  before Goals because metric bindings reference `blood.*`, `body.*`,
  `workout.*` read models; not strictly blocking (Android only displays
  evaluated statuses), but the metric-key picker UX is better once those
  domains exist.
- **Backend IMPL-12 (Goals)** — already landed on `main`. No backend
  changes required.

## Open questions deferred to implementation

- **Metric-key picker.** V1 may accept a free-text `metricKey` with the
  `MetricKeyLabels` map for display; a guided picker (grouped by
  namespace, sourced from a backend catalog if one is added) is a
  follow-up.
- **Chat history depth.** The chat-thread endpoint returns the thread;
  decide whether to lazy-load long threads. Defer until real data.
- **Proposal diffing when refining an existing goal.** When `goalId` is
  passed to chat, the proposal may modify an existing goal; V1 commits as
  a fresh shape — confirm the backend's refine-vs-create semantics during
  implementation and add a diff view if needed.
- **Dashboard goal summary.** Whether/where to surface active-goal
  progress on the dashboard — coordinate with the dashboard owner.
