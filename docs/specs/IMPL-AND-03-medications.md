# IMPL-AND-03: Android — Medications

> **⚠️ Contract update (2026-05, post PR #8 "dosing periods").** This spec was
> originally written against the early IMPL-05 medications contract. The
> backend + web have since added **dated dosage periods** and three new
> behaviors. Android must account for all of these (details inline, marked
> **[PR#8]**):
> - `Medication.dosagePeriods: List<DosagePeriod>` — the dated dose history is
>   now the source of truth; scalar `dose`/`unit` mirror the active period.
> - `POST /api/me/medications/{id}/dosage` — change dose effective on a date
>   (closes the open period, opens a new one).
> - `POST /api/me/medications/{id}/reactivate` — resume a discontinued med
>   from a resume date.
> - `UpdateMedicationRequest.startDate` — the medication start date is now
>   editable; `discontinue` accepts an explicit `endDate`.
> - **`DayOfWeek` is lowercase on the wire** (`"mon"`…`"sun"`) — the backend
>   registers a Jackson key (de)serializer; the Android Moshi adapter must
>   mirror it or `FrequencyConfig.specificDays` round-trips will fail.
>
> "Out of scope: backend changes" below is **no longer true** — the backend
> contract changed; Android consumes the new shapes (no new backend work).

## Goal

Bring the medications domain to the Android phone app at full feature parity
with the web client (`/me/meds`). Users can browse current and discontinued
medications as a card grid with AI-generated drug imagery and 30-day
adherence sparklines, add a medication via a streaming AI-assisted drug
lookup, edit dose/frequency/schedule, log adherence per time-window (both
from the medications screen and from a "Today's doses" card on the
dashboard), and discontinue with a reason. This populates the
`feature-medical` module (currently an empty placeholder) and replaces the
hardcoded med entry in the dashboard's `TodayCard.kt` with a live,
interactive Today's Doses card.

## Scope

In scope:

- Medications list screen at the `medications` route, with **Current** and
  **History** tabs (filtered by `MedicationStatus.ACTIVE` /
  `DISCONTINUED`), rendered as a responsive card grid (1 column on phone,
  2-3 columns on foldable / unfolded inner display via
  `WindowSizeClass.widthSizeClass`).
- `MedicationCard` composable mirroring web's `MedicationCard.tsx`: square
  drug image, name + category pill, dose + frequency, time-window labels,
  30-day adherence sparkline, "Discontinued" overlay when applicable.
- FAB "+" opens **Add medication** flow as a full-screen modal:
  - Drug search input that filters the local catalog (`GET /api/drugs`)
  - If no catalog hit and query length >= 3, auto-trigger SSE lookup
    against `POST /api/drugs/lookup/stream` and stream phase updates into
    the UI ("Searching…" → "Found Testosterone Cypionate" → "Generating
    image…" → final selection)
  - Manual-entry fallback (custom name / category / form) when AI lookup
    returns `not_found`
  - Form: dose + unit, frequency (DAILY/WEEKLY/MONTHLY/PRN), times-per-
    period, time-window picker (MORNING/AFTERNOON/EVENING/BEDTIME),
    prescribed-by, notes
  - Submit → `POST /api/me/medications`
- **Medication detail screen** at `medication/{medicationId}` route:
  - Drug image, name, category, form, current dose + frequency + time
    slots
  - **Dosing history** — the dated `dosagePeriods` timeline (newest first,
    each row `dose unit · startDate – endDate`, the open period marked
    "Current" with `– Present`). Render only when more than one period
    exists. **[PR#8]**
  - Dose-change history (chronological list from
    `MedicationDetailResponse.history`)
  - 30-day adherence calendar / sparkline
  - Correlated blood markers (read-only chip row)
  - Editable **start date** (via Edit) and, when discontinuing, an
    explicit **end date** picker. **[PR#8]**
  - Edit, **Change dose** (effective-date picker → `POST .../dosage`),
    Discontinue (reason picker + end-date), **Resume** (shown only when
    `status == DISCONTINUED` → resume-date picker → `POST .../reactivate`),
    Delete actions. **[PR#8]** Dose is changed through the dedicated
    Change-dose flow so it builds dated history — the plain Edit form no
    longer edits `dose` directly (mirrors web).
- **Today's Doses card** on the dashboard, replacing the fixture med row
  in `TodayCard.kt`:
  - Sourced from `GET /api/me/medications/today`
  - Each row: checkbox, drug name, dose + unit, time-window label
  - Tap checkbox = optimistic `POST /api/me/medications/{id}/adherence`,
    revert + snackbar on failure
  - "View all" link routes to the medications screen
- **Quick-log tile** in the phone Log bottom sheet (the existing "Log"
  tab) that opens directly into "log a dose for an active med" — reuses
  the same `LogDoseUseCase` the Today's Doses checkbox uses.
- Drug-image rendering via the Coil wrapper from `core-ui` (`AsyncImage`),
  with `drug.imageFallback` on error and a form-shaped placeholder while
  loading.

Out of scope (explicitly deferred):

- **Protocol grouping advanced UI** (named protocols, drag-to-assign,
  protocol-aware schedule view). `protocolId` is read on the wire but no
  CRUD UI is built; protocol badges render only as a read-only label.
- **Blood-marker correlation chart markers** (vertical dashed lines for
  med start/stop on blood-marker trend charts). Lands with IMPL-AND-04
  (Blood Testing) since the chart lives there.
- **Refill / supply tracking.** Not present on web; do not introduce
  here.
- **Push notifications / dose reminders.** Belongs in a separate
  notifications IMPL.
- **Cycle frequency editor UI.** `FrequencyType.CYCLE` is modeled in the
  domain (so old web-created records round-trip) but the Add/Edit flow
  hides it behind the same simple/PRN/Weekly UI web exposes today —
  matches current web behavior.
- **Backend changes.** No new backend work is required, but note the
  contract is **not** the original IMPL-05 shape — it includes the PR #8
  dosing-period additions (see the contract-update banner at the top).
  Android consumes the current shapes verbatim.
- **Editing past dosage periods.** The backend supports correcting history
  by submitting a full `dosagePeriods` array on `PUT`, but the Android V1
  UI only *displays* the timeline and changes the dose forward via
  `/dosage`. A history-correction editor is deferred (matches web, which
  also defers it).

## Decisions

| Topic | Decision |
|---|---|
| Module | Populate the existing empty `feature-medical` module (per `android/settings.gradle.kts`). Do not introduce a new module. |
| Endpoint shapes | Reuse web-side shapes byte-for-byte. No new DTOs on the backend. `MedicationResponse` / `DrugResponse` / `AdherenceSummary` are the contract. |
| Drug lookup | SSE consumer via the OkHttp `EventSource` helper from IMPL-AND-00 `core-network`. UI displays each `phase` message as it arrives. Final `phase: "complete"` carries the `drug` payload. |
| Adherence logging | Optimistic update — flip the checkbox in `TodaysDosesUiState` immediately, fire the POST, revert the row + show an error snackbar via `SnackbarController` on failure. Matches web's `useTransition` UX. |
| Time-slot boundaries | Use the same fixed windows web hard-codes (morning 6-10, afternoon 12-15, evening 17-20, bedtime 21-23). Labels routed through `TimeWindowLabels` in `core-domain` so we don't drift from web. **Not** locale-dependent for V1. |
| Image loading | Coil via `core-ui` `AsyncImage`. On 404 / load failure fall back to `drug.imageFallback`. While the drug image is still being generated server-side, web polls every 3s; Android does the same via `viewModelScope.launch` with `delay(3_000)` up to 20 attempts in `MedicationsViewModel`. |
| Today's doses source | `GET /api/me/medications/today` (already implemented backend-side). Android does not re-derive doses from frequency client-side. |
| Dose-form decimals | Display `dose: Double` as integer when `dose % 1.0 == 0.0`, else one decimal. Centralized in `DoseFormatter` (in `core-domain`) so the medications card, detail screen, and Today's Doses card all match. |
| Local cache | No Room caching in V1. Backend is source of truth and list-fetch latency on Wi-Fi is sub-300ms. Add Room when offline read is needed — out of scope for this IMPL. |
| Confirm dialogs | Use the `ConfirmDialog` composable from `core-ui` (mirrors web's `useConfirm`). Discontinue uses a dedicated `DiscontinueDialog` because of the reason-picker + notes textarea — too much for the generic confirm. |
| Custom medication entries | When no `drugId` is set (manual entry), display `medication.customName` and fall back to a generic form-shaped placeholder image. Mirrors web's `medication.customName ?? drug?.name` rendering. |
| Discontinue reason enum | Mirror web exactly: `COMPLETED`, `SIDE_EFFECTS`, `SWITCHED`, `COST`, `OTHER`. Labels live in `core-domain` `DiscontinueReasonLabels`. |
| Date types | Wire `startDate` / `endDate` as `LocalDate` via Moshi `LocalDateAdapter` from IMPL-AND-00. `Instant` for `takenAt`. |
| Dosage periods **[PR#8]** | `Medication.dosagePeriods: List<DosagePeriod>` is the canonical dose history; the active period has `endDate == null`. Scalar `dose`/`unit` mirror the active period — keep reading them for card/summary display, but render the timeline from `dosagePeriods`. End dates are **exclusive** (a closed period's `endDate` equals the next period's `startDate`); subtract a day only for human-readable ranges. |
| Change dose **[PR#8]** | A dedicated `changeDose()` repository call hitting `POST /api/me/medications/{id}/dosage` with `{ dose, unit?, startDate?, changeNotes? }`. Backend closes the open period at `startDate` and opens a new one; also writes a `DOSE_CHANGE` history entry. The Edit form drops its dose field. |
| Reactivate **[PR#8]** | `reactivate()` → `POST /api/me/medications/{id}/reactivate` with `{ resumeDate? }`. Reopens dosing from `resumeDate` (gap stays visible in history). "Resume" button shows only for `DISCONTINUED` meds. |
| Editable start / end dates **[PR#8]** | `UpdateMedicationRequest` gains `startDate: LocalDate?` (shifts the earliest dosage period's start). `discontinue` sends an explicit `endDate` (defaults to today) that closes the open period. |
| `DayOfWeek` wire case **[PR#8]** | The backend serializes `DayOfWeek` map/enum values as **lowercase** (`"mon"`…`"sun"`) via a Jackson key (de)serializer. The Moshi adapter for `FrequencyConfig.specificDays` (and any `DayOfWeek`) must encode lowercase and decode case-insensitively, or weekly-schedule round-trips break. Mirror the web `DayOfWeek = 'mon'\|…` contract, not the uppercase enum name. |

## Per-module deliverables

### `core-domain/src/main/java/com/gte619n/healthfitness/domain/medications/`

Domain models (pure Kotlin, no framework dependencies). Field names mirror
`backend/api/medication/MedicationResponse.java` to keep wire/Moshi mapping
trivial.

```kotlin
package com.gte619n.healthfitness.domain.medications

import java.time.Instant
import java.time.LocalDate

enum class DrugCategory { PRESCRIPTION, SUPPLEMENT, OTC, PEPTIDE, TOPICAL }

enum class DrugForm {
    INJECTABLE_VIAL, TABLET, CAPSULE, SOFTGEL,
    CREAM, PATCH, LIQUID, POWDER
}

enum class MedicationStatus { ACTIVE, DISCONTINUED }

enum class FrequencyType { DAILY, WEEKLY, MONTHLY, PRN, CYCLE }

enum class TimeWindow { MORNING, AFTERNOON, EVENING, BEDTIME }

// [PR#8] Domain enum is uppercase, but the WIRE form is lowercase ("mon"…).
// The Moshi adapter must map both directions (see DTO/mapper section).
enum class DayOfWeek { MON, TUE, WED, THU, FRI, SAT, SUN }

enum class DiscontinueReason { COMPLETED, SIDE_EFFECTS, SWITCHED, COST, OTHER }

enum class ChangeType { DOSE_CHANGE, FREQUENCY_CHANGE, SCHEDULE_CHANGE }

data class Drug(
    val drugId: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val category: DrugCategory,
    val form: DrugForm,
    val defaultUnit: String,
    val commonDoses: List<String> = emptyList(),
    val imageUrl: String?,
    val imageFallback: String?,
    val suggestedMarkers: List<String> = emptyList(),
    val description: String? = null,
)

data class FrequencyConfig(
    val type: FrequencyType,
    val timesPerPeriod: Int? = null,
    val specificDays: List<DayOfWeek>? = null,
    val cycle: CycleConfig? = null,
) {
    data class CycleConfig(
        val onWeeks: Int,
        val offWeeks: Int,
        val startDate: LocalDate,
    )
}

data class TimeSlot(
    val window: TimeWindow,
    val dose: Double,
)

// [PR#8] Dated dose history. The active/current period has endDate == null.
// End dates are exclusive (a closed period's end == the next period's start).
data class DosagePeriod(
    val dose: Double,
    val unit: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
) {
    val isActive: Boolean get() = endDate == null
}

data class AdherenceSummary(
    val last30Days: List<DayAdherence>,
    val percentage: Double,
) {
    data class DayAdherence(val date: LocalDate, val taken: Boolean)
}

data class Medication(
    val medicationId: String,
    val drugId: String?,
    val drug: Drug?,
    val customName: String?,
    val status: MedicationStatus,
    val dose: Double,
    val unit: String,
    val frequency: FrequencyConfig,
    val timeSlots: List<TimeSlot>,
    val protocolId: String?,
    val notes: String?,
    val prescribedBy: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val discontinueReason: DiscontinueReason?,
    val discontinueNotes: String?,
    val correlatedMarkers: List<String>,
    val dosagePeriods: List<DosagePeriod> = emptyList(),   // [PR#8] dated dose history
    val adherence: AdherenceSummary?,
) {
    val displayName: String
        get() = customName ?: drug?.name ?: "Unknown"
}

data class MedicationHistoryEntry(
    val historyId: String,
    val changeType: ChangeType,
    val previousValue: String,
    val newValue: String,
    val changedAt: Instant,
    val notes: String?,
)

data class MedicationDetail(
    val medication: Medication,
    val history: List<MedicationHistoryEntry>,
)

data class TodaysDose(
    val medicationId: String,
    val drugName: String,
    val window: TimeWindow,
    val dose: Double,
    val unit: String,
    val taken: Boolean,
    val takenAt: Instant?,
)
```

Repository interfaces:

```kotlin
package com.gte619n.healthfitness.domain.medications

import kotlinx.coroutines.flow.Flow

interface MedicationRepository {
    suspend fun list(status: MedicationStatus? = null): List<Medication>
    suspend fun get(medicationId: String): MedicationDetail
    suspend fun create(request: CreateMedicationRequest): Medication
    suspend fun update(medicationId: String, request: UpdateMedicationRequest): Medication
    /** [PR#8] Change dose effective on a date; backend closes the open period + opens a new one. */
    suspend fun changeDose(medicationId: String, request: ChangeDoseRequest): Medication
    suspend fun discontinue(
        medicationId: String,
        reason: DiscontinueReason,
        notes: String?,
        endDate: LocalDate? = null,
    ): Medication
    /** [PR#8] Resume a discontinued medication from [resumeDate] (defaults to today server-side). */
    suspend fun reactivate(medicationId: String, resumeDate: LocalDate? = null): Medication
    suspend fun delete(medicationId: String)
    suspend fun todaysDoses(): List<TodaysDose>
}

interface DrugRepository {
    suspend fun catalog(): List<Drug>
    suspend fun get(drugId: String): Drug

    /** SSE stream emitting phase updates: SEARCHING, FOUND, GENERATING_IMAGE, COMPLETE, NOT_FOUND, FAILED. */
    fun lookupStream(query: String): Flow<DrugLookupEvent>
}

sealed interface DrugLookupEvent {
    data class Progress(val phase: String, val message: String?) : DrugLookupEvent
    data class Found(val drug: Drug) : DrugLookupEvent
    data class NotFound(val message: String?) : DrugLookupEvent
    data class Failed(val error: String) : DrugLookupEvent
}

interface AdherenceRepository {
    /** Log a dose taken at [takenAt] (default now). Returns the new total taken for the day for that med. */
    suspend fun logDose(
        medicationId: String,
        window: TimeWindow,
        takenAt: Instant = Instant.now(),
        dose: Double? = null,
    )
    suspend fun undoDose(medicationId: String, date: LocalDate, window: TimeWindow)
}

data class CreateMedicationRequest(
    val drugId: String? = null,
    val customName: String? = null,
    val customCategory: DrugCategory? = null,
    val customForm: DrugForm? = null,
    val dose: Double,
    val unit: String,
    val frequency: FrequencyConfig,
    val timeSlots: List<TimeSlot>,
    val notes: String? = null,
    val prescribedBy: String? = null,
    val correlatedMarkers: List<String> = emptyList(),
)

data class UpdateMedicationRequest(
    val customName: String? = null,
    val dose: Double? = null,         // legacy; prefer changeDose() for dated history
    val unit: String? = null,
    val frequency: FrequencyConfig? = null,
    val timeSlots: List<TimeSlot>? = null,
    val notes: String? = null,
    val prescribedBy: String? = null,
    val correlatedMarkers: List<String>? = null,
    val startDate: LocalDate? = null,         // [PR#8] edit the medication start date
    val dosagePeriods: List<DosagePeriod>? = null, // [PR#8] full-replacement history correction (V1 unused)
    val changeNotes: String? = null,
)

// [PR#8] effective-dated dose change. unit defaults to the med's current unit;
// startDate defaults to today server-side.
data class ChangeDoseRequest(
    val dose: Double,
    val unit: String? = null,
    val startDate: LocalDate? = null,
    val changeNotes: String? = null,
)
```

Helpers (pure, unit-testable):

```kotlin
package com.gte619n.healthfitness.domain.medications

object DoseFormatter {
    fun format(dose: Double, unit: String): String {
        val isWhole = dose % 1.0 == 0.0
        val n = if (isWhole) dose.toInt().toString() else "%.1f".format(dose)
        return "$n $unit"
    }
}

object FrequencyFormatter {
    fun format(f: FrequencyConfig): String = when (f.type) {
        FrequencyType.DAILY   -> if ((f.timesPerPeriod ?: 1) == 1) "Once daily" else "${f.timesPerPeriod}x daily"
        FrequencyType.WEEKLY  -> "${f.timesPerPeriod ?: 1}x weekly"
        FrequencyType.MONTHLY -> "Monthly"
        FrequencyType.PRN     -> "As needed"
        FrequencyType.CYCLE   -> f.cycle?.let { "${it.onWeeks}w on / ${it.offWeeks}w off" } ?: "Cycle"
    }
}

object TimeWindowLabels {
    fun label(w: TimeWindow): String = when (w) {
        TimeWindow.MORNING   -> "Morning"
        TimeWindow.AFTERNOON -> "Afternoon"
        TimeWindow.EVENING   -> "Evening"
        TimeWindow.BEDTIME   -> "Bedtime"
    }
}
```

### `core-data/src/main/java/com/gte619n/healthfitness/data/medications/`

Retrofit services + Moshi adapters + repository implementations.

```kotlin
package com.gte619n.healthfitness.data.medications

import com.gte619n.healthfitness.domain.medications.*
import retrofit2.http.*

internal interface MedicationsApi {
    @GET("/api/me/medications")
    suspend fun list(@Query("status") status: String? = null): List<MedicationDto>

    @GET("/api/me/medications/{id}")
    suspend fun get(@Path("id") id: String): MedicationDetailDto

    @POST("/api/me/medications")
    suspend fun create(@Body body: CreateMedicationDto): MedicationDto

    @PUT("/api/me/medications/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateMedicationDto): MedicationDto

    // [PR#8] change dose effective on a date (dated history)
    @POST("/api/me/medications/{id}/dosage")
    suspend fun changeDose(@Path("id") id: String, @Body body: ChangeDoseDto): MedicationDto

    @POST("/api/me/medications/{id}/discontinue")
    suspend fun discontinue(@Path("id") id: String, @Body body: DiscontinueDto): MedicationDto

    // [PR#8] resume a discontinued medication
    @POST("/api/me/medications/{id}/reactivate")
    suspend fun reactivate(@Path("id") id: String, @Body body: ReactivateDto): MedicationDto

    @DELETE("/api/me/medications/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("/api/me/medications/today")
    suspend fun today(): List<TodaysDoseDto>
}

internal interface AdherenceApi {
    @POST("/api/me/medications/{id}/adherence")
    suspend fun log(@Path("id") id: String, @Body body: LogDoseDto)

    @DELETE("/api/me/medications/{id}/adherence/{date}/{window}")
    suspend fun undo(
        @Path("id") id: String,
        @Path("date") date: String,
        @Path("window") window: String,
    )
}

internal interface DrugsApi {
    @GET("/api/drugs")
    suspend fun catalog(): List<DrugDto>

    @GET("/api/drugs/{id}")
    suspend fun get(@Path("id") id: String): DrugDto
}
```

DTOs mirror the JSON exactly (Moshi-codegen `@JsonClass(generateAdapter =
true)`):

```kotlin
@JsonClass(generateAdapter = true)
data class MedicationDto(
    val medicationId: String,
    val drugId: String?,
    val drug: DrugDto?,
    val customName: String?,
    val status: String,
    val dose: Double,
    val unit: String,
    val frequency: FrequencyConfigDto,
    val timeSlots: List<TimeSlotDto>,
    val protocolId: String?,
    val notes: String?,
    val prescribedBy: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val discontinueReason: String?,
    val discontinueNotes: String?,
    val correlatedMarkers: List<String>,
    val adherence: AdherenceSummaryDto?,
) { fun toDomain(): Medication = MedicationMapper.toDomain(this) }

// DrugDto, FrequencyConfigDto, TimeSlotDto, AdherenceSummaryDto,
// MedicationDetailDto, TodaysDoseDto, CreateMedicationDto,
// UpdateMedicationDto, DiscontinueDto, LogDoseDto — same pattern.
// [PR#8] ADD: DosagePeriodDto { dose, unit, startDate: LocalDate, endDate: LocalDate? },
//        plus `dosagePeriods: List<DosagePeriodDto>` on MedicationDto and
//        MedicationDetailDto; ChangeDoseDto { dose, unit?, startDate?, changeNotes? };
//        ReactivateDto { resumeDate: LocalDate? }; DiscontinueDto gains `endDate: LocalDate?`.
```

`MedicationMapper` converts DTO → domain (string-to-enum via
`enumValueOf<T>()` with safe fallback). Inverse mapper used for write
paths.

**[PR#8] `DayOfWeek` lowercase adapter.** Register a Moshi adapter (or
`@ToJson`/`@FromJson` pair) for `DayOfWeek` that writes
`value.name.lowercase()` and reads `valueOf(it.uppercase())`. Without it,
`FrequencyConfig.specificDays` fails to (de)serialize against the backend's
lowercase `"mon"`…`"sun"` keys. This mirrors the backend's
`DayOfWeekJacksonConfig` (app module) and the web `DayOfWeek = 'mon'\|…`
type. The same adapter is shared with IMPL-AND-06 (gym hours), which keys a
`Map<DayOfWeek, HoursSlot>` the same way.

SSE consumer for drug lookup uses the EventSource helper from
`core-network`:

```kotlin
package com.gte619n.healthfitness.data.medications

import com.gte619n.healthfitness.domain.medications.*
import com.gte619n.healthfitness.network.sse.SseClient
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class DrugLookupStreamClient(
    private val sseClient: SseClient,
    private val baseUrl: String,
    private val moshi: Moshi,
) {
    private val phaseAdapter = moshi.adapter(LookupPhaseDto::class.java)

    fun stream(query: String): Flow<DrugLookupEvent> = callbackFlow {
        val request = Request.Builder()
            .url("$baseUrl/api/drugs/lookup/stream")
            .post("""{"query":"$query"}""".toRequestBody("application/json".toMediaType()))
            .build()

        val source = sseClient.newEventSource(request) { event ->
            val dto = phaseAdapter.fromJson(event.data) ?: return@newEventSource
            when (dto.phase) {
                "complete"    -> dto.drug?.let { trySend(DrugLookupEvent.Found(it.toDomain())) }
                "not_found"   -> trySend(DrugLookupEvent.NotFound(dto.message))
                "failed"      -> trySend(DrugLookupEvent.Failed(dto.error ?: "Lookup failed"))
                else          -> trySend(DrugLookupEvent.Progress(dto.phase, dto.message))
            }
            if (dto.phase in setOf("complete", "not_found", "failed")) close()
        }
        awaitClose { source.cancel() }
    }
}

@JsonClass(generateAdapter = true)
internal data class LookupPhaseDto(
    val phase: String,
    val message: String? = null,
    val error: String? = null,
    val drug: DrugDto? = null,
)
```

Repository implementations (Hilt-injected, `@Singleton`):

```kotlin
@Singleton
internal class DefaultMedicationRepository @Inject constructor(
    private val api: MedicationsApi,
) : MedicationRepository { /* maps DTOs ↔ domain */ }

@Singleton
internal class DefaultDrugRepository @Inject constructor(
    private val api: DrugsApi,
    private val lookupClient: DrugLookupStreamClient,
) : DrugRepository {
    override fun lookupStream(query: String) = lookupClient.stream(query)
    /* ... */
}

@Singleton
internal class DefaultAdherenceRepository @Inject constructor(
    private val api: AdherenceApi,
) : AdherenceRepository { /* ... */ }
```

Hilt module:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class MedicationsDataModule {
    @Binds abstract fun medicationRepository(impl: DefaultMedicationRepository): MedicationRepository
    @Binds abstract fun drugRepository(impl: DefaultDrugRepository): DrugRepository
    @Binds abstract fun adherenceRepository(impl: DefaultAdherenceRepository): AdherenceRepository

    companion object {
        @Provides fun medicationsApi(retrofit: Retrofit): MedicationsApi = retrofit.create()
        @Provides fun drugsApi(retrofit: Retrofit): DrugsApi = retrofit.create()
        @Provides fun adherenceApi(retrofit: Retrofit): AdherenceApi = retrofit.create()
    }
}
```

### `feature-medical/`

Populate the empty module. Package root:
`com.gte619n.healthfitness.feature.medical`.

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.gte619n.healthfitness.feature.medical"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    buildFeatures { compose = true }
    kotlinOptions { jvmTarget = "21" }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    implementation(project(":core-network"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")

    testImplementation("junit:junit:4.13.2")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

Screens + ViewModels:

```kotlin
package com.gte619n.healthfitness.feature.medical.list

@HiltViewModel
class MedicationsViewModel @Inject constructor(
    private val medications: MedicationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<MedicationsUiState>(MedicationsUiState.Loading)
    val state: StateFlow<MedicationsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = MedicationsUiState.Loading
        runCatching { medications.list() }
            .onSuccess { all ->
                _state.value = MedicationsUiState.Ready(
                    active = all.filter { it.status == MedicationStatus.ACTIVE },
                    discontinued = all.filter { it.status == MedicationStatus.DISCONTINUED },
                )
            }
            .onFailure { _state.value = MedicationsUiState.Error(it.message ?: "Unknown error") }
    }
}

sealed interface MedicationsUiState {
    data object Loading : MedicationsUiState
    data class Ready(val active: List<Medication>, val discontinued: List<Medication>) : MedicationsUiState
    data class Error(val message: String) : MedicationsUiState
}

@Composable
fun MedicationsListScreen(
    onAdd: () -> Unit,
    onMedicationClick: (medicationId: String) -> Unit,
    viewModel: MedicationsViewModel = hiltViewModel(),
) { /* tabs + grid + FAB */ }
```

```kotlin
package com.gte619n.healthfitness.feature.medical.add

@HiltViewModel
class AddMedicationViewModel @Inject constructor(
    private val drugs: DrugRepository,
    private val medications: MedicationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AddMedicationUiState())
    val state: StateFlow<AddMedicationUiState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        // Local catalog filter
        // If no local match and length >= 3, trigger SSE after 400ms debounce
    }

    fun startLookup(query: String) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            drugs.lookupStream(query).collect { event ->
                _state.update { it.copy(lookupEvent = event) }
            }
        }
    }

    fun selectDrug(drug: Drug) { /* advance to form step */ }

    fun submit(request: CreateMedicationRequest, onDone: (Medication) -> Unit) =
        viewModelScope.launch {
            runCatching { medications.create(request) }
                .onSuccess(onDone)
                .onFailure { _state.update { s -> s.copy(error = it.message) } }
        }
}

data class AddMedicationUiState(
    val step: Step = Step.SEARCH,
    val query: String = "",
    val catalog: List<Drug> = emptyList(),
    val selectedDrug: Drug? = null,
    val lookupEvent: DrugLookupEvent? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
) { enum class Step { SEARCH, FORM, CUSTOM } }
```

```kotlin
package com.gte619n.healthfitness.feature.medical.detail

@HiltViewModel
class MedicationDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val medications: MedicationRepository,
) : ViewModel() {
    private val medicationId: String = checkNotNull(savedState["medicationId"])
    /* exposes MedicationDetailUiState with detail + actions: edit/discontinue/delete */
}
```

Composables (file paths in `feature-medical/src/main/java/.../components/`):

- `MedicationCard.kt` — square image (Coil `AsyncImage`), name, category
  pill, dose + frequency line, time-window chip row, adherence sparkline.
- `MedicationGrid.kt` — `LazyVerticalGrid` with column count from
  `WindowWidthSizeClass` (`Compact = 1`, `Medium = 2`, `Expanded = 3`).
- `AdherenceSparkline.kt` — 30 vertical bars, green (`Color(0xFF22C55E)`)
  when `taken`, gray when not, percentage label on the right.
- `DrugImage.kt` — wraps `AsyncImage` from `core-ui`; falls back to
  `imageFallback`, then to a form-shaped vector placeholder per `DrugForm`.
- `TimeSlotEditor.kt` — chip row of 4 windows + per-slot dose input.
- `FrequencySelector.kt` — segmented picker for `FrequencyType` +
  conditional `timesPerPeriod` stepper + `specificDays` chips for WEEKLY.
- `DiscontinueDialog.kt` — `AlertDialog` with `DiscontinueReason` dropdown
  and notes `OutlinedTextField`.
- `DrugLookupProgress.kt` — renders the live SSE phase: spinner +
  rotating message ("Searching…", "Found Testosterone…", "Generating
  image…").

Navigation routes (type-safe via `@Serializable` per IMPL-AND-00):

```kotlin
package com.gte619n.healthfitness.feature.medical.nav

@Serializable data object MedicationsRoute
@Serializable data object AddMedicationRoute
@Serializable data class MedicationDetailRoute(val medicationId: String)

fun NavGraphBuilder.medicationsGraph(
    onBack: () -> Unit,
    navigateToDetail: (String) -> Unit,
    navigateToAdd: () -> Unit,
) {
    composable<MedicationsRoute> {
        MedicationsListScreen(onAdd = navigateToAdd, onMedicationClick = navigateToDetail)
    }
    composable<AddMedicationRoute> {
        AddMedicationScreen(onDone = onBack)
    }
    composable<MedicationDetailRoute> {
        MedicationDetailScreen(onBack = onBack)
    }
}
```

### `app/` changes

- `app/src/main/java/.../dashboard/TodaysDosesCard.kt` (new):

```kotlin
@HiltViewModel
class TodaysDosesViewModel @Inject constructor(
    private val medications: MedicationRepository,
    private val adherence: AdherenceRepository,
    private val snackbar: SnackbarController,
) : ViewModel() {
    private val _state = MutableStateFlow<TodaysDosesUiState>(TodaysDosesUiState.Loading)
    val state: StateFlow<TodaysDosesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { medications.todaysDoses() }
            .onSuccess { _state.value = TodaysDosesUiState.Ready(it) }
            .onFailure { _state.value = TodaysDosesUiState.Error(it.message ?: "Unknown error") }
    }

    fun toggle(dose: TodaysDose) = viewModelScope.launch {
        // Optimistic
        _state.update { s ->
            if (s !is TodaysDosesUiState.Ready) s
            else s.copy(doses = s.doses.map {
                if (it.medicationId == dose.medicationId && it.window == dose.window)
                    it.copy(taken = !it.taken)
                else it
            })
        }
        runCatching {
            if (dose.taken) adherence.undoDose(dose.medicationId, LocalDate.now(), dose.window)
            else adherence.logDose(dose.medicationId, dose.window)
        }.onFailure {
            snackbar.showError("Could not save — try again")
            refresh()   // revert by re-fetching truth
        }
    }
}

@Composable
fun TodaysDosesCard(viewModel: TodaysDosesViewModel = hiltViewModel(), onSeeAll: () -> Unit) { /* … */ }
```

- `app/src/main/java/.../dashboard/TodayCard.kt`: remove the hardcoded
  medications fixture row; replace with `TodaysDosesCard(onSeeAll = { nav
  → MedicationsRoute })`.
- `app/src/main/java/.../nav/AppNavHost.kt`: add `medicationsGraph(...)`
  to the existing `NavHost` from IMPL-AND-00.
- `app/src/main/java/.../nav/BottomNav.kt`: add `Meds` destination
  (label + pill icon) routing to `MedicationsRoute`.
- `app/src/main/java/.../nav/FoldableSidebar.kt`: add `Meds` row.
- `app/src/main/java/.../log/LogBottomSheet.kt`: add a "Log a dose"
  quick-tile that routes to `MedicationsRoute` with a deep-link argument
  that auto-opens the Today's Doses card section.

### Gradle wiring

- Top-level `settings.gradle.kts` already includes `:feature-medical`; no
  change needed.
- `app/build.gradle.kts`: add `implementation(project(":feature-medical"))`.
- `core-data/build.gradle.kts`: ensure Moshi codegen and Retrofit Moshi
  converter are already declared (landed in IMPL-AND-00).

## Tests

`core-domain/src/test/java/.../medications/`:

- `DoseFormatterTest` — `format(200.0, "mg") == "200 mg"`,
  `format(0.5, "mg") == "0.5 mg"`, `format(12.50, "mg") == "12.5 mg"`.
- `FrequencyFormatterTest` — covers every `FrequencyType` branch.
- `FrequencyConfigTest` — pure: given a `FrequencyConfig` + `List<TimeSlot>`,
  compute the **expected count of doses today** (used by Today's Doses
  derivation when the backend is unreachable; matches what the backend
  returns). DAILY 2x with two slots → 2; PRN → 0; WEEKLY 3x on
  Mon/Wed/Fri, today = Wed → matches slot count; CYCLE with today inside
  off-week → 0.

`core-data/src/test/java/.../medications/`:

- `MedicationMapperTest` — round-trip every enum, null handling for
  `drug`, `customName`, `endDate`.
- `MedicationsApiTest` (MockWebServer) — GET/POST/PUT/DELETE shapes.
- `DrugLookupStreamClientTest` (MockWebServer, chunked
  `text/event-stream` response) — emits `Progress("searching", …)`,
  `Progress("generating_image", …)`, then `Found(drug)`. Asserts the
  flow completes after `complete`. Second test: server emits `not_found`
  → `NotFound` emitted, flow completes.
- `AdherenceRepositoryTest` (MockWebServer) — `logDose` POSTs correct
  JSON body; `undoDose` constructs path with ISO date + window enum
  name.

`feature-medical/src/test/java/.../medications/`:

- `MedicationsViewModelTest` (Turbine, fake `MedicationRepository`) —
  loading → ready, error path, refresh.
- `AddMedicationViewModelTest` (fake DrugRepository emitting a scripted
  flow of `DrugLookupEvent`s) — query change → SSE collected → state
  carries each `Progress`, then `Found(drug)` advances `step` to `FORM`.
  Submit success → invokes `onDone`. Submit failure surfaces `error`.
- `TodaysDosesViewModelTest` — optimistic flip; on failure the row
  reverts and the snackbar controller receives `showError`.
- `MedicationCardTest` (Compose `createComposeRule`) — renders dose +
  frequency line; "Discontinued" overlay present when
  `status == DISCONTINUED`.
- `TodaysDosesCardTest` — snapshot/preview test that the empty state
  renders "No scheduled doses for today.".

## Acceptance criteria

Manual on a real device with a connected account:

1. **Add testosterone via AI lookup.** Open Meds → FAB → type
   "testosterone cypionate" (not in local catalog) → SSE phases display
   live ("Searching…", "Generating image…") → drug appears → fill dose
   200 mg, frequency `WEEKLY` 1x, time `MORNING`, save. New card appears
   in **Current** with the AI-generated vial image (initial placeholder
   may swap in after polling).
2. **Log a dose from the dashboard.** On the dashboard, the Today's
   Doses card shows the testosterone row; tap the checkbox; row flips
   to checked optimistically. Pull-to-refresh keeps it checked. Force-
   quit + reopen → still checked (persisted server-side).
3. **Log a dose with network failure.** Airplane-mode on, tap the
   checkbox; row flips, snackbar shows "Could not save — try again",
   row reverts.
4. **Discontinue.** Tap card → detail → Discontinue → reason
   `SWITCHED`, notes "moved to enanthate" → confirm. Card disappears
   from Current, appears in **History** tab with the reason displayed.
5. **Change dose creates a dated period + history. [PR#8]** From an active
   med, tap **Change dose** → 200 mg → 250 mg, effective today, notes
   "labs". Detail screen's **Dosing history** shows the prior 200 mg period
   closed at today's date and a new 250 mg "Current" period; the dose-change
   history list also shows the `DOSE_CHANGE` entry. The card now reads 250 mg.
6. **Edit start date. [PR#8]** From Edit, change the start date to an
   earlier date and save; the earliest dosing-history period's start shifts
   to match.
7. **Discontinue with an end date. [PR#8]** Discontinue with reason
   `SWITCHED`, notes "moved to enanthate", and an explicit end date →
   confirm. The open dosing period closes at that date; the card disappears
   from **Current** and appears in **History** with the reason displayed.
8. **Resume a discontinued med. [PR#8]** On a discontinued med, tap
   **Resume**, pick a resume date → med returns to **Current**; dosing
   history shows the pause as a gap and a fresh open period from the resume
   date.
9. **Delete.** From detail, Delete → confirm. Card removed from both
   tabs; backend returns 404 on subsequent fetch.
10. **Custom (no-AI) entry.** Open Add → type a garbage string →
    `not_found` arrives → tap "Add manually" → fill custom name / form
    / dose / unit → save. Card appears with the generic form-shaped
    fallback image.
11. **Foldable layout.** On a Pixel Fold inner display, the grid is 3
    columns; on phone portrait, 1 column.

Automated:

- `./gradlew :core-domain:test :core-data:test :feature-medical:test`
  passes (includes Turbine + MockWebServer tests above). Add coverage for
  the **[PR#8]** additions: `DosagePeriod` round-trip in the mapper, the
  `DayOfWeek` lowercase adapter, and `changeDose` / `reactivate` repository
  calls (MockWebServer).
- `./gradlew :app:assembleDebug` succeeds; APK installs.

## Dependencies

- **IMPL-AND-00 (Android foundations)** — required. Provides Hilt
  integration, NavHost, `core-network` with `AuthInterceptor` /
  `TokenAuthenticator` / `BuildConfig.BACKEND_BASE_URL` / OkHttp
  `EventSource` SSE helper, Coil `AsyncImage` wrapper in `core-ui`,
  `SnackbarController`, `ConfirmDialog`, loading / empty / error state
  composables, ViewModel + repository conventions.
- **IMPL-AND-01 (Dashboard live data)** — recommended sequencing
  predecessor. Adds the `TodayCard.kt` wiring this IMPL modifies. Not
  technically blocking (this IMPL can land first and stub the dashboard
  card), but easier to land after.
- **Backend medications (IMPL-05 + PR #8 dosing periods)** — already
  landed on `main`. No backend changes required by this IMPL; Android
  consumes the current shapes (including `dosagePeriods`, `/dosage`,
  `/reactivate`, editable start/end dates, lowercase `DayOfWeek`).

## Open questions resolved before implementation

- **Module choice** — populate the existing empty `feature-medical`
  module rather than introducing `feature-medications`.
- **SSE library** — reuse the OkHttp `EventSource` helper from
  IMPL-AND-00. No additional dependency.
- **Adherence UX** — optimistic with revert + snackbar on failure
  (matches web's `useTransition` pattern).
- **Catalog caching** — none in V1; the catalog endpoint is small and
  hit only when the Add flow opens.
- **Image polling** — 3s interval, 20 attempts max, same as web.
- **Discontinue reason** — modeled as enum, mirrors web exactly.
- **Time windows** — fixed hard-coded boundaries, not locale-dependent,
  per IMPL-05.
- **Dose changes vs. plain edit [PR#8]** — dose is changed only through
  the dedicated Change-dose flow (`/dosage`, dated history); the Edit form
  no longer carries a dose field. Mirrors web post-PR #8.

## Open questions deferred to implementation

- **Deep-link from notifications** — when push-notifications IMPL lands,
  it should route to `MedicationDetailRoute(id)`. Reserve the deep-link
  path now.
- **Adherence over multiple time windows per day** — the backend stores
  `doses: [{ window, takenAt, dose }]` per date document; tapping the
  same med's MORNING checkbox twice in a day should be idempotent (POST
  with the same window key is a no-op). Confirm during implementation;
  if not idempotent, switch to PUT-with-set semantics.
- **History-entry truncation** — detail screen shows full history; if
  long-running protocols generate 50+ entries, consider a "show more"
  affordance. Defer until real data shows it.
- **Editing past dosage periods [PR#8]** — the backend accepts a full
  `dosagePeriods` array on `PUT` for history correction, but Android V1
  only displays the timeline + changes dose forward. Add a period-editor
  if real usage needs back-dated corrections (web defers it too).
- **Wear OS dose-logging** — out of scope for this IMPL but a likely
  follow-up; the `LogDoseUseCase` shape established here should be
  callable from a Wear `Tile` later.
