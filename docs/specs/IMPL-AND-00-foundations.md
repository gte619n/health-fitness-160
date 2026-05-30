# IMPL-AND-00: Android foundations (DI, navigation, network, UI primitives)

## Goal

Land the architectural foundation that every other Android parity spec
(IMPL-AND-01 through -06) builds on. This IMPL turns the current ad-hoc
Compose scaffold — manual graph wiring in `MainActivity`, an `if/else`
routing in `AppRoot`, and zero live network calls — into a real
multi-module Android app with Hilt-driven DI, a single typed
Navigation-Compose graph, an authenticated Retrofit/OkHttp/Moshi stack
keyed to the existing `IdTokenCache`, Coil-backed image loading, and a
shared set of state / editing primitives in `core-ui`. No new user-
visible feature ships here; the goal is that the next IMPL can write
`@HiltViewModel`s, declare a `Route` entry, drop a Retrofit interface in
`core-data`, and have everything wire itself.

## Scope

In scope:

- **Hilt 2.52 integration** across `app`, `wear`, `core-data`, and the
  feature modules. `@HiltAndroidApp` Application classes on both phone
  and wear. Manual coordinator wiring in `MainActivity` is replaced by
  `@HiltViewModel` + `hiltViewModel()`.
- **Single Navigation-Compose graph** at
  `app/.../navigation/AppNavGraph.kt` with sealed-class `Route` entries
  for every top-level destination (Today, Body, Blood, Workouts,
  Medications, Settings, plus placeholder leaves for nested detail
  screens added by later IMPLs). Bottom nav (compact) and the foldable
  sidebar (medium/expanded) both `navController.navigate(Route.X)` into
  this graph.
- **New `core-network` module** owning OkHttp, Retrofit, Moshi, the
  bearer-token interceptor, a `TokenAuthenticator` that performs a
  silent refresh on 401 and retries once, a small SSE consumer helper,
  and `BackendBaseUrlProvider` that reads `BuildConfig.BACKEND_BASE_URL`
  per flavor.
- **Build flavors** (`dev`, `staging`, `prod`) on `app` and `wear` so
  the backend base URL is a compile-time constant per build. `dev`
  points at `http://10.0.2.2:8080` (the emulator → host loopback for
  the local backend started by `infra/scripts/dev.sh`); `staging` and
  `prod` point at the deployed Cloud Run URLs.
- **Coil 2.7 image loading**: a singleton `ImageLoader` provided via
  `ImageLoaderFactory` on the Application, plus a `HfAsyncImage`
  wrapper composable in `core-ui` that applies the project's
  placeholder, error, and crossfade treatment.
- **`core-ui` shared state primitives**: `LoadingState`, `EmptyState`,
  `ErrorState` composables matching the existing `HealthFitnessTheme`,
  a `SnackbarController` provided via `CompositionLocal`, and an
  `EditableNumber` composable mirroring web's click-to-edit pattern
  (used heavily by DEXA in IMPL-AND-05).
- **ViewModel + Repository conventions** documented and demonstrated by
  migrating `AuthCoordinator` to `AuthViewModel`. A reusable
  `XUiState` sealed-interface shape (`Loading` / `Loaded(...)` /
  `Error(message)`) and a `DispatcherModule` (so tests can swap
  coroutine dispatchers).
- **`applicationCoroutineScope`** (SupervisorJob + Main.immediate) on
  the Application, exposed as a Hilt-qualified `CoroutineScope` for
  fire-and-forget work that must outlive a screen.

Out of scope (deferred):

- Specific Retrofit service interfaces per domain. Each feature IMPL
  (AND-01..06) adds its own service in `core-data` or its feature
  module. This IMPL ships `NetworkModule` and the auth plumbing only.
- Room entities/DAOs. Backend stays the source of truth; Room caches
  land per-feature when an actual offline-read need appears. The
  classpath dep is already present (IMPL-03 era).
- PDF viewing / multipart upload / file-picker contracts — deferred to
  the first feature that needs them (IMPL-AND-04 blood).
- Push notifications, deep links, accessibility audit — separate
  workstreams.
- Migrating `feature-workouts`, `feature-medical`, `feature-chat` away
  from their `.gitkeep` placeholders — those modules just gain a
  `dependencies { implementation(project(":core-network")) }` line so
  Hilt's aggregating task can see them.

## Decisions

| Topic | Decision |
|---|---|
| DI framework | Hilt 2.52 (already on classpath, plugin already applied to `app/`). Activate by annotating Applications and switching ViewModels. No Anvil / Koin. |
| Hilt graph boundary | Phone Application and Wear Application are separate Hilt graphs. They share no `@Singleton` state — token relay between them already uses the Wearable Data Layer, not a shared DI container. |
| Navigation library | Navigation-Compose 2.8.4 (already in `libs.versions.toml`). Use **type-safe routes** via the `@Serializable` `Route` sealed hierarchy — Nav 2.8 supports `kotlinx.serialization`-driven typed nav. Add `kotlinx-serialization` plugin to `app`. |
| Single graph | One `AppNavGraph` `NavHost` lives in `app`. Feature modules expose top-level `composable` extension functions (`NavGraphBuilder.bloodGraph(navController)` etc.) that the app aggregates. Feature modules do not own a `NavHost`. |
| Bottom nav vs sidebar | Both drive the same `navController`. The compact/foldable choice is a layout concern, not a routing concern. `currentBackStackEntryAsState()` derives the selected item. |
| Network stack | Retrofit 2.11 + Moshi 1.15 + OkHttp 4.12, all already on `core-data`'s classpath. Move them to a new `core-network` module so feature modules can depend on the network surface without inheriting Room / DataStore. |
| New module name | `core-network`. Separate from `core-data` because `core-data` will, post-IMPL-AND-01, grow Room DAOs and feature repositories; the network primitives are stable plumbing and should not be re-compiled every time a Room migration lands. |
| Auth injection | OkHttp `Interceptor` reads the cached ID token synchronously via `runBlocking { idTokenCache.read() }`. The `read()` call is a DataStore `first()` and completes in microseconds when the value is in memory; the cost is acceptable for the request-path. |
| 401 handling | OkHttp `Authenticator` (not interceptor) — that's the contract OkHttp documents for credentials renewal. On 401: call `GoogleAuthRepository.silentRefresh()`, write the result through `IdTokenCache`, and resubmit the original request once. If refresh also fails, return `null` (OkHttp surfaces the 401 to the caller). |
| Retry budget | One refresh attempt per request. A `Request.Builder()` tag tracks attempts so we never loop. |
| SSE consumer | OkHttp `EventSource.Factory` from `okhttp-sse` (new transitive dep). Wrapped in a tiny `suspend fun streamSse(url, body): Flow<SseEvent>` helper. Used by blood upload (IMPL-AND-04), DEXA upload (IMPL-AND-05), drug lookup (IMPL-AND-03). |
| Backend base URL | `BuildConfig.BACKEND_BASE_URL` per flavor. No runtime override, no env var read at startup. Switching environments means switching build variant in Android Studio (`devDebug` / `stagingDebug` / `prodRelease`). |
| Dev flavor URL | `http://10.0.2.2:8080`. Hardcoded — the emulator-to-host loopback IP is stable. Real devices in dev mode use `staging`. |
| Staging / prod URLs | Read from `local.properties` keys `BACKEND_URL_STAGING` and `BACKEND_URL_PROD` at build time, with hardcoded defaults pointing at the current Cloud Run URLs. `local.properties` is already in `.gitignore`. |
| `cleartextTraffic` | Phone `dev` flavor sets `usesCleartextTraffic="true"` and a flavor-scoped `network_security_config.xml` that allows cleartext only to `10.0.2.2`. `staging`/`prod` keep the default (HTTPS-only). |
| ViewModel state shape | `sealed interface XUiState { data object Loading; data class Loaded(...); data class Error(message: String) }`. No third "Empty" state — empty is `Loaded` with an empty payload, and the screen decides whether to render `EmptyState`. |
| Repository surface | Interface in `core-domain`, Retrofit-backed impl in `core-data` (or in a feature module, when feature-scoped). `core-domain` stays pure Kotlin — no Android imports, no Retrofit imports. |
| Dispatcher injection | `@Qualifier IoDispatcher`, `@Qualifier DefaultDispatcher`, `@Qualifier MainDispatcher` provided by `DispatcherModule` (in `core-data`). All suspending repository calls use the injected `IoDispatcher`. |
| App-scope coroutines | `@Qualifier ApplicationScope` provides a `CoroutineScope(SupervisorJob() + Main.immediate)` owned by the Application. Used for fire-and-forget work (e.g., the wear-token publish on auth bootstrap). |
| Image loading | Coil 2.7 (already on classpath in `app/`). `core-ui` gets a Coil dependency and exposes `HfAsyncImage`. Phone-only — wear doesn't fetch network images in this IMPL. |
| Snackbar surfacing | `SnackbarController` is a `CompositionLocal`-provided object backed by a `Channel<SnackbarMessage>`. Scaffold subscribes; any composable in the tree can call `LocalSnackbarController.current.show(msg)`. |
| Module visibility | `core-network` depends on `core-domain` only (for the `AuthTokenProvider` interface it consumes). It does **not** depend on `core-data` — that would create a cycle with `core-data` → `core-network`. The interface bridges the cycle. |
| Tests | JVM unit tests for everything testable without a device: MockWebServer for interceptor/authenticator, Robolectric not required. The two preview-driven state composables get screenshot-style `@Preview` annotations only — full Paparazzi/Roborazzi is deferred. |
| Compose previews | All new composables in `core-ui` ship at least one `@Preview` so they show up in Android Studio's preview pane. |

## Module deliverables

### `app/` — phone

#### `app/src/main/java/com/gte619n/healthfitness/mobile/HealthFitnessApp.kt`

```kotlin
@HiltAndroidApp
class HealthFitnessApp : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoaderProvider: Provider<ImageLoader>

    override fun newImageLoader(): ImageLoader = imageLoaderProvider.get()
}
```

Register the class in `AndroidManifest.xml`:

```xml
<application
    android:name=".HealthFitnessApp"
    ... >
```

#### `app/src/main/java/com/gte619n/healthfitness/mobile/MainActivity.kt`

`MainActivity` is annotated `@AndroidEntryPoint`. Manual construction
of `IdTokenCache`, `GoogleAuthRepository`, `PhoneTokenPublisher`, and
`AuthCoordinator` is removed. The activity body shrinks to fold-state
observation, theme + window-size-class, and a `NavHost`.

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeFoldState()
        setContent {
            HealthFitnessTheme {
                val windowSize = calculateWindowSizeClass(this)
                ProvideSnackbarController {
                    AppRoot(widthClass = windowSize.widthSizeClass)
                }
            }
        }
    }

    // observeFoldState() body unchanged from IMPL-02.
}

@Composable
private fun AppRoot(widthClass: WindowWidthSizeClass) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val state by authViewModel.uiState.collectAsState()
    when (state) {
        is AuthUiState.SignedIn -> SignedInScaffold(widthClass)
        AuthUiState.Loading     -> SignInScreen(state = AuthState.Loading, onSignIn = {})
        else -> SignInScreen(
            state = state.toLegacyAuthState(),
            onSignIn = { authViewModel.interactiveSignIn() },
        )
    }
}
```

#### `app/src/main/java/com/gte619n/healthfitness/mobile/auth/AuthViewModel.kt`

The `AuthCoordinator` body moves verbatim into this `ViewModel`. The
constructor signature stays the same — Hilt provides the dependencies.

```kotlin
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: GoogleAuthRepository,
    private val cache: IdTokenCache,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState

    init { viewModelScope.launch { bootstrap() } }

    fun interactiveSignIn() { viewModelScope.launch { /* repo.interactiveSignIn() */ } }
    fun signOut()           { viewModelScope.launch { repo.signOut(); _uiState.value = AuthUiState.SignedOut } }

    private suspend fun bootstrap() { /* moved from AuthCoordinator.bootstrap() */ }
}

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data class SignedIn(val userId: String, val email: String?, val displayName: String?) : AuthUiState
    data class Failed(val message: String) : AuthUiState
}
```

`AuthCoordinator.kt` is deleted; `SignInScreen` keeps its current
signature (a small `state.toLegacyAuthState()` shim avoids touching the
sign-in UI in this IMPL).

#### `app/src/main/java/com/gte619n/healthfitness/mobile/navigation/Route.kt`

```kotlin
@Serializable
sealed interface Route {
    @Serializable data object Today : Route
    @Serializable data object Body : Route
    @Serializable data object Blood : Route
    @Serializable data object Workouts : Route
    @Serializable data object Medications : Route
    @Serializable data object Settings : Route

    // Detail leaves wired by later IMPLs but declared here so the graph
    // is the single source of truth for the route surface.
    @Serializable data class DexaDetail(val scanId: String) : Route
    @Serializable data class BloodReportDetail(val reportId: String) : Route
    @Serializable data class MedicationDetail(val medicationId: String) : Route
    @Serializable data class GymDetail(val gymId: String) : Route
}
```

#### `app/src/main/java/com/gte619n/healthfitness/mobile/navigation/AppNavGraph.kt`

```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Today,
        modifier = modifier,
    ) {
        composable<Route.Today>       { PhoneTodayScreen() }
        composable<Route.Body>        { PlaceholderScreen("Body") }
        composable<Route.Blood>       { PlaceholderScreen("Blood") }
        composable<Route.Workouts>    { PlaceholderScreen("Workouts") }
        composable<Route.Medications> { PlaceholderScreen("Medications") }
        composable<Route.Settings>    { PlaceholderScreen("Settings") }

        composable<Route.DexaDetail>          { PlaceholderScreen("DEXA detail") }
        composable<Route.BloodReportDetail>   { PlaceholderScreen("Blood report") }
        composable<Route.MedicationDetail>    { PlaceholderScreen("Medication") }
        composable<Route.GymDetail>           { PlaceholderScreen("Gym") }
    }
}
```

`PlaceholderScreen` is a one-liner that renders the destination name in
`Hf.type.headingLg` and a hint that "this screen ships in IMPL-AND-XX".
Each later IMPL replaces a `PlaceholderScreen` call with its real
screen.

#### `app/src/main/java/com/gte619n/healthfitness/mobile/navigation/SignedInScaffold.kt`

Hosts the `NavHost` and decides bottom-nav vs sidebar based on
`widthClass`. Replaces the current `DashboardRoot` `when` block.

```kotlin
@Composable
fun SignedInScaffold(widthClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarController.current.hostState) },
        bottomBar = {
            if (widthClass == WindowWidthSizeClass.Compact) BottomNavBar(navController)
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (widthClass != WindowWidthSizeClass.Compact) FoldableSidebar(navController)
            AppNavHost(navController, Modifier.weight(1f))
        }
    }
}
```

The existing `BottomNav` block from `PhoneTodayScreen.kt` and the
`FoldableSidebar` from `FoldableDashboardScreen.kt` are extracted into
their own files in this package and rewritten to take a
`NavHostController` plus the route list defined in `Route.kt` (instead
of `DashboardFixtures.phoneBottomNav` / `foldableNav`).

#### `app/src/main/java/com/gte619n/healthfitness/mobile/di/AppModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun idTokenCache(@ApplicationContext ctx: Context): IdTokenCache = IdTokenCache(ctx)

    @Provides @Singleton
    fun googleAuthRepository(
        @ApplicationContext ctx: Context,
        cache: IdTokenCache,
        publisher: PhoneTokenPublisher,
    ): GoogleAuthRepository = GoogleAuthRepository(
        context = ctx,
        cache = cache,
        webOauthClientId = BuildConfig.WEB_OAUTH_CLIENT_ID,
        onTokenIssued = { token, _ -> publisher.publish(token) },
    )

    @Provides @Singleton
    fun phoneTokenPublisher(@ApplicationContext ctx: Context) = PhoneTokenPublisher(ctx)

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

@Qualifier @Retention(BINARY) annotation class ApplicationScope
```

#### `app/build.gradle.kts` changes

Add the `kotlinx-serialization` plugin and the new dependency on
`core-network`.

```kotlin
plugins {
    // existing plugins
    alias(libs.plugins.kotlin.serialization)
}

android {
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "BACKEND_BASE_URL", "\"http://10.0.2.2:8080/\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_dev"
        }
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            val url = (project.findProperty("BACKEND_URL_STAGING") as String?)
                ?: "https://hf-backend-staging-XXXX.us-central1.run.app/"
            buildConfigField("String", "BACKEND_BASE_URL", "\"$url\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_release"
        }
        create("prod") {
            dimension = "env"
            val url = (project.findProperty("BACKEND_URL_PROD") as String?)
                ?: "https://hf-backend-XXXX.us-central1.run.app/"
            buildConfigField("String", "BACKEND_BASE_URL", "\"$url\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_release"
        }
    }
}

dependencies {
    implementation(project(":core-network"))
    // existing project + library dependencies
}
```

Update `AndroidManifest.xml` `<application>` tag:

```xml
<application
    android:name=".HealthFitnessApp"
    android:usesCleartextTraffic="${usesCleartextTraffic}"
    android:networkSecurityConfig="${networkSecurityConfig}"
    ... >
```

#### `app/src/dev/res/xml/network_security_config_dev.xml`

Permits cleartext only to `10.0.2.2`. Other flavors get a `release`
config that disables cleartext entirely (under `app/src/main/res/xml/`).

### `core-network/` — new module

#### `core-network/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gte619n.healthfitness.network"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}

dependencies {
    implementation(project(":core-domain"))

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

#### `core-network/src/main/java/com/gte619n/healthfitness/network/AuthTokenProvider.kt`

```kotlin
package com.gte619n.healthfitness.network

interface AuthTokenProvider {
    suspend fun currentToken(): String?
    suspend fun refresh(): String?
}
```

`core-data`'s `IdTokenCache` + `GoogleAuthRepository` implement this
via an adapter in `core-data` (so `core-network` doesn't take a
dependency on `core-data`).

#### `core-network/src/main/java/com/gte619n/healthfitness/network/AuthInterceptor.kt`

```kotlin
class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = runBlocking { tokenProvider.currentToken() }
        val authorized = if (token.isNullOrBlank()) request
            else request.newBuilder().header("Authorization", "Bearer $token").build()
        return chain.proceed(authorized)
    }
}
```

#### `core-network/src/main/java/com/gte619n/healthfitness/network/TokenAuthenticator.kt`

```kotlin
class TokenAuthenticator(
    private val tokenProvider: AuthTokenProvider,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header(RETRY_TAG) != null) return null
        val refreshed = runBlocking { tokenProvider.refresh() } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $refreshed")
            .header(RETRY_TAG, "1")
            .build()
    }

    private companion object { const val RETRY_TAG = "X-HF-Auth-Retry" }
}
```

#### `core-network/src/main/java/com/gte619n/healthfitness/network/BackendBaseUrlProvider.kt`

```kotlin
interface BackendBaseUrlProvider { val baseUrl: String }
```

Implementation lives in `app/` and `wear/` (each module knows its own
`BuildConfig.BACKEND_BASE_URL`):

```kotlin
class AppBackendBaseUrlProvider @Inject constructor() : BackendBaseUrlProvider {
    override val baseUrl: String = BuildConfig.BACKEND_BASE_URL
}
```

#### `core-network/src/main/java/com/gte619n/healthfitness/network/NetworkModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun moshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides @Singleton
    fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    @Provides @Singleton
    fun authInterceptor(tokenProvider: AuthTokenProvider): AuthInterceptor =
        AuthInterceptor(tokenProvider)

    @Provides @Singleton
    fun tokenAuthenticator(tokenProvider: AuthTokenProvider): TokenAuthenticator =
        TokenAuthenticator(tokenProvider)

    @Provides @Singleton
    fun okHttpClient(
        auth: AuthInterceptor,
        logging: HttpLoggingInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(auth)
        .addInterceptor(logging)
        .authenticator(authenticator)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, moshi: Moshi, base: BackendBaseUrlProvider): Retrofit =
        Retrofit.Builder()
            .baseUrl(base.baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides @Singleton
    fun sseFactory(client: OkHttpClient): EventSource.Factory =
        EventSources.createFactory(client)
}
```

#### `core-network/src/main/java/com/gte619n/healthfitness/network/sse/SseConsumer.kt`

```kotlin
sealed interface SseEvent {
    data class Data(val name: String?, val payload: String) : SseEvent
    data object Open : SseEvent
    data class Failure(val cause: Throwable?, val response: Response?) : SseEvent
    data object Closed : SseEvent
}

class SseConsumer @Inject constructor(
    private val factory: EventSource.Factory,
) {
    fun stream(request: Request): Flow<SseEvent> = callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onOpen(es: EventSource, r: Response) { trySend(SseEvent.Open) }
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                trySend(SseEvent.Data(name = type, payload = data))
            }
            override fun onClosed(es: EventSource) { trySend(SseEvent.Closed); close() }
            override fun onFailure(es: EventSource, t: Throwable?, r: Response?) {
                trySend(SseEvent.Failure(t, r)); close(t)
            }
        }
        val source = factory.newEventSource(request, listener)
        awaitClose { source.cancel() }
    }
}
```

### `core-data/` — repository + dispatcher wiring

#### `core-data/src/main/java/com/gte619n/healthfitness/data/auth/IdTokenCacheAuthTokenProvider.kt`

Bridges `IdTokenCache` + `GoogleAuthRepository` to the
`AuthTokenProvider` contract `core-network` consumes.

```kotlin
class IdTokenCacheAuthTokenProvider @Inject constructor(
    private val cache: IdTokenCache,
    private val repo: GoogleAuthRepository,
) : AuthTokenProvider {

    override suspend fun currentToken(): String? = cache.read().idToken

    override suspend fun refresh(): String? {
        val state = repo.silentRefresh()
        return (state as? AuthState.SignedIn)?.idToken
    }
}
```

#### `core-data/src/main/java/com/gte619n/healthfitness/data/di/DispatcherModule.kt`

```kotlin
@Qualifier @Retention(BINARY) annotation class IoDispatcher
@Qualifier @Retention(BINARY) annotation class DefaultDispatcher
@Qualifier @Retention(BINARY) annotation class MainDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides @IoDispatcher      fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun default(): CoroutineDispatcher = Dispatchers.Default
    @Provides @MainDispatcher    fun main(): CoroutineDispatcher = Dispatchers.Main.immediate
}
```

#### `core-data/src/main/java/com/gte619n/healthfitness/data/di/DataModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds @Singleton
    abstract fun bindAuthTokenProvider(impl: IdTokenCacheAuthTokenProvider): AuthTokenProvider
}
```

#### `core-data/build.gradle.kts` changes

- Drop Retrofit/Moshi/OkHttp dependencies — they belong to
  `core-network` now. Keep Room and DataStore.
- Add `implementation(project(":core-network"))` so the
  `AuthTokenProvider` adapter compiles.
- Apply the Hilt plugin: `alias(libs.plugins.hilt)`.

### `core-ui/` — state + editing primitives

#### `core-ui/src/main/java/com/gte619n/healthfitness/ui/state/LoadingState.kt`

```kotlin
@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Hf.colors.accent, strokeWidth = 2.dp)
        if (label != null) {
            Spacer(Modifier.height(12.dp))
            Text(label, style = Hf.type.bodyMd, color = Hf.colors.textTertiary)
        }
    }
}

@Preview @Composable private fun LoadingPreview() =
    HealthFitnessTheme { LoadingState(label = "Loading...") }
```

#### `core-ui/src/main/java/com/gte619n/healthfitness/ui/state/EmptyState.kt`

```kotlin
@Composable
fun EmptyState(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) { /* themed empty layout */ }
```

#### `core-ui/src/main/java/com/gte619n/healthfitness/ui/state/ErrorState.kt`

```kotlin
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) { /* themed error layout with optional retry CTA */ }
```

#### `core-ui/src/main/java/com/gte619n/healthfitness/ui/snackbar/SnackbarController.kt`

```kotlin
data class SnackbarMessage(val text: String, val isError: Boolean = false)

class SnackbarController internal constructor(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
    private val channel: Channel<SnackbarMessage>,
) {
    fun show(message: SnackbarMessage) { scope.launch { channel.send(message) } }
    fun show(text: String, isError: Boolean = false) = show(SnackbarMessage(text, isError))
}

val LocalSnackbarController = staticCompositionLocalOf<SnackbarController> {
    error("SnackbarController not provided — wrap with ProvideSnackbarController")
}

@Composable
fun ProvideSnackbarController(content: @Composable () -> Unit) {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val channel = remember { Channel<SnackbarMessage>(capacity = Channel.BUFFERED) }
    val controller = remember(hostState, scope) { SnackbarController(hostState, scope, channel) }
    LaunchedEffect(channel) {
        for (msg in channel) { hostState.showSnackbar(msg.text) }
    }
    CompositionLocalProvider(LocalSnackbarController provides controller, content = content)
}
```

#### `core-ui/src/main/java/com/gte619n/healthfitness/ui/input/EditableNumber.kt`

Mirrors web's click-to-edit pattern. Tap to switch from a static
`Text` to an inline `BasicTextField`; commit on focus loss or `Enter`.

```kotlin
@Composable
fun EditableNumber(
    value: Double?,
    onCommit: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimals: Int = 1,
    enabled: Boolean = true,
    placeholder: String = "—",
) { /* state: View(value) | Editing(text). Validates on commit, reverts on parse failure. */ }
```

#### `core-ui/src/main/java/com/gte619n/healthfitness/ui/image/HfAsyncImage.kt`

```kotlin
@Composable
fun HfAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = Hf.colors.canvasMuted,
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.background(placeholderColor),
        contentScale = contentScale,
        // Coil's defaults are reused; crossfade is enabled via the
        // singleton ImageLoader built in HealthFitnessApp.
    )
}
```

#### `core-ui/build.gradle.kts` changes

- Add `implementation(libs.coil.compose)`.
- Add `implementation(libs.kotlinx.coroutines.android)` (already
  transitive but make it direct for the snackbar controller).

### `wear/` — wear-side Hilt graph

#### `wear/src/main/java/com/gte619n/healthfitness/wear/HealthFitnessWearApp.kt`

```kotlin
@HiltAndroidApp
class HealthFitnessWearApp : Application()
```

Register in `wear/src/main/AndroidManifest.xml` (`android:name=".HealthFitnessWearApp"`).

#### `wear/src/main/java/com/gte619n/healthfitness/wear/MainActivity.kt`

Add `@AndroidEntryPoint`. The existing body stays — wear doesn't get a
NavHost in this IMPL (there's nowhere to navigate). Hilt is introduced
so future IMPLs can inject `AuthTokenProvider`-style dependencies for
the wear's own Retrofit instance (added in IMPL-AND-08 when wear
surfaces land).

#### `wear/src/main/java/com/gte619n/healthfitness/wear/di/WearAuthModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object WearAuthModule {
    @Provides @Singleton
    fun wearIdTokenCache(@ApplicationContext ctx: Context) = WearIdTokenCache(ctx)
}
```

`wear/build.gradle.kts` gains the Hilt plugin, the Hilt + KSP
dependencies, and the same `dev`/`staging`/`prod` flavor block as
phone (with the same `BACKEND_BASE_URL` rules). `core-network` is
**not** depended on from wear in this IMPL — wear has nothing to call
yet — but the flavor scaffolding is added now so the wear-side
network module can drop in later without a Gradle re-shuffle.

### Feature modules (`feature-workouts`, `feature-medical`, `feature-chat`)

No code change. Each `build.gradle.kts` adds:

```kotlin
plugins {
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core-network"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

This guarantees Hilt's aggregating annotation processor walks every
module in the graph, even before they contain code.

## Gradle changes

### `android/gradle/libs.versions.toml`

```toml
[versions]
# new:
kotlinSerialization = "1.7.3"
okhttpSse = "4.12.0"          # same version as okhttp
mockwebserver = "4.12.0"
coroutinesTest = "1.9.0"
turbine = "1.1.0"

[libraries]
# new:
okhttp-sse = { module = "com.squareup.okhttp3:okhttp-sse", version.ref = "okhttpSse" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "mockwebserver" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesTest" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinSerialization" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
# new:
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`hilt`, `hilt-navigation-compose`, `coil-compose`, `navigation-compose`
already exist — no changes to their entries.

### `android/settings.gradle.kts`

```kotlin
include(
    ":app",
    ":wear",
    ":core-data",
    ":core-domain",
    ":core-network",   // new
    ":core-ui",
    ":core-health",
    ":feature-workouts",
    ":feature-medical",
    ":feature-chat",
)
```

### `android/build.gradle.kts` (root)

No change required — plugins are applied per-module via the version
catalog aliases.

## Tests

All tests live under `core-network/src/test/...` (pure JVM, no
Android instrumentation) unless explicitly called out.

- `AuthInterceptorTest` — MockWebServer. Cases:
  1. Token present → request carries `Authorization: Bearer <token>`.
  2. Token null → request goes out unauthenticated (server can still
     return 401, which routes into `TokenAuthenticator`).
  3. Whitespace token → treated as missing.
- `TokenAuthenticatorTest` — MockWebServer.
  1. 401 → `tokenProvider.refresh()` invoked once → request resent
     with refreshed bearer → 200.
  2. 401 then refresh returns `null` → no retry, original 401 surfaces.
  3. 401 on the retried request → no second refresh; original 401
     surfaces (no infinite loop).
- `NetworkModuleTest` — builds the Hilt graph in a test
  `@HiltAndroidTest`-style fixture (or by manually invoking the
  `@Provides` methods if Hilt-testing setup is too heavy) and asserts
  that `OkHttpClient` has both the interceptor and authenticator wired,
  and that `Retrofit` resolves the configured base URL.
- `SseConsumerTest` — MockWebServer that emits a chunked
  `text/event-stream` body. Asserts:
  1. `Open` event is the first emission.
  2. Each `data:` line is surfaced as `SseEvent.Data`.
  3. Closing the stream emits `Closed` and completes the flow.
- `core-ui/src/test/.../EditableNumberTest.kt` — JUnit + Compose UI
  test (Robolectric, since these run on the JVM): parse failure
  reverts to the prior value; numeric commit calls `onCommit` with the
  parsed `Double`; clearing the field commits `null`.
- `core-ui` `@Preview` annotations on every new state composable are
  treated as a test by the IDE's preview pane; no automated screenshot
  diffing in this IMPL.

`app/` keeps its existing `junit` test dependency. No new tests land
in `app/` for this IMPL — the meaningful logic lives in `core-network`
and `core-ui`.

## Acceptance

1. `./gradlew :core-network:test` passes, including all interceptor,
   authenticator, and SSE tests above.
2. `./gradlew :app:assembleDevDebug :app:assembleStagingDebug
   :app:assembleProdRelease :wear:assembleDevDebug` all complete with
   `BUILD SUCCESSFUL` on a clean checkout.
3. Launching the `devDebug` variant against a backend started by
   `bash infra/scripts/dev.sh` shows: sign-in flow unchanged from
   IMPL-02, dashboard renders (still backed by `DashboardFixtures`),
   bottom-nav taps switch destinations via the new `NavHost`
   (placeholder screens for Body/Blood/Workouts/Medications/Settings).
4. `MainActivity.kt` no longer constructs `IdTokenCache`,
   `GoogleAuthRepository`, `PhoneTokenPublisher`, or
   `AuthCoordinator` directly — those bindings come from Hilt.
5. `grep -r "AuthCoordinator" android/app/src` returns no matches; the
   class is deleted and `AuthViewModel` replaces it.
6. `grep -r "if (state is AuthState.SignedIn) {" android/app/src`
   returns no matches; the routing decision lives entirely inside the
   `NavHost`.
7. A test Retrofit call from a throwaway scratch screen
   (`GET /api/me`) succeeds in `devDebug` and the `Authorization:
   Bearer ...` header is present in the OkHttp log. Manually
   invalidating the cached token (clearing DataStore) and re-issuing
   the call shows: 401 → silent refresh → retried request returns
   200, all within one user-visible call.
8. Coil renders a remote image (e.g., `https://picsum.photos/200`) via
   `HfAsyncImage` in a preview composable without configuring an
   `ImageLoader` explicitly — the singleton wired by
   `HealthFitnessApp` is picked up.
9. `core-network` has no dependency on `core-data` (verified by
   `./gradlew :core-network:dependencies` showing no `:core-data`
   entry); the `AuthTokenProvider` interface lives in `core-network`,
   and its impl lives in `core-data`.
10. `wear/` builds with Hilt active and the wear app still launches to
    `SignInRequiredScreen` / `WearHelloScreen` based on cached token
    presence (no functional regression from IMPL-02).

## Open questions resolved before implementation

- **DI framework** — Hilt 2.52. Already on classpath.
- **Routing library** — Navigation-Compose 2.8.4 with typed routes via
  `kotlinx.serialization`. Already in `libs.versions.toml`.
- **Network module placement** — New `core-network` module, not an
  extension of `core-data`. Keeps the network surface decoupled from
  the Room/DataStore surface that will grow under `core-data`.
- **Backend URL configuration** — `BuildConfig.BACKEND_BASE_URL` per
  flavor (`dev` / `staging` / `prod`). No runtime override.
- **Cleartext traffic in dev** — Allowed only for `10.0.2.2` via a
  flavor-scoped network security config. Real devices use `staging`.
- **Auth token bridging** — `AuthTokenProvider` interface in
  `core-network`, adapter in `core-data`. Breaks the otherwise-circular
  dependency between the two modules.
- **401 retry policy** — One refresh + one retry per request. Tracked
  via a request header tag so even concurrent failures cannot loop.
- **Wear DI** — Separate Hilt graph from phone. No shared bindings;
  shared state (the ID token) crosses the gap via the Wearable Data
  Layer, not Hilt.
- **`AuthCoordinator` deletion vs deprecation** — Deleted outright.
  Body moves verbatim into `AuthViewModel`; no callers outside
  `MainActivity` reference it today.
- **Feature module Hilt activation** — Each empty feature module gets
  the Hilt + KSP plugins now even though it has no code, so the
  aggregating processor's module list is stable. Avoids a re-trigger
  the first time a feature IMPL adds an `@HiltViewModel`.

## Open questions deferred to implementation

- **Coil disk cache size** — Default (250 MB) is almost certainly
  fine; revisit if drug/equipment image catalogs end up larger than
  expected. No `@Provides` override planned in this IMPL.
- **OkHttp logging level in `prod`** — Currently `BASIC` everywhere.
  Acceptable while the app is pre-launch; downgrade to `NONE` for
  `prod` once we have telemetry that proves we don't need request
  logs in user devices.
- **Robolectric for `EditableNumber`** — If Robolectric's compose
  support is flaky on Compose 1.7, drop the test for now; the
  composable's behavior will get covered indirectly the first time a
  feature IMPL uses it in a screen.
- **`hilt-work` for WorkManager** — Not needed until push-notification
  / background-sync workstreams land. No plugin added in this IMPL.
- **Per-screen back-stack restoration policy** — Navigation Compose's
  default is fine; feature IMPLs that need state restoration (e.g.,
  scroll position in a list after detail-back) declare it then.
- **Detail-screen typed args using `@Serializable` `data class`** —
  Works for primitives. If a future IMPL needs to pass a complex
  object across the graph it gets a `NavType` adapter; for now every
  declared `data class Route` argument is a `String`.
