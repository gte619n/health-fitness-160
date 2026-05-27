# IMPL-AND-04: Android blood testing

## Goal

Bring the web client's blood-testing surface to Android: a Blood overview
that lists recent lab reports and tracked markers (latest value, reference-
range bar, 12-month sparkline), a marker-detail screen with a full
history chart, a manual reading entry form, a lab-PDF upload flow that
consumes the backend's multipart→SSE extraction stream, and a report
detail with extracted markers + a PDF viewer. Lives in a new
`feature-blood/` module and reuses the dashboard's `RangeBar` and
`Sparkline` primitives.

The dashboard's `BloodPanel` (already landed in IMPL-AND-01 wiring) feeds
from the same `/api/me/blood` + `/api/me/blood/reports` shape — this IMPL
introduces the repository layer that both the dashboard and the new
feature module consume.

## Scope

In scope:

- New `feature-blood/` module (added to `settings.gradle.kts`)
- Five screens: Blood overview, Marker detail, Add reading (modal/sheet),
  Upload lab PDF (modal/sheet with live SSE phase), Report detail
- `core-domain/blood/` models + repositories (mirroring backend
  `BloodMarker`, `BloodReading`, `BloodTestReport`, `ExtractedMarker`)
- `core-data/blood/` Retrofit services + Moshi adapters + impls
- `core-network/` extension: **`MultipartSseClient`** helper combining an
  OkHttp `MultipartBody` request with EventSource-style chunked
  `text/event-stream` consumption. Reusable in IMPL-AND-05 (DEXA upload)
- File picker for PDFs via
  `ActivityResultContracts.GetContent("application/pdf")`
- PDF viewer: handed off via `Intent.ACTION_VIEW` (decision below)
- Navigation routes: `blood`, `blood/markers/{markerKey}`,
  `blood/reports/{reportId}`; bottom-nav (phone) + foldable sidebar (app)
  destination wiring
- Dashboard wiring: the `BloodPanel` composable reads from
  `BloodMarkerRepository.observeLatestByMarker()` instead of fixtures

Out of scope (deferred):

- Report-PDF *generation* on Android (PDF only viewed, never authored)
- Marker ↔ medication correlation overlays (lands alongside IMPL-AND-03
  follow-ups)
- Inline editing of extracted marker values on the report detail screen
  (web has a `PATCH /reports/{id}/field`; deferred until a real edit
  use-case surfaces on mobile)
- Offline-first writes — the manual reading form requires network. Caching
  reads in Room is also deferred; the screen relies on the in-flight
  `Flow` from Retrofit + a small in-memory cache only.
- Marker correlation linking to medications/protocols
- Admin-side marker catalog edits

## Decisions

| Topic | Decision |
|---|---|
| Module path | New top-level module `feature-blood/`. `feature-medical/` stays empty (placeholder for future medical-record domains: vaccinations, conditions, allergies). Blood gets its own surface because the flows (upload → SSE extraction, marker history) are large enough to dominate any shared module. |
| PDF viewer | **System intent.** Tap "View PDF" on a report → `Intent.ACTION_VIEW` with the downloaded `application/pdf` cached under `cacheDir/blood/{reportId}.pdf`, exposed via the existing `androidx.core.content.FileProvider` authority `com.gte619n.healthfitness.fileprovider`. Rationale: `androidx.pdf:pdf-viewer-fragment` is still in alpha (1.0.0-alpha02 as of 2026-05) and pulls a Fragment dependency the rest of the app avoids. Revisit when stable. |
| Multipart + SSE | One custom helper, `MultipartSseClient`, in `core-network/`. Builds the `MultipartBody` directly (no Retrofit `@Multipart` interface), calls `OkHttpClient.newCall(...).execute()` on a coroutine `withContext(Dispatchers.IO)`, then reads the response `BufferedSource` line-by-line into a `Flow<SseEvent>`. Reusable from IMPL-AND-05 DEXA upload. See [MultipartSseClient](#multipartsseclient) below. |
| Marker enum | Mirror backend's `BloodMarker` exactly: `TOTAL_CHOLESTEROL, LDL, HDL, TRIGLYCERIDES, APO_B, HBA1C, FASTING_GLUCOSE, HS_CRP`. Display labels and target descriptions live in `core-domain/blood/MarkerCatalog.kt` (parity with web's `MARKER_LABELS` + `MARKER_INFO`). |
| Reference ranges | Backend ships them on each `BloodReadingResponse.reference` (unit, orientation, goodThreshold, displayMin, displayMax). Android takes them as authoritative — no client-side guessing. |
| Marker name → enum mapping | Server-side. The web's `MARKER_PATTERNS` regex table is **not** duplicated in Kotlin; the backend already canonicalizes extracted markers via `ExtractedMarker.name`. If a report row's name doesn't match a known `BloodMarker`, the Android UI renders it under "Other markers" rather than discarding. |
| State exposure | Each ViewModel exposes a single `StateFlow<XxxUiState>`. UiState is a sealed interface with `Loading`, `Ready(data)`, `Error(message, retry)`. Convention established in IMPL-AND-00. |
| File picker location | UI layer (`feature-blood/UploadLabReportScreen`), not `core-data`. `core-data` only sees `ByteArray + filename` so it stays free of Android `Uri` types. |
| Dashboard ↔ Blood sharing | Both consume `BloodReadingRepository` + `BloodTestReportRepository` from `core-data`. The dashboard's existing `BloodPanel` is refactored to read live data through a new `DashboardBloodViewModel`; this IMPL ships that refactor (small — 1 file) so the dashboard isn't blocked on a separate ticket. |
| Coil for nothing | No images in this feature. Coil dep is already on the classpath from IMPL-AND-00; no change. |
| Network errors | 401/403 are handled centrally by `TokenAuthenticator` (IMPL-AND-00). Other failures surface as `Error(message, retry)` in UiState; the screen renders the IMPL-AND-00 `ErrorState` composable with a Retry button. |

## Per-module deliverables

### `core-domain/blood/` — pure-Kotlin models + repository interfaces

```kotlin
// core-domain/src/main/java/com/gte619n/healthfitness/core/domain/blood/BloodMarker.kt
package com.gte619n.healthfitness.core.domain.blood

enum class BloodMarker {
    TOTAL_CHOLESTEROL,
    LDL,
    HDL,
    TRIGLYCERIDES,
    APO_B,
    HBA1C,
    FASTING_GLUCOSE,
    HS_CRP,
}

data class ReferenceRange(
    val unit: String,
    val orientation: Orientation,
    val goodThreshold: Double,
    val displayMin: Double,
    val displayMax: Double,
) {
    enum class Orientation { LOWER_IS_BETTER, HIGHER_IS_BETTER }
}

data class BloodReading(
    val readingId: String,
    val marker: BloodMarker,
    val value: Double,
    val unit: String,
    val sampleDate: LocalDate,
    val labSource: String?,
    val notes: String?,
    val reference: ReferenceRange,
)

data class ExtractedMarker(
    val name: String,           // canonical e.g. "LDL"; may not map to enum
    val value: Double?,
    val unit: String?,
    val refRangeLow: Double?,
    val refRangeHigh: Double?,
    val flag: Flag?,            // H / L / null
) {
    enum class Flag { H, L }
}

data class BloodTestReport(
    val reportId: String,
    val sampleDate: LocalDate?,
    val labSource: String,
    val markers: List<ExtractedMarker>,
    val pdfDownloadPath: String, // "/api/me/blood/reports/{id}/pdf"
    val createdAt: Instant,
)

data class MarkerHistoryPoint(
    val date: LocalDate,
    val value: Double,
    val source: Source,
) {
    sealed interface Source {
        data object Manual : Source
        data class Lab(val reportId: String, val labSource: String) : Source
    }
}

interface BloodReadingRepository {
    fun observeReadings(): Flow<List<BloodReading>>
    suspend fun refresh()
    suspend fun create(
        marker: BloodMarker,
        value: Double,
        unit: String?,        // null → server default for marker
        sampleDate: LocalDate,
        labSource: String?,
        notes: String?,
    ): BloodReading
    suspend fun delete(readingId: String)
}

interface BloodTestReportRepository {
    fun observeReports(): Flow<List<BloodTestReport>>
    suspend fun refresh()
    suspend fun get(reportId: String): BloodTestReport
    suspend fun delete(reportId: String)

    // Uploads a PDF and streams extraction phases. Terminal events are
    // Complete(report) or Failed(error). Cold flow: collection starts
    // the upload; cancelling the collector aborts the request.
    fun upload(fileName: String, pdfBytes: ByteArray): Flow<UploadEvent>
}

sealed interface UploadEvent {
    data object Uploading : UploadEvent
    data object Extracting : UploadEvent
    data object Saving : UploadEvent
    data class Complete(val report: BloodTestReport) : UploadEvent
    data class Failed(val error: String) : UploadEvent
}

// Combined view used by both the Blood overview "Tracked markers" grid
// and the dashboard BloodPanel. Derived in core-domain so both consumers
// see the same shape and ordering.
data class LatestMarker(
    val marker: BloodMarker,
    val value: Double?,
    val unit: String,
    val sampleDate: LocalDate?,
    val reference: ReferenceRange?,
    val flag: ExtractedMarker.Flag?,
    val history: List<MarkerHistoryPoint>,   // up to last 12 months
    val source: Source,
) {
    enum class Source { MANUAL, LAB, NONE }
}

object MarkerCatalog {
    fun displayName(marker: BloodMarker): String = when (marker) {
        BloodMarker.TOTAL_CHOLESTEROL -> "Total cholesterol"
        BloodMarker.LDL -> "LDL"
        BloodMarker.HDL -> "HDL"
        BloodMarker.TRIGLYCERIDES -> "Triglycerides"
        BloodMarker.APO_B -> "ApoB"
        BloodMarker.HBA1C -> "HbA1c"
        BloodMarker.FASTING_GLUCOSE -> "Fasting glucose"
        BloodMarker.HS_CRP -> "hs-CRP"
    }
    fun description(marker: BloodMarker): String
    fun target(marker: BloodMarker): String
    val DISPLAY_ORDER: List<BloodMarker>
}
```

### `core-data/blood/` — Retrofit services + impls

```kotlin
// core-data/.../data/blood/BloodApi.kt
internal interface BloodApi {
    @GET("api/me/blood")
    suspend fun listReadings(): List<BloodReadingDto>

    @POST("api/me/blood")
    suspend fun createReading(@Body body: CreateReadingRequestDto): BloodReadingDto

    @DELETE("api/me/blood/{readingId}")
    suspend fun deleteReading(@Path("readingId") id: String)

    @GET("api/me/blood/reports")
    suspend fun listReports(): List<BloodTestReportDto>

    @GET("api/me/blood/reports/{reportId}")
    suspend fun getReport(@Path("reportId") id: String): BloodTestReportDto

    @DELETE("api/me/blood/reports/{reportId}")
    suspend fun deleteReport(@Path("reportId") id: String)
}

// core-data/.../data/blood/BloodDtos.kt
@JsonClass(generateAdapter = true)
internal data class BloodReadingDto(
    val readingId: String,
    val marker: String,
    val value: Double,
    val unit: String,
    val sampleDate: String,        // ISO yyyy-MM-dd
    val labSource: String?,
    val notes: String?,
    val reference: ReferenceDto,
)
@JsonClass(generateAdapter = true)
internal data class ReferenceDto(
    val unit: String,
    val orientation: String,       // "LOWER_IS_BETTER" | "HIGHER_IS_BETTER"
    val goodThreshold: Double,
    val displayMin: Double,
    val displayMax: Double,
)
@JsonClass(generateAdapter = true)
internal data class CreateReadingRequestDto(
    val marker: String,
    val value: Double,
    val unit: String?,
    val sampleDate: String,
    val labSource: String?,
    val notes: String?,
)
@JsonClass(generateAdapter = true)
internal data class BloodTestReportDto(
    val reportId: String,
    val sampleDate: String?,
    val labSource: String,
    val markers: List<ExtractedMarkerDto>,
)
@JsonClass(generateAdapter = true)
internal data class ExtractedMarkerDto(
    val name: String,
    val value: Double?,
    val unit: String?,
    val refRangeLow: Double?,
    val refRangeHigh: Double?,
    val flag: String?,             // "H" | "L" | null
)

// core-data/.../data/blood/BloodReadingRepositoryImpl.kt
@Singleton
internal class BloodReadingRepositoryImpl @Inject constructor(
    private val api: BloodApi,
) : BloodReadingRepository {
    private val state = MutableStateFlow<List<BloodReading>>(emptyList())
    override fun observeReadings(): Flow<List<BloodReading>> = state.asStateFlow()
    override suspend fun refresh() {
        state.value = api.listReadings().map { it.toDomain() }
    }
    override suspend fun create(...): BloodReading {
        val created = api.createReading(CreateReadingRequestDto(...)).toDomain()
        state.update { (it + created).sortedByDescending { r -> r.sampleDate } }
        return created
    }
    override suspend fun delete(readingId: String) {
        api.deleteReading(readingId)
        state.update { it.filterNot { r -> r.readingId == readingId } }
    }
}

// core-data/.../data/blood/BloodTestReportRepositoryImpl.kt
@Singleton
internal class BloodTestReportRepositoryImpl @Inject constructor(
    private val api: BloodApi,
    private val multipartSseClient: MultipartSseClient,   // from core-network
    private val moshi: Moshi,
    @Named("backendBaseUrl") private val baseUrl: HttpUrl,
) : BloodTestReportRepository {

    private val state = MutableStateFlow<List<BloodTestReport>>(emptyList())
    override fun observeReports() = state.asStateFlow()
    override suspend fun refresh() {
        state.value = api.listReports().map { it.toDomain() }
    }
    override suspend fun get(reportId: String): BloodTestReport =
        api.getReport(reportId).toDomain()
    override suspend fun delete(reportId: String) {
        api.deleteReport(reportId)
        state.update { it.filterNot { r -> r.reportId == reportId } }
    }

    override fun upload(fileName: String, pdfBytes: ByteArray): Flow<UploadEvent> =
        multipartSseClient.stream(
            url = baseUrl.newBuilder()
                .addPathSegments("api/me/blood/reports").build(),
            parts = listOf(
                MultipartSseClient.Part(
                    name = "file",
                    fileName = fileName,
                    contentType = "application/pdf".toMediaType(),
                    body = pdfBytes,
                ),
            ),
        ).map { event -> event.toUploadEvent(moshi) }
         .onEach { e -> if (e is UploadEvent.Complete) refresh() }
}

// Phase payloads from BloodTestController:
//   { "phase": "uploading", "message": "..." }
//   { "phase": "extracting", "message": "..." }
//   { "phase": "saving", "message": "..." }
//   { "phase": "complete", "report": { ...BloodTestReportDto } }
//   { "phase": "failed", "error": "..." }
private fun SseEvent.toUploadEvent(moshi: Moshi): UploadEvent {
    val payload = moshi.adapter(Map::class.java).fromJson(data) ?: return UploadEvent.Failed("Empty event")
    return when (payload["phase"]) {
        "uploading" -> UploadEvent.Uploading
        "extracting" -> UploadEvent.Extracting
        "saving" -> UploadEvent.Saving
        "complete" -> {
            val dto = moshi.adapter(BloodTestReportDto::class.java)
                .fromJsonValue(payload["report"])!!
            UploadEvent.Complete(dto.toDomain())
        }
        "failed" -> UploadEvent.Failed(payload["error"]?.toString() ?: "Upload failed")
        else -> UploadEvent.Failed("Unknown phase: ${payload["phase"]}")
    }
}
```

Wire impls via Hilt in `core-data/.../data/blood/BloodDataModule.kt`:

```kotlin
@Module @InstallIn(SingletonComponent::class)
internal abstract class BloodDataModule {
    @Binds @Singleton
    abstract fun bindBloodReadingRepository(
        impl: BloodReadingRepositoryImpl,
    ): BloodReadingRepository

    @Binds @Singleton
    abstract fun bindBloodTestReportRepository(
        impl: BloodTestReportRepositoryImpl,
    ): BloodTestReportRepository

    companion object {
        @Provides fun provideBloodApi(retrofit: Retrofit): BloodApi =
            retrofit.create(BloodApi::class.java)
    }
}
```

### `core-network/` — `MultipartSseClient`

```kotlin
// core-network/.../network/sse/MultipartSseClient.kt
package com.gte619n.healthfitness.core.network.sse

@Singleton
class MultipartSseClient @Inject constructor(
    private val client: OkHttpClient,    // shared, auth interceptor already attached
) {
    data class Part(
        val name: String,
        val fileName: String,
        val contentType: MediaType,
        val body: ByteArray,
    )
    data class SseEvent(val event: String?, val data: String)

    fun stream(url: HttpUrl, parts: List<Part>): Flow<SseEvent> = flow {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                parts.forEach { p ->
                    addFormDataPart(
                        p.name,
                        p.fileName,
                        p.body.toRequestBody(p.contentType),
                    )
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .post(multipart)
            .header("Accept", "text/event-stream")
            .build()

        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val source: BufferedSource = response.body?.source()
                ?: throw IOException("Empty body")
            var eventName: String? = null
            val dataBuf = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.isEmpty() -> {
                        if (dataBuf.isNotEmpty()) {
                            emit(SseEvent(eventName, dataBuf.toString()))
                            eventName = null
                            dataBuf.clear()
                        }
                    }
                    line.startsWith(":") -> Unit  // comment
                    line.startsWith("event:") -> eventName = line.substring(6).trim()
                    line.startsWith("data:") -> {
                        if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                        dataBuf.append(line.substring(5).trimStart())
                    }
                }
            }
            // Final event without trailing blank line.
            if (dataBuf.isNotEmpty()) emit(SseEvent(eventName, dataBuf.toString()))
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)
}
```

The existing `EventSource` helper from IMPL-AND-00 covers GET-shaped SSE
(e.g. drug AI lookup); `MultipartSseClient` is the POST-multipart-with-
SSE-response variant. They cannot share an underlying call because OkHttp's
`EventSources.createFactory(...)` builds its own GET-only `Request`.

### `feature-blood/` — populate the module

#### `feature-blood/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.gte619n.healthfitness.feature.blood"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    implementation(project(":core-network"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.bundles.test.jvm)        // junit + turbine + mockwebserver
}
```

#### Screens + ViewModels

```kotlin
// feature-blood/.../feature/blood/BloodOverviewViewModel.kt
@HiltViewModel
class BloodOverviewViewModel @Inject constructor(
    private val readings: BloodReadingRepository,
    private val reports: BloodTestReportRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(
            val recentReports: List<BloodTestReport>,
            val trackedMarkers: List<LatestMarker>,
        ) : UiState
        data class Error(val message: String) : UiState
    }

    val state: StateFlow<UiState> = combine(
        readings.observeReadings(),
        reports.observeReports(),
    ) { r, rep -> UiState.Ready(
        recentReports = rep.sortedByDescending { it.sampleDate ?: LocalDate.MIN }.take(10),
        trackedMarkers = LatestMarkers.derive(r, rep),
    ) as UiState }
     .catch { emit(UiState.Error(it.localizedMessage ?: "Failed to load blood data")) }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init { viewModelScope.launch {
        runCatching { readings.refresh(); reports.refresh() }
    } }

    fun retry() = viewModelScope.launch { readings.refresh(); reports.refresh() }
}

// feature-blood/.../feature/blood/BloodOverviewScreen.kt
@Composable
fun BloodOverviewScreen(
    onMarkerClick: (BloodMarker) -> Unit,
    onReportClick: (String) -> Unit,
    onAddReading: () -> Unit,
    onUploadPdf: () -> Unit,
    vm: BloodOverviewViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    when (val s = ui) {
        is BloodOverviewViewModel.UiState.Loading -> LoadingState()
        is BloodOverviewViewModel.UiState.Error -> ErrorState(s.message, onRetry = vm::retry)
        is BloodOverviewViewModel.UiState.Ready -> BloodOverviewContent(
            state = s,
            onMarkerClick = onMarkerClick,
            onReportClick = onReportClick,
            onAddReading = onAddReading,
            onUploadPdf = onUploadPdf,
        )
    }
}

// Marker detail
@HiltViewModel
class MarkerDetailViewModel @Inject constructor(
    private val readings: BloodReadingRepository,
    private val reports: BloodTestReportRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val markerKey: BloodMarker = BloodMarker.valueOf(savedState["markerKey"]!!)
    val state: StateFlow<UiState> = ...   // history + readings table for one marker
    sealed interface UiState { /* Loading / Ready(latest, history, rows) / Error */ }
}

// Add reading (modal/sheet)
@HiltViewModel
class AddReadingViewModel @Inject constructor(
    private val readings: BloodReadingRepository,
) : ViewModel() {
    data class FormState(
        val marker: BloodMarker? = null,
        val value: String = "",
        val unit: String = "",
        val sampleDate: LocalDate = LocalDate.now(),
        val labSource: String = "",
        val notes: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
    )
    val form = MutableStateFlow(FormState())
    fun onMarker(m: BloodMarker)
    fun onValue(s: String)
    fun onDate(d: LocalDate)
    fun submit(onSuccess: () -> Unit)
}

// Upload lab PDF
@HiltViewModel
class UploadLabReportViewModel @Inject constructor(
    private val reports: BloodTestReportRepository,
) : ViewModel() {
    sealed interface UiState {
        data object Idle : UiState
        data object Uploading : UiState
        data object Extracting : UiState
        data object Saving : UiState
        data class Complete(val report: BloodTestReport) : UiState
        data class Failed(val error: String) : UiState
    }
    val state = MutableStateFlow<UiState>(UiState.Idle)
    private var job: Job? = null

    fun upload(fileName: String, bytes: ByteArray) {
        job?.cancel()
        job = viewModelScope.launch {
            reports.upload(fileName, bytes)
                .onStart { state.value = UiState.Uploading }
                .catch { state.value = UiState.Failed(it.localizedMessage ?: "Upload failed") }
                .collect { event ->
                    state.value = when (event) {
                        UploadEvent.Uploading -> UiState.Uploading
                        UploadEvent.Extracting -> UiState.Extracting
                        UploadEvent.Saving -> UiState.Saving
                        is UploadEvent.Complete -> UiState.Complete(event.report)
                        is UploadEvent.Failed -> UiState.Failed(event.error)
                    }
                }
        }
    }
    fun cancel() { job?.cancel(); state.value = UiState.Idle }
}

@Composable
fun UploadLabReportScreen(
    onComplete: (reportId: String) -> Unit,
    onDismiss: () -> Unit,
    vm: UploadLabReportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) { onDismiss(); return@rememberLauncherForActivityResult }
        val name = context.contentResolver.queryDisplayName(uri) ?: "report.pdf"
        val bytes = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        vm.upload(name, bytes)
    }
    LaunchedEffect(Unit) { picker.launch("application/pdf") }
    val ui by vm.state.collectAsStateWithLifecycle()
    UploadLabReportSheet(state = ui, onDismiss = onDismiss, onComplete = onComplete)
}

// Report detail
@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val reports: BloodTestReportRepository,
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    @Named("backendBaseUrl") private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
) : ViewModel() {
    private val reportId: String = savedState["reportId"]!!
    val state: StateFlow<UiState> = flow { emit(reports.get(reportId)) }
        .map { UiState.Ready(it) as UiState }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Failed to load report")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    // Downloads the PDF into cacheDir/blood/{reportId}.pdf, returns a
    // content:// uri via FileProvider, and emits an ACTION_VIEW intent
    // request the screen launches.
    suspend fun preparePdfIntent(): Intent { ... }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        reports.delete(reportId); onDeleted()
    }
    sealed interface UiState { ... }
}
```

#### Composables

- `MarkerCard.kt` — grid cell: marker label, value (color-coded by
  in/out-of-range), unit, sparkline, range bar, sample date.
- `MarkerHistoryChart.kt` — full 12-month chart. Canvas polyline +
  reference-range band; pattern lifted from `WeightChart.kt` (lines
  39-113 of `app/src/main/.../dashboard/WeightChart.kt`). Caps at one
  point per day; multi-readings-per-day take the last value.
- `MarkerReferenceBar.kt` — moved out of the dashboard package and into
  `core-ui/` so both surfaces share one implementation. Re-exports the
  existing `BloodPanel.RangeBar` (lines 103-141 of `BloodPanel.kt`)
  unchanged. Move ticket: `BloodPanel.kt` references `MarkerReferenceBar`
  from `core-ui` instead of its private `@Composable RangeBar`.
- `UploadPhaseStepper.kt` — three-state stepper: Uploading → Extracting
  → Saving, with the active phase highlighted by `Hf.colors.accent`.
- `ExtractedMarkerRow.kt` — name, value+unit, flag pill ("HIGH" / "LOW"
  via the existing `Pill` composable from `dashboard/Components.kt`).

#### Navigation routes

```kotlin
// feature-blood/.../feature/blood/BloodNavigation.kt
object BloodRoutes {
    const val OVERVIEW = "blood"
    const val MARKER_DETAIL = "blood/markers/{markerKey}"
    const val REPORT_DETAIL = "blood/reports/{reportId}"
    const val ADD_READING = "blood/add"
    const val UPLOAD_REPORT = "blood/upload"
    fun markerDetail(m: BloodMarker) = "blood/markers/${m.name}"
    fun reportDetail(id: String) = "blood/reports/$id"
}

fun NavGraphBuilder.bloodGraph(navController: NavController) {
    composable(BloodRoutes.OVERVIEW) {
        BloodOverviewScreen(
            onMarkerClick = { navController.navigate(BloodRoutes.markerDetail(it)) },
            onReportClick = { navController.navigate(BloodRoutes.reportDetail(it)) },
            onAddReading = { navController.navigate(BloodRoutes.ADD_READING) },
            onUploadPdf = { navController.navigate(BloodRoutes.UPLOAD_REPORT) },
        )
    }
    composable(
        route = BloodRoutes.MARKER_DETAIL,
        arguments = listOf(navArgument("markerKey") { type = NavType.StringType }),
    ) { MarkerDetailScreen(onBack = { navController.popBackStack() }) }
    composable(
        route = BloodRoutes.REPORT_DETAIL,
        arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
    ) { ReportDetailScreen(onBack = { navController.popBackStack() }) }
    dialog(BloodRoutes.ADD_READING) {
        AddReadingScreen(onDone = { navController.popBackStack() })
    }
    dialog(BloodRoutes.UPLOAD_REPORT) {
        UploadLabReportScreen(
            onComplete = { reportId ->
                navController.popBackStack()
                navController.navigate(BloodRoutes.reportDetail(reportId))
            },
            onDismiss = { navController.popBackStack() },
        )
    }
}
```

### `app/` — nav + dashboard wiring

- Add `bloodGraph(navController)` to the top-level `NavHost` in
  `MobileNavHost.kt`. The "Blood" bottom-nav tab (phone) and the
  foldable sidebar entry both `navigate(BloodRoutes.OVERVIEW)`.
- Replace `DashboardFixtures.bloodMarkers` references in `BloodPanel.kt`
  with a `hiltViewModel<DashboardBloodViewModel>()` that exposes a
  `StateFlow<List<LatestMarker>>`. Keep the existing `MarkerRow` /
  `RangeBar` layout intact — only the data source changes.

```kotlin
// app/.../mobile/dashboard/DashboardBloodViewModel.kt
@HiltViewModel
class DashboardBloodViewModel @Inject constructor(
    readings: BloodReadingRepository,
    reports: BloodTestReportRepository,
) : ViewModel() {
    val markers: StateFlow<List<LatestMarker>> =
        combine(readings.observeReadings(), reports.observeReports()) { r, rep ->
            LatestMarkers.derive(r, rep).take(4)   // top 4 for the dashboard panel
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

### `settings.gradle.kts`

```kotlin
include(
    ":app",
    ":wear",
    ":core-data",
    ":core-domain",
    ":core-ui",
    ":core-health",
    ":core-network",
    ":feature-workouts",
    ":feature-medical",
    ":feature-blood",        // new
    ":feature-chat",
)
```

## Tests

- `BloodOverviewViewModelTest` (`turbine`, fake repos) — emits `Loading`
  then `Ready` with derived `LatestMarker` list when both readings and
  reports flows emit; emits `Error` when `refresh()` throws.
- `MarkerDetailViewModelTest` — combines readings + report markers,
  filters to the route's marker, dedupes same-day entries (last wins,
  parity with web `buildMarkerHistory`).
- `UploadLabReportViewModelTest` (MockWebServer) — drives a chunked
  `text/event-stream` response (`uploading` → `extracting` → `saving` →
  `complete`); asserts the `UiState` transitions match in order and the
  terminal `Complete` carries a parsed `BloodTestReport`. Second test
  case: stream ends with `failed` → `UiState.Failed(error)`.
- `MultipartSseClientTest` (MockWebServer) — request inspection
  verifies `Content-Type: multipart/form-data; boundary=…`, the `file`
  part body equals the input bytes, and the `Accept` header is
  `text/event-stream`. Response inspection: a chunked body with two
  events separated by `\n\n` is decoded into two `SseEvent` emissions in
  order; an event with multiple `data:` lines is joined with `\n`.
- `BloodReadingRepositoryImplTest` — `create()` posts the expected JSON
  body shape; `delete()` removes the row from the local `StateFlow`.
- `MarkerReferenceBarTest` (Compose preview / `Paparazzi` snapshot if
  available, otherwise just `@Preview` composables under
  `core-ui/.../preview/`) — three snapshots: in-range, low, high.
- `BloodNavigationTest` (instrumented, ComposeNavigation) — `blood` →
  tap marker card → lands on `blood/markers/LDL`; back returns to
  overview.

## Acceptance criteria

Manual, against a deployed dev backend:

1. Open Blood from the bottom nav. Overview loads within ~1s, showing
   the user's actual recent reports and tracked-marker grid. No
   fixtures visible.
2. Tap a marker card → marker detail opens with a 12-month chart and a
   table of readings labeled with source ("Manual" or "Lab — Quest
   Diagnostics").
3. From overview, tap **Add reading**. Pick LDL, enter `82`, today's
   date, submit. The new reading appears at the top of the LDL marker
   detail and on the dashboard's `BloodPanel` immediately on return.
4. From overview, tap **Upload lab PDF**. Pick a real PDF
   (e.g. `docs/test_reports/blood/labcorp_2025_q4.pdf`). The phase
   stepper transitions Uploading → Extracting → Saving → Done within
   ~20-30s. On Done, the app navigates to the new report detail.
5. On report detail, tap **View PDF** — the system PDF viewer opens
   the cached file. Tap **Delete report** → confirm → list refreshes
   without the row.
6. Background the app during an upload, return after the stream
   completes — UI converges on the terminal state correctly (no stuck
   spinner) thanks to `WhileSubscribed(5_000)` re-collection on
   foreground.
7. Toggle airplane mode mid-upload → overview's upload sheet shows
   `Failed(error)` with a retry-friendly message; no crash.
8. Dashboard `BloodPanel` shows the same top-4 markers as the overview
   grid, in the same order, with the same values and range positions.

CI:

9. `./gradlew :feature-blood:test :core-data:test :core-network:test
   :core-domain:test` passes.
10. `./gradlew :feature-blood:lint` clean.

## Open questions

Resolved:

- **Module location** — own module `feature-blood/`. `feature-medical/`
  reserved for non-lab medical records.
- **PDF viewer** — system intent via FileProvider. `androidx.pdf` still
  alpha; revisit when stable.
- **Multipart + SSE** — custom `MultipartSseClient` in `core-network/`,
  not Retrofit, because Retrofit can't expose the streaming body as a
  `Flow` without losing the multipart upload semantics in one helper.
- **Marker name canonicalization** — server-authoritative. Don't port
  web's regex table to Kotlin.
- **Manual reading defaults** — unit defaults to the marker's canonical
  unit (`BloodReferenceRanges.rangeFor(marker).unit()` server-side);
  pre-filled by reading it off the first existing reading or by an
  initial `GET /api/me/blood?probe=true` (deferred — initial render
  asks the server when the form opens).

Deferred:

- **Inline editing of extracted marker values** on report detail.
  Backend supports `PATCH /reports/{id}/field`; mobile flow needs
  number-pad keyboard + per-row save. Out of scope here.
- **Offline cache** — Room mirror of readings + reports. Deferred
  until a real offline use-case (e.g. clinic with no signal) surfaces.
- **Marker ↔ medication overlays** — lands as a follow-up to
  IMPL-AND-03.
- **Wear OS surface** — viewing latest LDL on a watch face is a
  reasonable complication, but tied to IMPL-AND wear phase.

## Dependencies

- **IMPL-AND-00** (foundations) — Hilt, `core-network` with
  `AuthInterceptor` + `TokenAuthenticator` + base URL config, NavHost,
  `core-ui` `LoadingState` / `EmptyState` / `ErrorState`, ViewModel
  convention. This IMPL adds `MultipartSseClient` to `core-network/`
  but assumes the EventSource helper and the OkHttp client wiring are
  already in place.
- **IMPL-AND-01** (dashboard live data) — the `BloodPanel` refactor in
  this IMPL replaces fixture data with the same repositories that feed
  the new overview. If IMPL-AND-01 lands a different `BloodPanel`
  wiring first, this IMPL collapses to "no-op for dashboard,
  feature-blood is additive".
- **Backend** — endpoints already exist
  (`backend/api/.../blood/BloodController.java`,
  `backend/app/.../bloodtest/BloodTestController.java`). No backend
  changes required.
