# IMPL-AND-02: Android Settings & Profile

## Goal

Land a Settings surface on the Android phone app reachable from the
phone's "More" tab and the foldable sidebar's settings icon. Settings
hosts three sub-sections — Profile, Google Health connection, About —
plus a Sign-out action. The marquee feature is the **Google Health
connection flow**, which mirrors the web's incremental-authorization
path from IMPL-04 (`access_type=offline` + `prompt=consent` →
refresh_token → backend KMS-encrypts and stores it). Android cannot use
Credential Manager for the additional scope; it uses the Google Identity
Services `AuthorizationClient` to obtain a server auth code which the
backend exchanges for a refresh_token, matching the web flow's
server-side token shape exactly.

## Scope

In scope:

- New `feature-settings/` module hosting `SettingsScreen`,
  `ProfileScreen`, and `GoogleHealthSection` composables plus their
  ViewModels.
- `core-auth` extensions for the Google Health scope upgrade flow via
  `AuthorizationClient`.
- `core-data` Retrofit services for `ProfileService` and
  `GoogleHealthService`; `core-domain` interfaces and models for
  `Profile`, `HeightMetric`, and `GoogleHealthStatus`.
- Navigation wiring: `SettingsRoute`, `ProfileRoute`, and routing from
  both the phone's "More" tab destination and the foldable sidebar
  settings icon (currently inert in `FoldableDashboardScreen.kt`).
- Sign-out action that calls existing `GoogleAuthRepository.signOut()`,
  invalidates the in-memory `AuthCoordinator`, and routes back to
  `SignInScreen`.

Out of scope (deferred):

- Notification settings (push channels, dose reminders) — lands with
  IMPL-AND-03 medications.
- Theme / dark-mode toggle — currently no theme alternates exist in
  `core-ui`.
- Unit preferences (lb/kg, in/cm) — cross-cutting with web; needs an
  ADR before either side ships.
- Wear OS settings UI. Sign-out on wear is implicit: the phone-to-wear
  token relay stops publishing once the phone signs out, and the wear
  app already handles "no token" as a placeholder state.
- Admin gating affordances in Settings. Admin surfaces on Android are
  out of scope per the roadmap (`android-web-parity-roadmap.md` §2.8).
- Disconnecting on Google's side (revoking the OAuth grant). Same as
  IMPL-04: the user does that via their Google Account UI; we only
  clear the server-side connection record.

## Decisions

| Topic | Decision |
|---|---|
| **Health scope acquisition** | `AuthorizationClient.authorize()` from `com.google.android.gms:play-services-auth`. Credential Manager's `GetGoogleIdOption` does not support requesting additional OAuth scopes or `access_type=offline` — it returns an ID token only, not an OAuth grant. `AuthorizationClient` is Google's documented Android path for incremental OAuth and is what supports `requestOfflineAccess(webOauthClientId)` to produce a server auth code. |
| **Token exchange location** | Backend, not Android. `AuthorizationClient.requestOfflineAccess()` returns a **server auth code**; we POST that to the backend, which exchanges it for `refresh_token` + `access_token` against `https://oauth2.googleapis.com/token` using the existing web OAuth client's `id` + `secret` (the same credentials IMPL-04 already wires for the refresh→access exchange). Doing the exchange on-device would require shipping `OAUTH_WEB_CLIENT_SECRET` to the APK — unacceptable. |
| **Request body shape** | `POST /api/me/google-health/connect` accepts an **additional** body shape `{ serverAuthCode: string }` alongside the existing `{ refreshToken, accessToken }` web shape. Controller branches on which field is present. Both branches converge on `KmsTokenCipher.encrypt` + `recordGoogleHealthConnection`. Documented on the backend side as a small additive change in IMPL-04's controller. |
| **Repository placement** | Keep one auth repository per concern. Extend `GoogleAuthRepository` (in `core-data/data/auth`) with a sibling **`GoogleHealthScopeRepository`** class in the same package. Reason: `GoogleAuthRepository` owns the Credential Manager state machine for ID-token sign-in; mixing the AuthorizationClient flow into the same class would entangle two Google API surfaces (Credential Manager + GIS) with different threading and result-handling models. Sibling class shares no state with `GoogleAuthRepository` and is testable in isolation. |
| **Activity launching for authorization** | `AuthorizationClient.authorize()` returns an `AuthorizationResult`. When user consent is required, the result carries a `PendingIntent` — the calling Activity must launch it via `ActivityResultLauncher<IntentSenderRequest>`. The screen owns the launcher; the repository's `requestHealthAuthorization()` returns a `HealthAuthFlow` sealed type that's either `Resolved(serverAuthCode)` or `NeedsUserConsent(intentSender)`. Composable wires the launcher result back to the ViewModel. |
| **Profile endpoint** | Use the existing backend endpoint shape from IMPL-04 / current backend: `GET /api/me` returns `{ userId, email, displayName, heightCm }`; `PATCH /api/me` with `{ heightCm }` partial body updates height. The roadmap mentioned `POST /api/me/profile/height` but the backend's actual contract is `PATCH /api/me` — Android matches the backend, not the roadmap stub. |
| **Foldable settings icon → behavior** | Current `FoldableSidebar` renders a settings icon with `active = false` and no onClick. This IMPL wires it to `nav.navigate(SettingsRoute)`. Phone bottom nav exposes Settings as the "More" tab's destination (IMPL-AND-00 lands the "More" tab; this spec adds the destination it points at). |
| **ViewModel pattern** | Sealed `UiState` per screen: `Loading | Loaded(data) | Error(message)`. Repository interfaces in `core-domain`; Retrofit-backed impls in `core-data`. IMPL-AND-00 conventions, not redefined here. |
| **Health scope constant location** | `com.gte619n.healthfitness.data.auth.GoogleHealthScopes.METRICS_READ_ONLY = "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly"`. Single source of truth for Android; mirrors the constant `GOOGLE_HEALTH_SCOPE` in `web/auth.ts`. |
| **Sign-in retained on disconnect** | `DELETE /api/me/google-health/connect` clears the Google Health connection but does **not** sign the user out of the app. The user keeps their Google ID token; only the offline refresh token is purged server-side. Matches the web flow. |
| **Sign-out scope** | `signOut()` clears `IdTokenCache`, clears the Credential Manager state, and navigates back to `SignInScreen`. It does **not** call the backend — there's no server-side session to invalidate (JWT-only). |

## Per-module deliverables

### `core-domain/` (new domain models + repository interfaces)

- `core-domain/src/main/java/com/gte619n/healthfitness/domain/profile/Profile.kt`
  ```kotlin
  data class Profile(
      val userId: String,
      val email: String?,
      val displayName: String?,
      val heightCm: Int?,
  )
  ```
- `domain/profile/HeightMetric.kt` — display-time conversion helper.
  Backend stores cm; UI shows ft/in (US). Lives here so the ViewModel
  doesn't carry conversion math; mirrors the helpers in
  `web/components/profile/HeightForm.tsx`.
  ```kotlin
  object HeightMetric {
      const val CM_PER_INCH = 2.54
      const val INCHES_PER_FOOT = 12
      data class FtIn(val feet: Int, val inches: Int)
      fun cmToFtIn(cm: Int?): FtIn?           // null in → null out; rounds inches
      fun ftInToCm(feet: Int, inches: Int): Int
  }
  ```
- `core-domain/src/main/java/com/gte619n/healthfitness/domain/profile/ProfileRepository.kt`
  ```kotlin
  interface ProfileRepository {
      suspend fun get(): Result<Profile>
      suspend fun updateHeightCm(heightCm: Int?): Result<Profile>
  }
  ```
- `core-domain/src/main/java/com/gte619n/healthfitness/domain/googlehealth/GoogleHealthStatus.kt`
  ```kotlin
  data class GoogleHealthStatus(
      val connected: Boolean,
      val connectedAtEpochSeconds: Long?,
  )
  ```
- `core-domain/src/main/java/com/gte619n/healthfitness/domain/googlehealth/GoogleHealthRepository.kt`
  ```kotlin
  interface GoogleHealthRepository {
      suspend fun status(): Result<GoogleHealthStatus>
      suspend fun connectWithServerAuthCode(serverAuthCode: String): Result<Unit>
      suspend fun disconnect(): Result<Unit>
  }
  ```

### `core-data/` (Retrofit services + repository impls)

- `data/profile/ProfileService.kt` — Retrofit service:
  ```kotlin
  interface ProfileService {
      @GET("/api/me") suspend fun get(): ProfileDto
      @PATCH("/api/me") suspend fun patch(@Body body: PatchProfileBody): ProfileDto
  }
  @JsonClass(generateAdapter = true)
  data class ProfileDto(val userId: String, val email: String?, val displayName: String?, val heightCm: Int?)
  @JsonClass(generateAdapter = true)
  data class PatchProfileBody(val heightCm: Int?)
  ```
- `data/profile/ProfileRepositoryImpl.kt` — `runCatching { service.get().toDomain() }` /
  `runCatching { service.patch(PatchProfileBody(heightCm)).toDomain() }`.
  Private extension `fun ProfileDto.toDomain() = Profile(userId, email, displayName, heightCm)`.
- `data/googlehealth/GoogleHealthService.kt` — Retrofit service:
  ```kotlin
  interface GoogleHealthService {
      @GET("/api/me/google-health/status") suspend fun status(): GoogleHealthStatusDto
      @POST("/api/me/google-health/connect") suspend fun connect(@Body body: ConnectBody)
      @DELETE("/api/me/google-health/connect") suspend fun disconnect()
  }
  @JsonClass(generateAdapter = true)
  data class GoogleHealthStatusDto(val connected: Boolean, val connectedAt: String?)
  // Android branch of the connect body. Backend distinguishes Android
  // (serverAuthCode) from web (refreshToken+accessToken).
  @JsonClass(generateAdapter = true)
  data class ConnectBody(val serverAuthCode: String)
  ```
- `data/googlehealth/GoogleHealthRepositoryImpl.kt` — `status()` wraps
  the service call and parses `connectedAt` via `Instant.parse(...).epochSecond`;
  `connectWithServerAuthCode(code)` posts `ConnectBody(code)`; `disconnect()`
  calls the DELETE.
- `data/di/RepositoryModule.kt` — `@Module @InstallIn(SingletonComponent::class)`
  abstract class with `@Binds` for `ProfileRepository` and
  `GoogleHealthRepository`. `NetworkModule` (from IMPL-AND-00) declares
  the Retrofit-built services.

### `core-data/data/auth/` (Google Health scope flow)

- `core-data/src/main/java/com/gte619n/healthfitness/data/auth/GoogleHealthScopes.kt`
  ```kotlin
  object GoogleHealthScopes {
      const val METRICS_READ_ONLY =
          "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly"
  }
  ```
- `data/auth/GoogleHealthScopeRepository.kt` — sibling to
  `GoogleAuthRepository`. Wraps Google Identity Services'
  `AuthorizationClient` to obtain a server auth code for the Google
  Health metrics scope. Returns either a `Resolved(code)` ready to
  POST to the backend, or a `NeedsUserConsent(intentSender)` the
  Activity must launch — matching `AuthorizationClient`'s two-phase API.
  ```kotlin
  class GoogleHealthScopeRepository @Inject constructor(
      @ApplicationContext private val context: Context,
      private val webOauthClientId: String,
  ) {
      private val client = Identity.getAuthorizationClient(context)

      suspend fun requestHealthAuthorization(): HealthAuthFlow {
          val request = AuthorizationRequest.builder()
              .setRequestedScopes(listOf(Scope(GoogleHealthScopes.METRICS_READ_ONLY)))
              .requestOfflineAccess(webOauthClientId, /* forceCodeForRefreshToken */ true)
              .build()
          return try {
              val result = client.authorize(request).await()
              val code = result.serverAuthCode
              when {
                  code != null -> HealthAuthFlow.Resolved(code)
                  result.hasResolution() ->
                      HealthAuthFlow.NeedsUserConsent(result.pendingIntent!!.intentSender)
                  else -> HealthAuthFlow.Failed("No server auth code and no resolution")
              }
          } catch (e: ApiException) {
              HealthAuthFlow.Failed(e.message ?: e.javaClass.simpleName)
          }
      }

      fun parseConsentResult(data: Intent?): HealthAuthFlow {
          val result = client.getAuthorizationResultFromIntent(data)
          return result.serverAuthCode?.let { HealthAuthFlow.Resolved(it) }
              ?: HealthAuthFlow.Failed("Consent completed but no server auth code returned")
      }
  }

  sealed interface HealthAuthFlow {
      data class Resolved(val serverAuthCode: String) : HealthAuthFlow
      data class NeedsUserConsent(val intentSender: IntentSender) : HealthAuthFlow
      data class Failed(val cause: String) : HealthAuthFlow
  }
  ```
  `requestOfflineAccess(..., forceCodeForRefreshToken = true)` is the GIS
  equivalent of the web's `prompt=consent` — it forces a fresh consent
  screen so Google issues a new refresh token even if the user
  previously granted (and dismissed) the scope.

### `feature-settings/` (new module)

Package: `com.gte619n.healthfitness.feature.settings`, with subpackages
`profile/`, `googlehealth/`, `about/`. Depends on `:core-domain`,
`:core-data`, `:core-ui`.

- `SettingsScreen.kt`
  ```kotlin
  @Composable
  fun SettingsScreen(
      onNavigateBack: () -> Unit,
      onNavigateToProfile: () -> Unit,
      onSignedOut: () -> Unit,
      viewModel: SettingsViewModel = hiltViewModel(),
  )
  ```
  Renders three cards (Profile entry, Google Health section, About),
  plus a "Sign out" button at the bottom.
- `SettingsViewModel.kt` — `@HiltViewModel` holding injected
  `GoogleAuthRepository` + `AppVersionInfo`. Exposes `versionName` /
  `versionCode` (from BuildConfig) and `fun signOut(onDone: () -> Unit)`
  which calls `authRepo.signOut()` in `viewModelScope` then invokes the
  callback.
- `profile/ProfileScreen.kt`
  ```kotlin
  @Composable
  fun ProfileScreen(
      onNavigateBack: () -> Unit,
      viewModel: ProfileViewModel = hiltViewModel(),
  )
  ```
  Renders read-only name + email rows and an editable height row using
  two compact number inputs (feet / inches). Save button posts to the
  ViewModel. Mirrors `web/components/profile/HeightForm.tsx` visually.
- `profile/ProfileViewModel.kt`:
  ```kotlin
  @HiltViewModel
  class ProfileViewModel @Inject constructor(
      private val repo: ProfileRepository,
  ) : ViewModel() {
      sealed interface UiState {
          data object Loading : UiState
          data class Loaded(val profile: Profile, val saving: Boolean = false) : UiState
          data class Error(val message: String) : UiState
      }
      val state: StateFlow<UiState>             // MutableStateFlow(Loading), refreshed in init {}
      fun refresh()                             // Loading → repo.get() → Loaded | Error
      fun saveHeight(feet: Int, inches: Int)    // Loaded(saving=true) → repo.updateHeightCm(
                                                //   HeightMetric.ftInToCm(feet, inches)) → Loaded | Error
  }
  ```
- `googlehealth/GoogleHealthSection.kt` — composable that owns the
  `ActivityResultLauncher<IntentSenderRequest>` for the consent intent.
  Uses `rememberLauncherForActivityResult(StartIntentSenderForResult())
  { viewModel.onConsentResult(it.data) }` and a `LaunchedEffect` to
  collect `viewModel.consentRequests` and launch each emitted
  `IntentSender` via `IntentSenderRequest.Builder(sender).build()`.
  Renders the Connected / Not connected card plus the action button.
- `googlehealth/GoogleHealthViewModel.kt`:
  ```kotlin
  @HiltViewModel
  class GoogleHealthViewModel @Inject constructor(
      private val repo: GoogleHealthRepository,
      private val scope: GoogleHealthScopeRepository,
  ) : ViewModel() {
      sealed interface UiState {
          data object Loading : UiState
          data class Disconnected(val connecting: Boolean = false) : UiState
          data class Connected(val connectedAtEpochSeconds: Long?, val disconnecting: Boolean = false) : UiState
          data class Error(val message: String) : UiState
      }
      val state: StateFlow<UiState>             // backed by MutableStateFlow(Loading)
      val consentRequests: Flow<IntentSender>   // backed by Channel(capacity = 1)

      fun refresh()                             // GET status → Disconnected | Connected | Error
      fun connect()                             // Disconnected(connecting=true);
                                                //   scope.requestHealthAuthorization() →
                                                //     Resolved → submitAuthCode
                                                //     NeedsUserConsent → emit intentSender on channel
                                                //     Failed → Error
      fun onConsentResult(data: Intent?)        // scope.parseConsentResult(data) → submitAuthCode | Error
      fun disconnect()                          // Connected(disconnecting=true); DELETE → Disconnected | Error
      private suspend fun submitAuthCode(code: String) // POST connect → refresh() | Error
  }
  ```
  Channel (rather than StateFlow) is used for `consentRequests` because
  each consent intent must be launched exactly once from the Composable;
  StateFlow's replay semantics would re-launch on recomposition.
- `about/AboutSection.kt` — version row + two stub link rows (Privacy,
  Terms) that open `https://placeholder.tesseta.app/...` via
  `Intent.ACTION_VIEW`. Real URLs wired in a follow-up IMPL.

### `app/` (navigation + entry points)

- `mobile/nav/Routes.kt` — add `@Serializable data object SettingsRoute`
  and `@Serializable data object ProfileRoute` to the type-safe routes
  from IMPL-AND-00.
- `mobile/nav/NavGraph.kt` — add two `composable<...>` destinations:
  `SettingsRoute` calls `SettingsScreen(onNavigateBack=popBackStack,
  onNavigateToProfile=navigate(ProfileRoute), onSignedOut={
  authCoordinator.markSignedOut(); popBackStack(SignInRoute,
  inclusive=false) })`; `ProfileRoute` calls `ProfileScreen(
  onNavigateBack=popBackStack)`.
- `FoldableDashboardScreen.kt` — change the existing
  `FoldableNavIcon(icon = DashboardIcons.Settings, ...)` call site to
  accept an `onClick: () -> Unit` and dispatch into the NavController.
  The phone "More" tab (added in IMPL-AND-00) routes to `SettingsRoute`
  rather than its current placeholder.
- `mobile/settings/AppVersionInfo.kt` — `class AppVersionInfo @Inject
  constructor()` exposing `versionName: String = BuildConfig.VERSION_NAME`
  and `versionCode: Int = BuildConfig.VERSION_CODE`. Hilt-provided so
  `feature-settings` doesn't depend on app's `BuildConfig`. Bound in a
  `@Module` in `app/di`.
- `AuthCoordinator` gains `fun markSignedOut() { _state.value =
  AuthState.SignedOut }` so the navigation callback can reset state
  without an extra bootstrap pass.

## Gradle changes

`android/gradle/libs.versions.toml` — add:

```toml
[versions]
playServicesAuth = "21.2.0"
[libraries]
play-services-auth = { module = "com.google.android.gms:play-services-auth", version.ref = "playServicesAuth" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version = "1.9.0" }
```

`android/core-data/build.gradle.kts` — add
`implementation(libs.play.services.auth)` and
`implementation(libs.kotlinx.coroutines.play.services)` (needed for
`Task<...>.await()`).

`android/feature-settings/build.gradle.kts` (new) — standard Compose +
Hilt + KSP library plugin set from IMPL-AND-00; dependencies on
`:core-domain`, `:core-data`, `:core-ui`, plus the Compose, Hilt, and
`androidx.hilt:hilt-navigation-compose` bundles.

`android/settings.gradle.kts` — `include(":feature-settings")`.
`android/app/build.gradle.kts` — `implementation(project(":feature-settings"))`.

`webOauthClientId` is already on `BuildConfig.WEB_OAUTH_CLIENT_ID`;
`GoogleHealthScopeRepository` reuses it via a `@Provides` in
`core-data/di/AuthModule.kt`.

## Backend touchpoint (additive, no new IMPL needed)

`GoogleHealthConnectController.connect()` accepts the existing web shape
`{ refreshToken, accessToken }` and a new optional Android shape
`{ serverAuthCode }`. When `serverAuthCode` is present, the controller
calls a small helper on `GoogleHealthOAuthClient` —
`exchangeServerAuthCode(String authCode) → TokenPair(refreshToken,
accessToken)` — using the existing `web-oauth-client-id` +
`web-oauth-client-secret` config from IMPL-04. From the encrypt-and-store
point onward the path is identical.

This addition is intentionally small and lands as part of this IMPL's
backend slice so Android doesn't ship blocked on a separate backend
spec. The change is one new request shape + one helper method;
existing web traffic continues to use the original shape.

## Tests

`feature-settings/.../profile/ProfileViewModelTest.kt` — Turbine on
`state` against a fake `ProfileRepository`. Asserts `Loading → Loaded`,
`saveHeight(6, 2) → Loaded(saving=true) → Loaded(heightCm=188)`, and
repository-failure → `UiState.Error`.

`feature-settings/.../googlehealth/GoogleHealthViewModelTest.kt` —
Turbine on both `state` and `consentRequests`. Asserts:
- `Loading → Disconnected` (status returns `connected=false`).
- `Disconnected → Disconnected(connecting=true) → Connected(...)`
  when the scope repo returns `HealthAuthFlow.Resolved` and the
  connect POST succeeds.
- `Disconnected(connecting=true)` plus an `IntentSender` emission on
  `consentRequests` when the scope repo returns `NeedsUserConsent`,
  followed by `Connected` after `onConsentResult(...)`.
- `Connected → Connected(disconnecting=true) → Disconnected` on
  `disconnect()`.
- Error path per repo call.

`core-data/.../auth/GoogleHealthScopeRepositoryTest.kt` — wraps the
final `AuthorizationClient` behind a small internal interface (impl
forwards to `Identity.getAuthorizationClient(...)`), letting the test
substitute a fake. Covers the resolved-immediate, needs-consent, and
`ApiException` paths, plus `parseConsentResult` with a stub intent.

`core-data/.../profile/ProfileRepositoryImplTest.kt` — MockWebServer.
Asserts `GET /api/me` and `PATCH /api/me` request bodies and the
DTO → domain mapping.

`core-data/.../googlehealth/GoogleHealthRepositoryImplTest.kt` —
MockWebServer. `status()` parses the ISO `connectedAt`. `connect`
sends `{"serverAuthCode":"..."}`. `disconnect` issues the DELETE.

## Acceptance

1. `./gradlew :feature-settings:test :core-data:test :core-domain:test`
   passes.
2. From the phone app, tapping the "More" tab routes to the Settings
   screen. The Settings screen renders three cards (Profile, Google
   Health, About) plus a Sign out button.
3. From the foldable layout, tapping the settings icon in
   `FoldableSidebar` routes to the same Settings screen.
4. **Profile flow:** Open Settings → Profile. Name + email show the
   values from the signed-in Google account. Entering `6` feet `2`
   inches and pressing Save issues `PATCH /api/me` with
   `{ "heightCm": 188 }`, then the row updates to show 6'2" again on
   the next load.
5. **Google Health connect flow (manual):**
   - Settings → Google Health card shows "Not connected".
   - Tapping "Connect Google Health" opens the OAuth consent sheet
     listing the
     `googlehealth.health_metrics_and_measurements.readonly` scope.
   - Granting consent dismisses the sheet, the card transitions to
     "Connecting…" briefly, then "Connected · <timestamp>".
   - Backend's `users/{sub}` Firestore document gains a populated
     `googleHealth` block with non-empty `refreshTokenCiphertext` and
     `dekCiphertext` (verified per IMPL-04 acceptance step 8).
   - `GET /api/me/google-health/status` returns
     `{ connected: true, connectedAt: <iso> }`.
6. **Disconnect flow:** From the "Connected" card, tapping Disconnect
   transitions to "Disconnecting…", then "Not connected". Backend's
   `googleHealth` block is cleared. No app-level sign-out occurs.
7. **Sign out:** From the Settings footer, tapping Sign out clears
   `IdTokenCache`, clears Credential Manager state, navigates back to
   `SignInScreen`. Subsequent silent refresh returns `AuthState.SignedOut`.
8. **About:** App version line shows `BuildConfig.VERSION_NAME
   (#${VERSION_CODE})`. Privacy + Terms rows fire `ACTION_VIEW`
   intents (real URLs deferred).

## Open questions resolved before implementation

- **Credential Manager vs AuthorizationClient** — AuthorizationClient.
  Credential Manager's GoogleIdTokenCredential request returns ID
  tokens only and has no surface for `access_type=offline` or
  arbitrary OAuth scopes; AuthorizationClient is Google's documented
  path for exactly this case.
- **Server auth code vs client-side refresh token** — Server auth
  code. Backend already holds the web OAuth client secret (needed for
  the refresh→access exchange in IMPL-04); reusing it for the
  Android auth-code exchange avoids ever putting that secret on
  device.
- **Repository shape** — New sibling class
  `GoogleHealthScopeRepository`, not an extension on
  `GoogleAuthRepository`. Different API surface (GIS vs Credential
  Manager), different threading model (Tasks API vs suspend), and
  different lifecycle (called once per upgrade vs every silent
  refresh) make the separation cleaner than the alternative.
- **Connect-body shape** — Add `{ serverAuthCode }` as an alternative
  request body on the existing backend controller. One small additive
  change to `GoogleHealthConnectController` and a helper on
  `GoogleHealthOAuthClient`. Lands with this IMPL's backend slice.
- **Profile endpoint shape** — Use the backend's existing `PATCH
  /api/me` with `{ heightCm }`. The roadmap's mention of
  `POST /api/me/profile/height` is a stub; the backend never
  implemented that path.
- **Sign-out scope** — Clears local cache + Credential Manager only;
  no server call. Matches web (Auth.js v5 sign-out is also
  client-side for JWT sessions).

## Open questions deferred to implementation

- **Push-notification permission prompt in Settings** — almost
  certainly belongs here once IMPL-AND-03 adds dose reminders, but is
  out of scope until that lands.
- **Privacy / Terms URLs** — placeholder intents land here; real URLs
  arrive with a content workstream outside the Android domain.
- **In-line height edit UX** — current design saves on button press
  rather than on blur. Once the broader "edit in place" pattern lands
  for DEXA in IMPL-AND-05, revisit whether `HeightForm` should adopt
  the same shape on Android.
- **Disconnect confirmation dialog** — web uses a plain button. If
  user feedback shows accidental disconnects, add a confirmation
  using the eventual Android equivalent of web's `useConfirm()`.
