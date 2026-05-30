# IMPL-AND-06: Android — gym locations & equipment

## Goal

Bring the web's gym/equipment management surface to the Android phone app:
list, create, edit, and delete user gym locations; manage their cover
photos, hours, and amenities; attach equipment from the shared catalog or
submit new equipment for review; and override equipment specs per
location. Populates the currently-empty `feature-workouts/` module and
adds it to the bottom-nav / foldable sidebar. Mirrors the data model
introduced by IMPL-GYM-001 exactly so the same backend endpoints (`/api/me/gyms*`,
`/api/equipment*`, `/api/me/equipment*`) serve both clients without
forking DTOs.

## Scope

In scope:

- Domain models matching the IMPL-GYM-001 Firestore / API shape:
  `Location`, `HoursSlot`, `Amenity` enum, `Equipment`, `EquipmentSpec`
  sealed class with per-category subtypes, `EquipmentOverride`.
- Retrofit services + Moshi adapters (including a
  `PolymorphicJsonAdapterFactory` for `EquipmentSpec`) under
  `core-data/workouts/`.
- Five screens / two bottom-sheet modals in `feature-workouts/`:
  Gyms list, New gym, Gym detail, Edit gym, Add-equipment sheet,
  Equipment-override sheet (see "Screens").
- `MultipartUploadClient` helper in `core-network` (factored out so
  IMPL-AND-04 / IMPL-AND-05 can also use it for blood / DEXA PDF uploads).
- Cover-photo picker via `ActivityResultContracts.GetContent("image/*")`
  + multipart upload to `POST /api/me/gyms/{id}/photo`.
- Coil image loading for cover photos and equipment thumbnails (Coil is
  already wired in `core-ui` per IMPL-AND-00).
- Navigation: add a `Workouts` destination to the phone bottom-nav and the
  foldable sidebar, with the four routes nested under it.

Out of scope (deferred):

- **Bulk CSV / paste-list import** (IMPL-GYM-002 on web) — desktop-friendlier
  paste-and-preview flow; will land later if a mobile use case emerges.
- **Admin surfaces** (pending equipment review, catalog management, image
  regeneration with custom prompts) — desktop-only per parity roadmap §2.8.
- **Active workout logging** — greenfield on both clients, owned by Phase 7
  / a separate ADR. This IMPL only handles the gym + equipment metadata
  the future logging UI will read.
- **Per-equipment "exercise count" badges** — backend returns
  `exerciseCount: null` until exercises ship; UI tolerates null and hides
  the row.
- **Equipment image regeneration UI** — admin-only.
- **Soft-deleted-gym restoration UI** — the backend supports
  `?include=inactive` but the phone only shows active.
- **Wear OS surface** — wear doesn't expose gym management.

## Decisions

| Topic | Decision |
|---|---|
| Spec-schema modeling | Sealed class `EquipmentSpec` with subtypes `Selectorized`, `PlateLoaded`, `Bodyweight`, `Cable`, `Cardio`, `WeightSet`. Carries no platform types — pure Kotlin data classes. The discriminator wire field is `specSchema` (uppercase enum: `SELECTORIZED` etc.), matching the backend. |
| Polymorphic JSON | Moshi `PolymorphicJsonAdapterFactory.of(EquipmentSpec::class.java, "specSchema")` registered on the app `Moshi` instance. Each subtype's labels match the backend uppercase enum names. `withDefaultValue(Bodyweight)` for forward compatibility — unknown schemas degrade to "no specs" rather than crashing the list. |
| Per-location override storage | Web's `Location.equipmentSpecs` is `Map<equipmentId, Map<String, Any>>` — a free-form override that's *not* a polymorphic `EquipmentSpec`. Android mirrors that: `Location.equipmentSpecs: Map<String, Map<String, Any?>>`. The override sheet hydrates the catalog default into the same per-category form, lets the user edit, and PATCHes the resulting `specs` map back. The map is intentionally untyped — partial overrides are valid (e.g., only `maxWeight` differs from catalog). |
| Cover-photo upload | `ActivityResultContracts.GetContent("image/*")` → `Uri` → resolve to a `ContentResolver` `InputStream`, copy into a `RequestBody` chunk, send as `multipart/form-data` field `file`. New `MultipartUploadClient` helper in `core-network` so blood/DEXA can reuse. No client-side resize (backend already serves transcoded WebP). |
| Hours form representation | `HoursMatrix` composable holds a `Map<DayOfWeek, HoursSlot?>`. Submitting maps to the API as `Map<DayOfWeek, HoursSlot>` excluding null entries (closed days). When `is24Hours` is `true` the matrix is hidden and the API payload omits `hours`. **[PR#8]** Times are **15-minute-increment dropdowns** (00:00…23:45), not a free TimePicker — matches web's `HoursEditor` `TIME_OPTIONS`. Each day toggles **Closed** (clears the row → null) and offers a per-day “copy from the day above” action. |
| `DayOfWeek` wire case **[PR#8]** | The `hours` map is keyed by `DayOfWeek`, serialized **lowercase** on the wire (`"mon"`…`"sun"`) by the backend's Jackson key (de)serializer. The Moshi adapter must encode lowercase and decode case-insensitively, or the hours map fails to (de)serialize — this was the cause of the gym-hours save-500 fixed in PR #8. Reuse the shared `DayOfWeek` Moshi adapter from IMPL-AND-03. |
| Amenities | Hardcoded list of 10 IDs that mirror `web/lib/types/gym.ts` `AMENITIES` exactly. Stored on the wire as `List<String>` of IDs (lowercase). No localization in this IMPL — labels are English-only. |
| Default-gym handling | `POST /api/me/gyms/{id}/default` is atomic on the backend — the only client responsibility is to refresh the list after the call. ViewModel optimistically flips the `isDefault` flag on the active item and unflips siblings, with rollback on error. |
| Equipment catalog search | Catalog list inside the add-equipment sheet uses a debounced (300ms) search-text-flow → `GET /api/equipment?search=...&category=...&sub=...`. Server-side filtering — no client cache of the whole catalog. |
| Submit-new-equipment | Inside the same bottom sheet as the catalog browser — a tab toggle "Catalog | Submit new". On submit, the equipment is added to the current location atomically: `POST /api/me/equipment` → take the returned `equipmentId` → `POST /api/me/gyms/{locationId}/equipment/{equipmentId}`. |
| Delete-gym confirmation | Uses the AlertDialog already established in IMPL-AND-00 (`ConfirmDialog`). Soft delete — same as web. |
| Admin surfaces on phone | Not built. No admin Workouts entries in nav. |
| AI image generation | Backend handles image generation; Android only consumes `imageUrl`. When `imageStatus == PENDING` or `FAILED`, Coil displays a per-category placeholder vector from `core-ui` (`R.drawable.ic_equipment_<category>`). |
| Sealed-class serialization fallback | If the backend later adds a spec schema unknown to this build (e.g., a `WEIGHT_SET_V2`), `withDefaultValue(Bodyweight)` keeps the screen rendering. The override sheet refuses to edit and shows "Open in web app to edit". |

## Dependencies

This IMPL declares the following landed-prereqs (defined in earlier
specs — do **not** redefine here):

- **IMPL-AND-00**: Hilt graph, `@HiltViewModel`, `hiltViewModel()`,
  `core-network` with `AuthInterceptor` + `TokenAuthenticator` and
  `BuildConfig.BACKEND_BASE_URL`, `NavHost` with type-safe routes,
  `core-ui` primitives (`LoadingState`, `EmptyState`, `ErrorState`,
  `SnackbarController`, `AsyncImage` Coil wrapper, `ConfirmDialog`),
  base `Moshi` instance exposed through Hilt, ViewModel/UiState
  conventions, bottom-nav scaffold with `Workouts` slot reserved.
- **IMPL-AND-04 / IMPL-AND-05**: if either has already landed a multipart
  helper inside `core-network`, this IMPL extracts the multipart concern
  into `MultipartUploadClient` and refactors callers; if not, this IMPL
  is the first to define it.

## Deliverables

### `core-domain/workouts/`

Package: `com.gte619n.healthfitness.core.domain.workouts`

```kotlin
// Location.kt
enum class DayOfWeek { MON, TUE, WED, THU, FRI, SAT, SUN }

data class HoursSlot(val open: String, val close: String)   // "HH:mm", 15-min increments
// [PR#8] DayOfWeek wire form is lowercase ("mon"..); Moshi adapter must map both ways.

enum class Amenity(val id: String, val label: String) {
    TWENTY_FOUR_HR("24hr", "24-Hour Access"),
    LOCKERS("lockers", "Lockers"),
    SHOWERS("showers", "Showers"),
    PARKING("parking", "Parking"),
    WIFI("wifi", "WiFi"),
    TOWELS("towels", "Towels"),
    SAUNA("sauna", "Sauna"),
    POOL("pool", "Pool"),
    CHILDCARE("childcare", "Childcare"),
    TRAINING("training", "Personal Training");
    companion object { fun fromId(id: String): Amenity? = entries.firstOrNull { it.id == id } }
}

data class Location(
    val locationId: String,
    val name: String,
    val address: String?,
    val coverPhotoUrl: String?,
    val is24Hours: Boolean,
    val hours: Map<DayOfWeek, HoursSlot>?,
    val amenities: List<Amenity>,
    val equipmentIds: List<String>,
    val equipmentSpecs: Map<String, Map<String, Any?>>, // per-location overrides
    val isDefault: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

```kotlin
// Equipment.kt
enum class SpecSchemaTag { SELECTORIZED, PLATE_LOADED, BODYWEIGHT, CABLE, CARDIO, WEIGHT_SET }
enum class ImageStatus { PENDING, GENERATED, FAILED }
enum class EquipmentStatus { ACTIVE, PENDING_REVIEW, REJECTED }

sealed class EquipmentSpec {
    abstract val tag: SpecSchemaTag

    data class Selectorized(
        val minWeight: Double, val maxWeight: Double, val increment: Double,
    ) : EquipmentSpec() { override val tag = SpecSchemaTag.SELECTORIZED }

    data class PlateLoaded(
        val barWeight: Double, val availablePlates: List<Double>,
    ) : EquipmentSpec() { override val tag = SpecSchemaTag.PLATE_LOADED }

    data object Bodyweight : EquipmentSpec() { override val tag = SpecSchemaTag.BODYWEIGHT }

    data class Cable(val weightStack: Double, val numStations: Int)
        : EquipmentSpec() { override val tag = SpecSchemaTag.CABLE }

    data class Cardio(val resistanceLevels: Int, val hasIncline: Boolean)
        : EquipmentSpec() { override val tag = SpecSchemaTag.CARDIO }

    data class WeightSet(
        val minWeight: Double?, val maxWeight: Double?,
        val increment: Double?, val weights: List<Double>?,
    ) : EquipmentSpec() { override val tag = SpecSchemaTag.WEIGHT_SET }
}

data class Equipment(
    val equipmentId: String,
    val name: String,
    val category: String,
    val subcategory: String,
    val specSchema: SpecSchemaTag,
    val specs: EquipmentSpec,
    val imageUrl: String?,
    val imageStatus: ImageStatus,
    val ownerId: String?,
    val status: EquipmentStatus,
    val contributorId: String?,
    val exerciseCount: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class EquipmentOverride(
    val equipmentId: String,
    val specs: Map<String, Any?>,
)
```

```kotlin
// Repositories.kt
interface LocationRepository {
    suspend fun list(includeInactive: Boolean = false): Result<List<Location>>
    suspend fun get(locationId: String): Result<Location>
    suspend fun create(req: CreateLocationRequest): Result<Location>
    suspend fun update(locationId: String, req: UpdateLocationRequest): Result<Location>
    suspend fun delete(locationId: String): Result<Unit>
    suspend fun setDefault(locationId: String): Result<Unit>
    suspend fun uploadCoverPhoto(locationId: String, file: PendingUpload): Result<String>
    suspend fun deleteCoverPhoto(locationId: String): Result<Unit>
    suspend fun addEquipment(locationId: String, equipmentId: String): Result<Unit>
    suspend fun removeEquipment(locationId: String, equipmentId: String): Result<Unit>
    suspend fun updateEquipmentSpecs(
        locationId: String, equipmentId: String, specs: Map<String, Any?>,
    ): Result<Location>
}

interface EquipmentRepository {
    suspend fun searchCatalog(
        search: String? = null, category: String? = null, subcategory: String? = null,
    ): Result<List<Equipment>>
    suspend fun get(equipmentId: String): Result<Equipment>
    suspend fun categories(): Result<Map<String, List<String>>>
    suspend fun submit(req: CreateEquipmentRequest): Result<Equipment>
    suspend fun mySubmissions(): Result<List<Equipment>>
    suspend fun deleteSubmission(equipmentId: String): Result<Unit>
}

data class CreateLocationRequest(
    val name: String, val address: String?, val is24Hours: Boolean,
    val hours: Map<DayOfWeek, HoursSlot>?, val amenities: List<String>,
    val equipmentIds: List<String> = emptyList(),
)
data class UpdateLocationRequest(
    val name: String? = null, val address: String? = null,
    val is24Hours: Boolean? = null, val hours: Map<DayOfWeek, HoursSlot>? = null,
    val amenities: List<String>? = null, val equipmentIds: List<String>? = null,
)
data class CreateEquipmentRequest(
    val name: String, val category: String, val subcategory: String,
    val specSchema: SpecSchemaTag, val specs: EquipmentSpec,
)
```

### `core-network/`

If `MultipartUploadClient` does not yet exist (i.e., no earlier IMPL
introduced it), add it here. If IMPL-AND-04 added an
SSE+multipart hybrid (`MultipartSseClient`), refactor: factor the
multipart-body construction into this helper and have the SSE client
depend on it.

```kotlin
// core/network/MultipartUploadClient.kt
package com.gte619n.healthfitness.core.network

data class PendingUpload(
    val filename: String,
    val mimeType: String,
    val source: () -> InputStream,
)

class MultipartUploadClient @Inject constructor(
    private val client: OkHttpClient,
    @Named("backendBaseUrl") private val baseUrl: String,
) {
    suspend fun <T> upload(
        path: String,
        upload: PendingUpload,
        fieldName: String = "file",
        parse: (ResponseBody) -> T,
    ): Result<T> { /* builds Multipart.Body, POSTs, dispatches on Dispatchers.IO */ }
}
```

A small `UriUploads` helper in `core-network` converts an Android `Uri`
into a `PendingUpload` via `ContentResolver` (`openInputStream`,
`getType`, `query DISPLAY_NAME`). Kept in `core-network` to avoid a
Compose-feature dependency on Android framework `Uri` parsing inside the
domain layer.

### `core-data/workouts/`

Package: `com.gte619n.healthfitness.core.data.workouts`

```kotlin
// LocationDto.kt + EquipmentDto.kt — wire-format mirrors with String enums
// and Instant ISO strings. Mappers translate to/from core-domain models.

interface LocationApi {
    @GET("api/me/gyms")
    suspend fun list(@Query("include") include: String? = null): List<LocationDto>

    @GET("api/me/gyms/{id}")
    suspend fun get(@Path("id") id: String): LocationDto

    @POST("api/me/gyms")
    suspend fun create(@Body req: CreateLocationDto): LocationDto

    @PATCH("api/me/gyms/{id}")
    suspend fun update(@Path("id") id: String, @Body req: UpdateLocationDto): LocationDto

    @DELETE("api/me/gyms/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>

    @POST("api/me/gyms/{id}/default")
    suspend fun setDefault(@Path("id") id: String): Response<Unit>

    @DELETE("api/me/gyms/{id}/photo")
    suspend fun deleteCoverPhoto(@Path("id") id: String): Response<Unit>

    @POST("api/me/gyms/{id}/equipment/{equipmentId}")
    suspend fun addEquipment(@Path("id") id: String, @Path("equipmentId") eq: String): Response<Unit>

    @DELETE("api/me/gyms/{id}/equipment/{equipmentId}")
    suspend fun removeEquipment(@Path("id") id: String, @Path("equipmentId") eq: String): Response<Unit>

    @PATCH("api/me/gyms/{id}/equipment/{equipmentId}")
    suspend fun updateEquipmentSpecs(
        @Path("id") id: String, @Path("equipmentId") eq: String,
        @Body body: SpecsPatchDto,
    ): LocationDto
}

interface EquipmentApi {
    @GET("api/equipment")
    suspend fun search(
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
        @Query("sub") sub: String? = null,
    ): List<EquipmentDto>

    @GET("api/equipment/{id}") suspend fun get(@Path("id") id: String): EquipmentDto
    @GET("api/equipment/categories") suspend fun categories(): Map<String, List<String>>

    @POST("api/me/equipment") suspend fun submit(@Body req: CreateEquipmentDto): EquipmentDto
    @GET("api/me/equipment") suspend fun mySubmissions(): List<EquipmentDto>
    @DELETE("api/me/equipment/{id}") suspend fun delete(@Path("id") id: String): Response<Unit>
}

@Module @InstallIn(SingletonComponent::class)
object WorkoutsDataModule {
    @Provides @IntoSet
    fun provideEquipmentSpecAdapterFactory(): JsonAdapter.Factory =
        PolymorphicJsonAdapterFactory.of(EquipmentSpec::class.java, "specSchema")
            .withSubtype(EquipmentSpec.Selectorized::class.java, "SELECTORIZED")
            .withSubtype(EquipmentSpec.PlateLoaded::class.java, "PLATE_LOADED")
            .withSubtype(EquipmentSpec.Bodyweight::class.java, "BODYWEIGHT")
            .withSubtype(EquipmentSpec.Cable::class.java, "CABLE")
            .withSubtype(EquipmentSpec.Cardio::class.java, "CARDIO")
            .withSubtype(EquipmentSpec.WeightSet::class.java, "WEIGHT_SET")
            .withDefaultValue(EquipmentSpec.Bodyweight)

    @Provides fun provideLocationApi(retrofit: Retrofit): LocationApi = retrofit.create()
    @Provides fun provideEquipmentApi(retrofit: Retrofit): EquipmentApi = retrofit.create()
}

class LocationRepositoryImpl @Inject constructor(
    private val api: LocationApi,
    private val multipart: MultipartUploadClient,
    private val moshi: Moshi,
) : LocationRepository { /* try/catch → Result; map DTO ↔ domain */ }

class EquipmentRepositoryImpl @Inject constructor(
    private val api: EquipmentApi,
) : EquipmentRepository { /* try/catch → Result */ }

@Module @InstallIn(SingletonComponent::class)
abstract class WorkoutsRepositoryModule {
    @Binds abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository
    @Binds abstract fun bindEquipmentRepository(impl: EquipmentRepositoryImpl): EquipmentRepository
}
```

### `feature-workouts/`

Package: `com.gte619n.healthfitness.feature.workouts`

Module `build.gradle.kts` (replaces the current stub):

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}
android {
    namespace = "com.gte619n.healthfitness.feature.workouts"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    implementation(project(":core-network"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

Navigation (added to the app's central `NavHost`):

```kotlin
// nav/WorkoutsRoutes.kt
sealed interface WorkoutsRoute {
    @Serializable data object GymsList : WorkoutsRoute
    @Serializable data object NewGym : WorkoutsRoute
    @Serializable data class GymDetail(val locationId: String) : WorkoutsRoute
    @Serializable data class EditGym(val locationId: String) : WorkoutsRoute
}

fun NavGraphBuilder.workoutsGraph(navController: NavController) {
    composable<WorkoutsRoute.GymsList> { GymsListScreen(/* nav callbacks */) }
    composable<WorkoutsRoute.NewGym> { NewGymScreen(/* nav callbacks */) }
    composable<WorkoutsRoute.GymDetail> { GymDetailScreen(/* ... */) }
    composable<WorkoutsRoute.EditGym> { EditGymScreen(/* ... */) }
}
```

Screens / ViewModels (one Kotlin file per pair):

```kotlin
// GymsListViewModel.kt
@HiltViewModel
class GymsListViewModel @Inject constructor(
    private val repo: LocationRepository,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val locations: List<Location> = emptyList(),
        val error: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    init { refresh() }
    fun refresh() { /* repo.list() → state */ }
}

// GymsListScreen.kt — Scaffold + LazyVerticalGrid of LocationCard, FAB → NewGym
@Composable fun GymsListScreen(
    onAddGym: () -> Unit,
    onOpenGym: (String) -> Unit,
    vm: GymsListViewModel = hiltViewModel(),
) { /* LoadingState/EmptyState/ErrorState dispatch */ }
```

```kotlin
// NewGymViewModel.kt
@HiltViewModel
class NewGymViewModel @Inject constructor(
    private val repo: LocationRepository,
) : ViewModel() {
    data class FormState(
        val name: String = "",
        val address: String = "",
        val is24Hours: Boolean = false,
        val hours: Map<DayOfWeek, HoursSlot?> = DayOfWeek.entries.associateWith { null },
        val amenities: Set<Amenity> = emptySet(),
        val submitting: Boolean = false,
        val error: String? = null,
    )
    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()
    fun update(transform: FormState.() -> FormState) { _form.update(transform) }
    fun submit(onSuccess: (locationId: String) -> Unit) { /* validate + repo.create */ }
    fun validate(state: FormState): String? = when {
        state.name.isBlank() -> "Name is required"
        !state.is24Hours && state.hours.values.all { it == null } -> "Set at least one day's hours"
        else -> null
    }
}

// NewGymScreen.kt — uses LocationForm composable
```

```kotlin
// GymDetailViewModel.kt
@HiltViewModel
class GymDetailViewModel @Inject constructor(
    private val repo: LocationRepository,
    private val equipmentRepo: EquipmentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val locationId: String = savedStateHandle.toRoute<WorkoutsRoute.GymDetail>().locationId
    data class UiState(
        val loading: Boolean = true,
        val location: Location? = null,
        val equipment: List<Equipment> = emptyList(), // catalog rows for this gym
        val error: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    init { refresh() }
    fun refresh() { /* repo.get + parallel equipmentRepo.get for each id */ }
    fun setDefault() { /* optimistic flip + repo.setDefault */ }
    fun delete(onDeleted: () -> Unit) { /* ConfirmDialog → repo.delete */ }
    fun removeEquipment(equipmentId: String) { /* repo.removeEquipment + refresh */ }
}

// GymDetailScreen.kt — hero cover, name, address, HoursMatrix (read-only),
// AmenityChipGrid (read-only), equipment list of EquipmentRow.
```

```kotlin
// EditGymViewModel.kt — same shape as NewGymViewModel, but loads existing
// location into FormState on init and POSTs PATCH. Also exposes the
// cover-photo upload action (delegates to LocationRepository.uploadCoverPhoto).

// AddEquipmentViewModel.kt
@HiltViewModel
class AddEquipmentViewModel @Inject constructor(
    private val locationRepo: LocationRepository,
    private val equipmentRepo: EquipmentRepository,
) : ViewModel() {
    enum class Tab { CATALOG, SUBMIT }
    data class CatalogState(
        val query: String = "",
        val category: String? = null,
        val subcategory: String? = null,
        val results: List<Equipment> = emptyList(),
        val loading: Boolean = false,
    )
    data class SubmitState(
        val name: String = "", val category: String = "", val subcategory: String = "",
        val schema: SpecSchemaTag = SpecSchemaTag.SELECTORIZED,
        val specs: EquipmentSpec = EquipmentSpec.Selectorized(0.0, 0.0, 0.0),
        val submitting: Boolean = false, val error: String? = null,
    )
    val catalog: StateFlow<CatalogState>
    val submitForm: StateFlow<SubmitState>
    fun setQuery(q: String) { /* debounced search */ }
    fun addFromCatalog(locationId: String, equipmentId: String, onDone: () -> Unit)
    fun submitNew(locationId: String, onDone: () -> Unit)
}

// AddEquipmentSheet.kt — ModalBottomSheet with Catalog | Submit tabs
```

```kotlin
// EquipmentOverrideViewModel.kt
@HiltViewModel
class EquipmentOverrideViewModel @Inject constructor(
    private val locationRepo: LocationRepository,
    private val equipmentRepo: EquipmentRepository,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val equipment: Equipment? = null,
        val specs: Map<String, Any?> = emptyMap(),     // current edits
        val submitting: Boolean = false,
        val error: String? = null,
    )
    fun load(locationId: String, equipmentId: String) { /* hydrate from catalog + existing override */ }
    fun update(specs: Map<String, Any?>) { _state.update { it.copy(specs = specs) } }
    fun save(locationId: String, equipmentId: String, onDone: () -> Unit)
}

// EquipmentOverrideSheet.kt — ModalBottomSheet, dispatches to EquipmentSpecForm
// based on the underlying catalog Equipment's schema.
```

Reusable composables (under `feature.workouts.ui/`):

- `LocationCard(location, onClick)` — Coil `AsyncImage` cover photo
  (16:9), name, default star, address, equipment count, amenity icon
  row (top 4).
- `LocationForm(state, onChange, onSubmit, submitLabel)` — wraps name +
  address text fields, 24h switch, `HoursMatrix`, `AmenityChipGrid`.
  Used by both `NewGymScreen` and `EditGymScreen`.
- `CoverPhotoUploader(currentUrl, onPick, onDelete, uploading)` — uses
  `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`
  with type `"image/*"`. Renders the current photo (`AsyncImage`) or a
  placeholder; the launcher result `Uri` is converted to `PendingUpload`
  via `UriUploads` and handed to `onPick`.
- `HoursMatrix(state, onChange)` — 7 rows of {day label, "copy from
  above" button (rows 2-7), open dropdown, close dropdown, "Closed"
  toggle}. **[PR#8]** Open/close are **15-minute-increment dropdowns**
  (generate `00:00`…`23:45`; display 12-hour labels, store 24-hour
  `HH:mm`) rather than a Material3 `TimePickerDialog` — mirrors web's
  `HoursEditor`. Tapping the day label (or "Closed") clears the row to
  null; the per-day ↑ copies the previous day's slot.
- `AmenityChipGrid(selected, onToggle)` — flow row of 10
  `FilterChip(Amenity.label)`.
- `EquipmentRow(equipment, override, onTap, onRemove)` — thumbnail,
  name, category, "Modified" badge if override present, overflow menu
  for remove.
- `EquipmentSpecForm(schema, specs, onChange)` — `when (schema)`
  dispatches to six per-category subcomposables that mirror the web
  `EquipmentSpecsForm`:
  - `SelectorizedSpecForm` — minWeight / maxWeight / increment numeric
    inputs.
  - `PlateLoadedSpecForm` — barWeight numeric, availablePlates as a
    chip row with add/remove.
  - `BodyweightSpecForm` — "No additional specifications required".
  - `CableSpecForm` — weightStack + numStations.
  - `CardioSpecForm` — resistanceLevels + hasIncline switch.
  - `WeightSetSpecForm` — min/max/increment OR explicit weights chip
    row; allow either set.
- `DeleteLocationButton(onConfirm)` — wraps `ConfirmDialog` from `core-ui`.
- `SetDefaultButton(isDefault, onClick)`.

### `app/`

- Add `Workouts` to `BottomNavDestination` + foldable
  `SidebarDestination` (icon: Material `Icons.Filled.FitnessCenter`,
  label: "Workouts"). Selecting it navigates to
  `WorkoutsRoute.GymsList`.
- Register `workoutsGraph(navController)` inside the central `NavHost`.
- No Application-level changes — Hilt graph extends automatically via the
  new `@Module` classes in `core-data/workouts` and `feature-workouts`.

## Tests

`feature-workouts/src/test/java/`:

- **GymsListViewModelTest** — `MockK` `LocationRepository`. Asserts:
  initial loading state, success populates list, repository failure
  surfaces `error`, `refresh()` re-invokes the repository. Uses
  `MainDispatcherRule` + Turbine.
- **GymDetailViewModelTest** — happy-path detail load with two catalog
  fetches in parallel, optimistic `setDefault` rollback on failure,
  `removeEquipment` triggers refresh.
- **NewGymViewModelTest** — form validation matrix: blank name rejected;
  is24Hours=false with no hours rejected; valid payload reaches
  `repo.create` with the expected `CreateLocationRequest`.
- **EquipmentOverrideViewModelTest** — loading the sheet for a gym with
  no existing override hydrates from the catalog default; saving sends a
  `Map<String,Any?>` matching the edits.

`core-data/src/test/java/`:

- **EquipmentSpecMoshiAdapterTest** — round-trip per category:
  - Selectorized: `{minWeight:10,maxWeight:200,increment:5,specSchema:"SELECTORIZED"}`
  - PlateLoaded: `{barWeight:45,availablePlates:[2.5,5,10,25,35,45],specSchema:"PLATE_LOADED"}`
  - Bodyweight: `{specSchema:"BODYWEIGHT"}`
  - Cable, Cardio, WeightSet analogously.
  - Unknown discriminator `"FUTURE_V2"` deserializes to
    `EquipmentSpec.Bodyweight` (default value).
- **LocationDtoMapperTest** — wire ↔ domain mapping including:
  amenities ID → enum, hours map with omitted days, `equipmentSpecs` map
  passthrough.
- **MultipartUploadClientTest** — uses `MockWebServer`, asserts the
  built multipart body has the expected `Content-Disposition`,
  `Content-Type`, and field name.

`feature-workouts/src/androidTest/java/` (Compose UI snapshots — same
runner pattern IMPL-AND-00 establishes):

- **EquipmentSpecFormTest** — snapshot one preview per category in light
  + dark themes (6 categories × 2 themes = 12 snapshots).
- **LocationCardTest** — snapshot with cover photo, with placeholder,
  with default-star, without address.

## Acceptance

Automated:

1. `./gradlew :feature-workouts:test :core-data:test
   :core-network:test` passes.
2. Compose snapshot tests above match recorded baselines.
3. `./gradlew :app:assembleDebug` builds with the new module wired in
   the bottom nav.

Manual (end-to-end against staging backend):

1. Sign in. Tap **Workouts** in the bottom nav. Empty state appears
   ("No gyms yet").
2. Tap the FAB. Fill name "Home Gym", leave address blank, toggle
   **24-hour access**, check **Lockers** + **Showers**. Submit. Lands on
   the new gym detail screen.
3. Edit. Tap the cover-photo placeholder, pick a photo from the picker
   ("image/*"). After upload, the photo renders inline. Back to detail
   — the photo is still there.
4. Tap **Add equipment**. Search "barbell". Catalog rows appear within
   ~500ms. Tap the catalog row for "Olympic Barbell". It joins the gym.
5. Tap that equipment row → **Override specs**. Change
   `availablePlates` to `[2.5,5,10,25,45]`. Save. The row shows the
   "Modified" badge. Reload the screen — the badge persists.
6. From the detail screen tap **Add equipment** → **Submit new**. Name
   "Concept2 BikeErg", category "Machines - Cardio", subcategory
   "Other", schema **Cardio**, resistance levels 10, no incline.
   Submit. The new equipment immediately joins the gym and appears
   under "My submissions" if you re-enter the sheet on a different gym.
7. From the gym detail tap **Set as default**. Star icon flips on this
   card. Return to the gyms list — only this card has the star.
8. Long-press / overflow → **Delete gym** → confirm. The card disappears
   from the list (soft-deleted on backend).

## Open questions

Resolved:

- **Sealed-class polymorphism** — Moshi
  `PolymorphicJsonAdapterFactory` with `withDefaultValue(Bodyweight)`.
  Future spec schemas degrade gracefully.
- **Per-location overrides typing** — kept as untyped `Map<String, Any?>`
  to match the web's free-form override storage. The override sheet
  reuses the polymorphic `EquipmentSpecForm` against the *catalog*
  equipment's schema and produces a map for the wire.
- **Multipart helper location** — `core-network/MultipartUploadClient`.
  Shared with blood / DEXA uploads.
- **Catalog caching** — none. Server-side search only.
- **Admin on mobile** — not built; reaffirmed by roadmap §2.8.

Deferred to implementation:

- **Time entry UX — RESOLVED [PR#8].** Web settled on 15-minute-increment
  dropdowns (no modal TimePicker), so Android matches: per-field exposed
  dropdowns of `00:00`…`23:45`. This also sidesteps the
  modal-TimePicker-on-small-screens concern entirely.
- **Coil placeholder palette** — initial set ships the 7 category vectors
  in `core-ui`. If the catalog grows new top-level categories, the
  composable falls back to a generic dumbbell silhouette.
- **Upload progress UI** — first cut shows an indeterminate
  `LinearProgressIndicator` across the cover-photo card while
  `LocationRepository.uploadCoverPhoto` is in flight. Real percentage
  needs an `OkHttp Interceptor` instrumented `RequestBody` wrapper —
  fold into IMPL-AND-04 if blood-PDF uploads need it too.
- **Bulk import on mobile** — explicitly out of scope. If a future
  product cycle wants paste-list import on phones, it lands as
  IMPL-AND-06b and depends on the backend endpoints already shipped by
  IMPL-GYM-002.
