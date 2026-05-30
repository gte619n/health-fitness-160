# IMPL-AND-05: Android body composition & DEXA

## Goal

Build the Android body composition surface — overview screen with hero
metrics + 90-day weight trend + DEXA scan grid, DEXA scan detail with
fully click-to-edit regional values, and the multipart-SSE DEXA PDF upload
flow. Backend already exposes everything via IMPL-04 (Google Health body
composition) and the `/api/me/dexa/**` endpoints; this spec wires the
Android client to that existing surface and reuses the already-implemented
`WeightChart.kt` Canvas composable by feeding it live data.

Closes Phase 5 of the Android ↔ Web parity roadmap
(`docs/plans/android-web-parity-roadmap.md`, §2.4).

## Scope

In scope:

- New `feature-body-composition/` Gradle module added to
  `settings.gradle.kts` and to `app/build.gradle.kts`.
- `core-domain/body-composition/` — pure Kotlin models + repository
  interfaces (`BodyCompositionSnapshot`, `BodyCompositionPoint`,
  `DexaScan`, `DexaScanSummary`, `DexaRegion`, `DexaRegionKey`,
  `BodyCompositionRepository`, `DexaScanRepository`).
- `core-data/body-composition/` — Retrofit services, Moshi adapters,
  repository implementations, reuse of the `MultipartSseClient` introduced
  in IMPL-AND-04.
- Three Compose screens with `@HiltViewModel` view models:
  `BodyCompositionScreen`, `DexaScanDetailScreen`, `UploadDexaScreen`.
- Reuse of the existing `WeightChart` Canvas composable from
  `app/src/main/java/com/gte619n/healthfitness/mobile/dashboard/WeightChart.kt`
  — promote it into `core-ui` so both the dashboard hero (IMPL-AND-01) and
  this feature module read the same composable.
- Click-to-edit numeric cells everywhere on the DEXA detail using
  `core-ui`'s `EditableNumber` composable (from IMPL-AND-00), with the
  PATCH semantics — optimistic write, revert on failure, snackbar feedback.
- DEXA PDF upload via `ActivityResultContracts.GetContent("application/pdf")`
  → multipart `POST /api/me/dexa/scans` → SSE phase stream
  (`uploading` → `extracting` → `saving` → `complete` | `failed`).
- DEXA PDF view via system intent (`ACTION_VIEW` on the downloaded PDF in
  cache, with `FileProvider`) — same choice as IMPL-AND-04 for blood reports.
- DEXA delete with confirm dialog and snackbar success.
- Hook the body composition destination into the bottom-nav (phone) and
  foldable sidebar landed by IMPL-AND-00.
- Wire the dashboard body-composition hero (already laid out as fixtures
  in IMPL-AND-01) to the same `BodyCompositionRepository` declared here.

Out of scope (deferred):

- **Manual weight entry on Android.** Web has no such surface either
  (Google Health is the source of weight readings). Reconsider when a
  user-facing direct-entry endpoint exists on the backend.
- **Photo progress tracking** (front/side/back monthly photos). Not on web,
  no backend support.
- **Body composition CSV export.** Not on web.
- **Local Room caching of body composition or DEXA data.** Backend is the
  source of truth; this spec keeps reads online-only. Add Room caching when
  the "offline read" non-goal flips per ADR.
- **Editing weight / body-fat values** synced from Google Health. Web treats
  those rows as read-only; Android matches.
- **Wear OS body composition surface.** Phone-only for now.
- **In-app PDF viewer.** Same decision as IMPL-AND-04 — system intent.

## Decisions

| Topic | Decision |
|---|---|
| Module name | `feature-body-composition/`. Sibling of `feature-medical` and `feature-workouts`. Body composition is its own bounded user-visible feature with three screens and its own VMs, so a dedicated module keeps it from polluting `feature-medical` (which is reserved for blood/conditions/allergies in IMPL-AND-04). |
| WeightChart ownership | Move `WeightChart.kt` from `app/.../dashboard/WeightChart.kt` into `core-ui/.../chart/WeightChart.kt` so both the dashboard hero and the body composition overview share one implementation. Signature changes to take a `WeightSeries` value object (real data) instead of reading `DashboardFixtures`. The dashboard call site swaps to the new signature in the same PR. |
| Editable cell composable | New `EditableNumberCell` in `feature-body-composition` wraps `core-ui`'s `EditableNumber` with the PATCH wiring: tap-to-edit, validate, optimistic save, revert on failure with snackbar. The bare `EditableNumber` stays presentation-only in `core-ui`. |
| PATCH endpoint shape | The backend exposes `PATCH /api/me/dexa/scans/{scanId}/field` with body `{ path: string, value: number \| null }` (existing endpoint from the web spec — see `backend/app/.../DexaScanController.java`). Android sends the same `path` strings (`totalMassLb`, `trunk.leanTissueLb`, `armsLeft.fatTissueLb`, etc.). No new endpoint is introduced. |
| Upload transport | Multipart `POST /api/me/dexa/scans` (`Content-Type: multipart/form-data`, field `file`) producing `text/event-stream`. Reuses the `MultipartSseClient` introduced in IMPL-AND-04 (blood PDF upload). If IMPL-AND-04 hasn't landed yet, this spec declares `MultipartSseClient` in `core-network` and IMPL-AND-04 reuses it. Either ordering works; see *Module ordering*. |
| Upload concurrency | Single-file upload (web supports multi-file drag-drop; mobile picker is single by default). A second-file flow stays a follow-up if requested. |
| PDF size limit | Match backend: 25 MB. Client validates pre-upload via `ContentResolver.openFileDescriptor` + `statSize` and surfaces a snackbar before the network call. |
| PDF viewer | System intent (`Intent.ACTION_VIEW` over a `FileProvider` URI in `cache/dexa-pdfs/`). Same call as IMPL-AND-04. PDF bytes pulled from `GET /api/me/dexa/scans/{scanId}/pdf`. |
| Delete confirm | Compose `AlertDialog` from `core-ui` — same primitive used by IMPL-AND-04 for delete-report flows. |
| Snackbar surface | `SnackbarController` from `core-ui` (IMPL-AND-00). Success: "Scan deleted". Failure: error text from backend or generic "Couldn't save". |
| Units | Backend stores DEXA masses in **lbs** (raw report unit, no conversion). Backend stores Google Health weight in **kg**. The hero card shows weight in lb (`* KG_TO_LB` constant in `core-domain/body-composition/Units.kt`, mirrored from `web/app/me/body-composition/page.tsx`). Lean mass is also displayed in lb. |
| Delta chips | Two chips next to the weight hero: 7-day and 90-day deltas, computed client-side from the same `BodyCompositionPoint` list that feeds the chart. Negative delta is green, positive is amber, zero is neutral. Matches the dashboard hero from IMPL-AND-01. |
| Dashboard hero ↔ feature module | The dashboard's body-composition hero (already wired in IMPL-AND-01) consumes the **same** `BodyCompositionRepository` and `BodyCompositionSnapshot` defined here. IMPL-AND-01 declared placeholder DTOs; this spec is the canonical home and IMPL-AND-01 swaps to import from `core-domain/body-composition`. |
| Module ordering | Hard deps: IMPL-AND-00 (Hilt, navigation, `core-network`, `core-ui` with `EditableNumber`, `SnackbarController`). Soft dep: IMPL-AND-04 (introduces `MultipartSseClient`). If IMPL-AND-04 ships first, reuse it. If IMPL-AND-05 ships first, declare `MultipartSseClient` in `core-network` and let IMPL-AND-04 reuse it then. |

## Per-module deliverables

### `core-domain/body-composition/`

Pure Kotlin, no Android/Compose imports. All numeric fields nullable
because vendors omit different subsets of the DEXA report.

```kotlin
// core-domain/src/main/java/com/gte619n/healthfitness/core/bodycomposition/Models.kt
package com.gte619n.healthfitness.core.bodycomposition

import java.time.Instant
import java.time.LocalDate

enum class BodyCompositionMetric { WEIGHT_KG, BODY_FAT_PERCENT, LEAN_MASS_KG, BMI }

data class BodyCompositionPoint(
    val recordId: String,
    val metric: BodyCompositionMetric,
    val value: Double,
    val sampleTime: Instant,
    val sourcePlatform: String?,
    val recordingMethod: String?,
)

/** Snapshot consumed by the dashboard hero and the overview screen. */
data class BodyCompositionSnapshot(
    val latestWeightKg: Double?,
    val latestBodyFatPercent: Double?,
    val latestLeanMassKg: Double?,
    val latestBmi: Double?,
    val latestSampleTime: Instant?,
    val sevenDayDeltaKg: Double?,
    val ninetyDayDeltaKg: Double?,
    val series90d: List<BodyCompositionPoint>, // metric=WEIGHT_KG, oldest-first
)

enum class DexaRegionKey {
    TRUNK, ANDROID, GYNOID,
    ARMS_TOTAL, ARMS_RIGHT, ARMS_LEFT,
    LEGS_TOTAL, LEGS_RIGHT, LEGS_LEFT;

    /** PATCH path prefix, matches the backend's `UpdateFieldRequest.path`. */
    fun pathKey(): String = when (this) {
        TRUNK -> "trunk"
        ANDROID -> "android"
        GYNOID -> "gynoid"
        ARMS_TOTAL -> "armsTotal"
        ARMS_RIGHT -> "armsRight"
        ARMS_LEFT -> "armsLeft"
        LEGS_TOTAL -> "legsTotal"
        LEGS_RIGHT -> "legsRight"
        LEGS_LEFT -> "legsLeft"
    }
}

data class DexaRegion(
    val totalMassLb: Double?,
    val leanTissueLb: Double?,
    val fatTissueLb: Double?,
    val regionFatPercent: Double?,
)

/** Grid cell summary used in the overview's "DEXA scans" section. */
data class DexaScanSummary(
    val scanId: String,
    val measuredOn: LocalDate?,
    val sourceFacility: String?,
    val totalMassLb: Double?,
    val totalBodyFatPercent: Double?,
)

data class DexaScan(
    val scanId: String,
    val measuredOn: LocalDate?,
    val sourceFacility: String?,
    val totalMassLb: Double?,
    val leanTissueLb: Double?,
    val fatTissueLb: Double?,
    val totalBodyFatPercent: Double?,
    val visceralFatLb: Double?,
    val androidGynoidRatio: Double?,
    val trunk: DexaRegion?,
    val android: DexaRegion?,
    val gynoid: DexaRegion?,
    val armsTotal: DexaRegion?,
    val armsRight: DexaRegion?,
    val armsLeft: DexaRegion?,
    val legsTotal: DexaRegion?,
    val legsRight: DexaRegion?,
    val legsLeft: DexaRegion?,
    val bmdTScore: Double?,
    val bmdZScore: Double?,
    val restingMetabolicRateKcal: Int?,
)
```

```kotlin
// core-domain/src/main/java/com/gte619n/healthfitness/core/bodycomposition/Units.kt
package com.gte619n.healthfitness.core.bodycomposition

const val KG_TO_LB: Double = 2.20462
fun kgToLb(kg: Double?): Double? = kg?.let { it * KG_TO_LB }
```

```kotlin
// core-domain/.../BodyCompositionRepository.kt
package com.gte619n.healthfitness.core.bodycomposition

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BodyCompositionRepository {
    /** Latest snapshot + 90d weight series. Hot-replays on refresh. */
    fun observeSnapshot(): Flow<BodyCompositionSnapshot>
    suspend fun refresh()
    suspend fun pointsInRange(
        metric: BodyCompositionMetric,
        from: Instant,
        to: Instant,
    ): List<BodyCompositionPoint>
}
```

```kotlin
// core-domain/.../DexaScanRepository.kt
package com.gte619n.healthfitness.core.bodycomposition

import kotlinx.coroutines.flow.Flow

interface DexaScanRepository {
    fun observeScans(): Flow<List<DexaScanSummary>>
    suspend fun refreshScans()
    suspend fun getScan(scanId: String): DexaScan
    suspend fun deleteScan(scanId: String)

    /** Pull PDF bytes from the backend. */
    suspend fun downloadPdf(scanId: String): ByteArray

    /**
     * PATCH a single numeric field. `path` matches the web client's path
     * convention (e.g. "totalMassLb", "trunk.leanTissueLb"). Returns the
     * updated scan. Throws on backend error so the caller can revert
     * optimistic state.
     */
    suspend fun patchField(scanId: String, path: String, value: Double?): DexaScan

    /** Multipart + SSE upload. See UploadDexaViewModel for consumer. */
    fun uploadPdf(fileName: String, bytes: ByteArray): Flow<DexaUploadEvent>
}

sealed interface DexaUploadEvent {
    /** "uploading" | "extracting" | "saving" — verbatim from backend. */
    data class Phase(val phase: String, val message: String?) : DexaUploadEvent
    data class Complete(val scan: DexaScan) : DexaUploadEvent
    data class Failed(val error: String) : DexaUploadEvent
}
```

### `core-data/body-composition/`

```kotlin
// core-data/.../api/BodyCompositionApi.kt
package com.gte619n.healthfitness.data.bodycomposition.api

import com.gte619n.healthfitness.data.bodycomposition.dto.BodyCompositionReadingDto
import retrofit2.http.GET
import retrofit2.http.Query

interface BodyCompositionApi {
    @GET("api/me/body-composition")
    suspend fun list(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("metric") metric: String? = null,
    ): List<BodyCompositionReadingDto>
}
```

```kotlin
// core-data/.../api/DexaScanApi.kt
package com.gte619n.healthfitness.data.bodycomposition.api

import com.gte619n.healthfitness.data.bodycomposition.dto.DexaScanDto
import com.gte619n.healthfitness.data.bodycomposition.dto.DexaScanSummaryDto
import com.gte619n.healthfitness.data.bodycomposition.dto.PatchFieldRequest
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Streaming

interface DexaScanApi {
    @GET("api/me/dexa/scans")
    suspend fun list(): List<DexaScanSummaryDto>

    @GET("api/me/dexa/scans/{scanId}")
    suspend fun get(@Path("scanId") scanId: String): DexaScanDto

    @PATCH("api/me/dexa/scans/{scanId}/field")
    suspend fun patchField(
        @Path("scanId") scanId: String,
        @Body body: PatchFieldRequest,
    ): DexaScanDto

    @DELETE("api/me/dexa/scans/{scanId}")
    suspend fun delete(@Path("scanId") scanId: String)

    @Streaming
    @GET("api/me/dexa/scans/{scanId}/pdf")
    suspend fun downloadPdf(@Path("scanId") scanId: String): ResponseBody
}
```

DTOs (`BodyCompositionReadingDto`, `DexaRegionDto`, `DexaScanDto`,
`DexaScanSummaryDto`, `PatchFieldRequest`) mirror the JSON shapes from
`backend/api/.../bodycomposition/BodyCompositionResponse.java` and
`backend/api/.../dexa/DexaScanResponse.java`. Moshi adapters generated via
`@JsonClass(generateAdapter = true)`. `LocalDate` and `Instant` adapters
already wired in `core-network` per IMPL-AND-00.

Repository impls:

```kotlin
// core-data/.../BodyCompositionRepositoryImpl.kt
@Singleton
class BodyCompositionRepositoryImpl @Inject constructor(
    private val api: BodyCompositionApi,
) : BodyCompositionRepository {
    private val snapshot = MutableSharedFlow<BodyCompositionSnapshot>(replay = 1)

    override fun observeSnapshot(): Flow<BodyCompositionSnapshot> = snapshot.asSharedFlow()

    override suspend fun refresh() {
        val readings = api.list().map { it.toDomain() }
        snapshot.emit(buildSnapshot(readings))
    }

    override suspend fun pointsInRange(
        metric: BodyCompositionMetric,
        from: Instant,
        to: Instant,
    ): List<BodyCompositionPoint> =
        api.list(from.toString(), to.toString(), metric.name).map { it.toDomain() }

    /** Aggregates raw points into the snapshot the UI consumes. */
    private fun buildSnapshot(points: List<BodyCompositionPoint>): BodyCompositionSnapshot { /* … */ }
}
```

```kotlin
// core-data/.../DexaScanRepositoryImpl.kt
@Singleton
class DexaScanRepositoryImpl @Inject constructor(
    private val api: DexaScanApi,
    private val multipartSseClient: MultipartSseClient,
    private val baseUrl: BackendBaseUrl, // typed wrapper around BuildConfig.BACKEND_BASE_URL
) : DexaScanRepository {
    private val scans = MutableSharedFlow<List<DexaScanSummary>>(replay = 1)

    override fun observeScans(): Flow<List<DexaScanSummary>> = scans.asSharedFlow()

    override suspend fun refreshScans() {
        scans.emit(api.list().map { it.toSummary() })
    }

    override suspend fun getScan(scanId: String): DexaScan = api.get(scanId).toDomain()

    override suspend fun deleteScan(scanId: String) {
        api.delete(scanId); refreshScans()
    }

    override suspend fun patchField(scanId: String, path: String, value: Double?): DexaScan =
        api.patchField(scanId, PatchFieldRequest(path, value)).toDomain()

    override suspend fun downloadPdf(scanId: String): ByteArray =
        api.downloadPdf(scanId).use { it.bytes() }

    override fun uploadPdf(fileName: String, bytes: ByteArray): Flow<DexaUploadEvent> =
        multipartSseClient.post(
            url = baseUrl.value + "api/me/dexa/scans",
            fileFieldName = "file",
            fileName = fileName,
            mediaType = "application/pdf",
            bytes = bytes,
        ).map { event ->
            // Each SSE event is `data:` line(s) of JSON: { phase, message?, scan?, error? }
            parseDexaEvent(event)
        }
}
```

Hilt module:

```kotlin
// core-data/.../di/BodyCompositionModule.kt
@Module @InstallIn(SingletonComponent::class)
abstract class BodyCompositionModule {
    @Binds abstract fun bindBodyComp(impl: BodyCompositionRepositoryImpl): BodyCompositionRepository
    @Binds abstract fun bindDexa(impl: DexaScanRepositoryImpl): DexaScanRepository

    companion object {
        @Provides fun bodyApi(retrofit: Retrofit): BodyCompositionApi =
            retrofit.create(BodyCompositionApi::class.java)
        @Provides fun dexaApi(retrofit: Retrofit): DexaScanApi =
            retrofit.create(DexaScanApi::class.java)
    }
}
```

### `feature-body-composition/`

```kotlin
// feature-body-composition/.../nav/BodyCompositionRoutes.kt
package com.gte619n.healthfitness.feature.bodycomposition.nav

import kotlinx.serialization.Serializable

@Serializable data object BodyCompositionRoute
@Serializable data class DexaScanDetailRoute(val scanId: String)
@Serializable data object UploadDexaRoute

fun NavGraphBuilder.bodyCompositionGraph(navController: NavController) {
    composable<BodyCompositionRoute> { BodyCompositionScreen(navController) }
    composable<DexaScanDetailRoute> { backStackEntry ->
        val route: DexaScanDetailRoute = backStackEntry.toRoute()
        DexaScanDetailScreen(scanId = route.scanId, navController = navController)
    }
    composable<UploadDexaRoute> { UploadDexaScreen(navController) }
}
```

```kotlin
// feature-body-composition/.../overview/BodyCompositionViewModel.kt
@HiltViewModel
class BodyCompositionViewModel @Inject constructor(
    private val bodyRepo: BodyCompositionRepository,
    private val dexaRepo: DexaScanRepository,
) : ViewModel() {

    data class UiState(
        val snapshot: BodyCompositionSnapshot? = null,
        val dexaScans: List<DexaScanSummary> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                bodyRepo.observeSnapshot(),
                dexaRepo.observeScans(),
            ) { snap, scans -> snap to scans }
                .collect { (snap, scans) ->
                    _state.update { it.copy(snapshot = snap, dexaScans = scans, loading = false) }
                }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching {
            coroutineScope {
                launch { bodyRepo.refresh() }
                launch { dexaRepo.refreshScans() }
            }
        }.onFailure { e ->
            _state.update { it.copy(loading = false, error = e.message ?: "Could not load") }
        }
    }
}
```

```kotlin
// feature-body-composition/.../detail/DexaScanDetailViewModel.kt
@HiltViewModel
class DexaScanDetailViewModel @Inject constructor(
    private val repo: DexaScanRepository,
    private val snackbar: SnackbarController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val scan: DexaScan? = null,
        val loading: Boolean = true,
        val deleting: Boolean = false,
        val error: String? = null,
    )

    private val scanId: String = savedStateHandle.toRoute<DexaScanDetailRoute>().scanId
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.getScan(scanId) }
            .onSuccess { scan -> _state.update { it.copy(scan = scan, loading = false) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    /**
     * Optimistic patch. Mutates local state immediately, fires PATCH in
     * background, reverts + snackbar on failure.
     */
    fun patchField(path: String, value: Double?) = viewModelScope.launch {
        val before = _state.value.scan ?: return@launch
        val optimistic = before.withFieldPatched(path, value)
        _state.update { it.copy(scan = optimistic) }
        runCatching { repo.patchField(scanId, path, value) }
            .onSuccess { updated -> _state.update { it.copy(scan = updated) } }
            .onFailure { e ->
                _state.update { it.copy(scan = before) }
                snackbar.show("Couldn't save: ${e.message ?: "error"}")
            }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(deleting = true) }
        runCatching { repo.deleteScan(scanId) }
            .onSuccess { snackbar.show("Scan deleted"); onDone() }
            .onFailure { e ->
                _state.update { it.copy(deleting = false) }
                snackbar.show("Couldn't delete: ${e.message ?: "error"}")
            }
    }
}

/** Extension that mirrors the backend path-string convention. */
private fun DexaScan.withFieldPatched(path: String, value: Double?): DexaScan { /* … */ }
```

```kotlin
// feature-body-composition/.../upload/UploadDexaViewModel.kt
@HiltViewModel
class UploadDexaViewModel @Inject constructor(
    private val repo: DexaScanRepository,
    private val snackbar: SnackbarController,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class InProgress(val phase: String, val message: String?) : UiState
        data class Complete(val scanId: String) : UiState
        data class Failed(val error: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun upload(fileName: String, bytes: ByteArray) {
        if (bytes.size > MAX_PDF_BYTES) {
            _state.value = UiState.Failed("PDF exceeds 25 MB limit")
            return
        }
        viewModelScope.launch {
            _state.value = UiState.InProgress("uploading", "Saving your PDF")
            repo.uploadPdf(fileName, bytes)
                .catch { e -> _state.value = UiState.Failed(e.message ?: "Upload failed") }
                .collect { event ->
                    when (event) {
                        is DexaUploadEvent.Phase ->
                            _state.value = UiState.InProgress(event.phase, event.message)
                        is DexaUploadEvent.Complete ->
                            _state.value = UiState.Complete(event.scan.scanId)
                        is DexaUploadEvent.Failed ->
                            _state.value = UiState.Failed(event.error)
                    }
                }
        }
    }

    companion object { const val MAX_PDF_BYTES = 25L * 1024 * 1024 }
}
```

Composables (signatures only):

```kotlin
@Composable fun BodyCompositionScreen(navController: NavController)
@Composable fun DexaScanDetailScreen(scanId: String, navController: NavController)
@Composable fun UploadDexaScreen(navController: NavController)

// Sub-components
@Composable fun BodyCompositionHero(snapshot: BodyCompositionSnapshot)
@Composable fun DexaScanCard(summary: DexaScanSummary, onClick: () -> Unit)
@Composable fun DexaRegionGrid(
    scan: DexaScan,
    onPatch: (path: String, value: Double?) -> Unit,
)
@Composable fun EditableNumberCell(
    value: Double?,
    fractionDigits: Int = 1,
    unit: String? = null,
    onSave: (Double?) -> Unit,
)
```

The PDF view action launches a file-provider intent:

```kotlin
// feature-body-composition/.../detail/PdfIntent.kt
suspend fun viewDexaPdf(context: Context, repo: DexaScanRepository, scanId: String) {
    val bytes = repo.downloadPdf(scanId)
    val cacheDir = File(context.cacheDir, "dexa-pdfs").apply { mkdirs() }
    val file = File(cacheDir, "dexa-$scanId.pdf").apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
```

`FileProvider` and the `cache/dexa-pdfs/` paths element are landed once by
IMPL-AND-04 (blood reports) and reused here. If IMPL-AND-05 ships first,
both the `<provider>` block in `AndroidManifest.xml` and
`res/xml/file_paths.xml` land here.

### `app/`

- `app/src/main/java/com/gte619n/healthfitness/mobile/MainNav.kt` —
  add `bodyCompositionGraph(navController)` into the existing `NavHost`.
- Bottom-nav (phone) and foldable-sidebar (foldable) gain a "Body"
  destination dispatching to `BodyCompositionRoute`. Icon:
  `Icons.Outlined.MonitorWeight`.
- `WeightChart.kt` moves from `mobile/dashboard/` to
  `core-ui/.../chart/WeightChart.kt`; dashboard imports the new location.
- The dashboard body-composition hero (laid out by IMPL-AND-01) reads
  `BodyCompositionRepository.observeSnapshot()` directly and tap-routes to
  `BodyCompositionRoute`.

## Gradle

`settings.gradle.kts` — add:

```kotlin
include(":feature-body-composition")
```

`feature-body-composition/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.gte619n.healthfitness.feature.bodycomposition"
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

`app/build.gradle.kts` — add
`implementation(project(":feature-body-composition"))`.

## Tests

`feature-body-composition/src/test/java/.../`:

- `BodyCompositionViewModelTest` (Turbine, fake `BodyCompositionRepository`
  + fake `DexaScanRepository`):
  - Emits `loading=true → loading=false` with snapshot populated after
    initial refresh.
  - `refresh()` re-pulls and re-emits.
  - Repository error surfaces as `state.error`.
- `DexaScanDetailViewModelTest`:
  - `load()` populates the scan; `error` set on failure.
  - `patchField` applies optimistic update synchronously, then settles to
    server response on success.
  - `patchField` failure reverts the optimistic update and fires the
    snackbar (assert `SnackbarController` was called with "Couldn't
    save…").
  - `delete()` success calls `onDone` callback and fires "Scan deleted".
- `UploadDexaViewModelTest`:
  - File above 25 MB short-circuits to `Failed` without a network call.
  - Chunked SSE phases produce the matching `InProgress` states in order.
  - `DexaUploadEvent.Complete` transitions to `UiState.Complete(scanId)`.
  - `DexaUploadEvent.Failed` transitions to `UiState.Failed(error)`.
- `DexaRegionGridTest` (Paparazzi / Compose preview snapshot — preview
  baseline only, no behavior):
  - Renders the full grid with all nine `DexaRegionKey` rows.
  - Renders with sparse data (some regions null).
- `EditableNumberCellTest` (Compose UI test):
  - Tap enters edit mode; Enter calls `onSave` with the parsed Double.
  - Empty string saves `null`.
  - Invalid input shows red border, does not call `onSave`.

`core-data/src/test/java/.../`:

- `DexaScanRepositoryImplTest` (MockWebServer):
  - `list()` parses the JSON shape returned by the backend's
    `DexaScanResponse`.
  - `patchField` POSTs the right body and parses the updated scan.
  - `downloadPdf` returns the raw byte array.
- `BodyCompositionRepositoryImplTest`:
  - Snapshot computation: latest weight, 7d / 90d deltas, sparse data
    edge cases (no 7d window → null delta).

## Acceptance criteria

Manual (against the deployed staging backend, using the test user that
has body composition data and DEXA scans):

1. From the dashboard, the body-composition hero shows the real latest
   weight, body fat %, and 7d delta (no fixtures).
2. Tap the hero → navigate to `BodyCompositionRoute`. The overview shows:
   - Hero with weight + body fat % + lean mass + 7d/90d delta chips.
   - 90-day weight chart rendered by the shared `WeightChart` composable.
   - A grid of DEXA scan cards (one per existing scan).
3. Tap "Upload DEXA scan" → system file picker opens, filtered to
   `application/pdf`.
4. Pick one of the fixtures from `docs/test_reports/dexa_scans/`.
   Status display cycles through "Uploading" → "Extracting" →
   "Saving" → completes. App navigates to
   `DexaScanDetailRoute(newScanId)`.
5. On the detail screen, tap any numeric value (e.g. `armsLeft.fatTissueLb`).
   Cell enters edit mode. Type a new number, press Enter / done.
   Cell shows the new value within ~300 ms; on backend failure the cell
   reverts and a snackbar appears.
6. Tap "View PDF" → system PDF viewer launches with the uploaded report.
7. Tap "Delete scan" → confirm dialog → snackbar "Scan deleted" → back
   stack returns to the overview, the scan card is gone.
8. `./gradlew :feature-body-composition:test :core-data:test` is green.
9. App launches with no warnings about `WeightChart` being defined in two
   places (the old `app/.../dashboard/WeightChart.kt` is gone).

## Open questions

Resolved:

- **Module name.** `feature-body-composition` — dedicated module per the
  Decisions table. Avoids overloading `feature-medical`.
- **`WeightChart` ownership.** Promoted into `core-ui` and shared between
  the dashboard hero and this screen.
- **PATCH endpoint.** Reuse existing `/api/me/dexa/scans/{scanId}/field`.
  No new endpoint.
- **PDF viewer.** System intent, same as IMPL-AND-04.
- **Delta chip semantics.** Computed client-side from
  `BodyCompositionSnapshot.series90d`, matching the dashboard hero in
  IMPL-AND-01.
- **Multi-file upload.** No — single-file picker. Web's multi-file
  drag-drop has no mobile analogue.
- **Inline-edit guard rails on Google Health rows.** Out of scope. The
  overview table only links DEXA rows to the detail screen; weight/body-fat
  rows from Google Health stay read-only display, matching web.

Deferred:

- **Manual weight entry on Android.** Backend has no endpoint; web
  doesn't support it. Revisit when a backend `POST /api/me/body-composition`
  exists.
- **Photo progress tracking.** Not on web, no backend support.
- **Offline read of DEXA scans / body composition.** Room caching deferred
  until a session-wide "offline read" ADR exists.
- **Editing of Google Health-sourced rows.** Reads are immutable from the
  app today; only DEXA fields are editable.
- **Wear OS body composition surface.** Phone-only for now; revisit during
  Phase 8 of the parity roadmap.
- **Backfill / re-extract action on the detail screen.** If extraction
  produced incomplete fields, the user must re-upload — there's no
  "re-extract" button on web either.

## Module ordering note

This spec lists IMPL-AND-00 as a hard dependency (Hilt, navigation,
`core-network`, `core-ui` with `EditableNumber` and `SnackbarController`).
`MultipartSseClient` is shared with IMPL-AND-04; either spec may land it
in `core-network` first. The other reuses it. Both specs reference the
same class FQCN
(`com.gte619n.healthfitness.network.multipart.MultipartSseClient`) so
ordering doesn't change the import sites.
