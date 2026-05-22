# IMPL-04: Google Health API integration — body composition (webhook + REST)

## Goal

Pull a user's body composition history from the Google Health API and keep
it current via webhook push. Four data types in this IMPL: weight, body
fat, lean mass, BMI. Stored under `users/{sub}/bodyComposition/{recordId}`
in Firestore. Read via a new `GET /api/me/body-composition` endpoint.

This lands the architecture committed to in `docs/architecture.md` —
direct HTTPS webhook from Google Health API → backend → REST hydrate →
Firestore — for one data domain. Future IMPLs widen the data scope
(exercise, sleep, heart rate) without re-litigating the architecture.

## Scope

In scope:

- OAuth scope `googlehealth.health_metrics_and_measurements.readonly`
  added to the web sign-in flow as an **incremental authorization** —
  existing sessions keep working, prompt only when the user enters the
  body-composition feature.
- Server-side **refresh token** for the new scope, encrypted with Cloud
  KMS and stored on the user document. Exchanged for access tokens on
  every backend → Google Health API call.
- Google Health REST client in `integrations/googlehealth/` covering
  `dataPoints.list` for the four body-comp data types.
- Webhook subscriber endpoint at `POST /api/webhooks/google-health` —
  verifies the configured `Authorization` secret, handles Google's
  domain-verification probe pair, parses notification payloads, and
  hydrates via REST.
- One-time **historical backfill** (default lookback: 4 years / 1460
  days) on first scope grant, so the user sees their existing data
  immediately rather than only what arrives after the webhook is wired.
  Backfill chunks the window into one-year REST queries to keep any
  single response bounded.
- `BodyCompositionMeasurement` record + `BodyCompositionRepository`
  (interface in `core`, Firestore impl in `persistence`).
- Read endpoint `GET /api/me/body-composition?from=...&to=...&metric=...`
  returning the user's stored measurements.
- One-time subscriber registration via
  `infra/scripts/setup-google-health-subscriber.sh` against the
  `projects.subscribers.create` admin API.
- KMS keyring + key for refresh-token envelope encryption.

Out of scope (deferred):

- Android / Wear OS path to the Google Health API. Phone reads the same
  Firestore-backed data through the existing `/api/me/*` surface — no
  separate Android client needed.
- Data types beyond body composition (exercise, sleep, heart rate,
  nutrition). The integration shape is reusable.
- Multi-environment subscriber endpoints (one subscriber per environment;
  prod and staging configured separately by re-running the registration
  script with different env values).
- UI for the body-composition feature. The API endpoint lands here;
  visual design is a follow-up IMPL.
- DEXA scan PDF ingestion (the `docs/test_reports/dexa_scans/*` files are
  reference data, not a data source).
- Subscriber deletion / lifecycle UX (user-driven "disconnect"). The
  manual `gcloud`/curl path works for now; a `DELETE
  /api/me/google-health` admin endpoint lands when the disconnect UI lands.

## Decisions

| Topic | Decision |
|---|---|
| Sync architecture | Webhook + REST per `architecture.md`. Direct HTTPS POST notifications (no Pub/Sub fronting). |
| Webhook auth | Google sends the configured `secret` as `Authorization` header value. We compare via constant-time equality. Secret stored in Secret Manager as `google-health-webhook-secret`. |
| Domain verification | Endpoint responds 200 to probes carrying the secret, 401 to probes that don't. Google sends both during subscriber create/update. |
| Subscriber endpoint URL | `https://<web-backend-cloud-run>/api/webhooks/google-health`. Public — no `/api/me/*` JWT auth filter applied. |
| Subscriber scope | One subscriber per Google Cloud project (i.e., per environment). All users automatically tracked under that subscriber once they grant the scope. |
| User identity bridge | Add `healthUserId` to the `User` record. Populated lazily: the first time we hydrate a webhook notification or backfill, we extract it from the `dataPoint.name` (`users/{healthUserId}/dataTypes/...`). Indexed for reverse lookup webhook → app userId. |
| Refresh token storage | Encrypted with Cloud KMS using envelope encryption (DEK wrapped by KMS KEK). Stored as `users/{sub}.googleHealth.refreshTokenCiphertext` (bytes) + `dekCiphertext` (bytes). One KMS key reused across users. |
| KMS key location | `projects/health-fitness-160/locations/us-central1/keyRings/auth/cryptoKeys/google-health-refresh-tokens`. Runtime SA gets `roles/cloudkms.cryptoKeyEncrypterDecrypter`. |
| OAuth scope flow | Incremental authorization via Auth.js v5. New "Connect Google Health" action triggers a re-auth with the added scope; existing session continues otherwise. |
| Document layout | `users/{sub}/bodyComposition/{recordId}`. One collection for all four metrics, distinguished by a `metric` enum field. `recordId` is the last segment of the Google Health record `name` field (idempotent UPSERTs, direct DELETE target). |
| Metric enum | `WEIGHT_KG`, `BODY_FAT_PERCENT`, `LEAN_MASS_KG`, `BMI`. Unit is part of the enum value so the `value` field is always a plain `double`. |
| Composite index | `metric ASC, sampleTime DESC` on the `bodyComposition` subcollection — enables "weight over the last 90 days" without a fan-out read. |
| Backfill window | 4 years (1460 days) lookback on first connect. Subsequent webhook notifications cover the forward path. Configurable via `app.googlehealth.backfill-days`. Backfill iterates yearly chunks to keep each REST response bounded. |
| Webhook-subscribable data types | Confirmed at registration time: `weight`, `body-fat`. The Google Health API does **not** accept `lean-mass` or `bmi` as subscribable types — those are REST-only. The 4-year backfill still pulls all four; only the forward-path real-time push is limited to weight + body-fat. |
| REST client | Hand-rolled over `java.net.http.HttpClient`. No third-party Google Health SDK (one isn't published yet at time of writing). Synchronous, virtual-thread friendly. |
| Notification retries | Google retries for 7 days with exponential backoff. We make the hydrate-and-store path idempotent via `recordId` (Firestore `set()` with the same doc ID). |
| DELETE notifications | Map to `BodyCompositionRepository.delete(userId, recordId)`. If the doc is already gone, no-op. |

## Backend deliverables (`backend/`)

### Dependencies (`backend/gradle/libs.versions.toml`)

```toml
[versions]
googleKms = "2.55.0"

[libraries]
# new:
google-cloud-kms = { module = "com.google.cloud:google-cloud-kms", version.ref = "googleKms" }
# existing google-auth-library and google-api-client already wired in IMPL-00
# are used here for the OAuth2 access-token refresh exchange.
```

Wire into `integrations/build.gradle.kts` and `persistence/build.gradle.kts`.

### Code — `core` module

- `core/src/main/java/com/gte619n/healthfitness/core/bodycomposition/BodyCompositionMetric.java`
  ```java
  public enum BodyCompositionMetric {
      WEIGHT_KG,
      BODY_FAT_PERCENT,
      LEAN_MASS_KG,
      BMI
  }
  ```
- `core/src/main/java/com/gte619n/healthfitness/core/bodycomposition/BodyCompositionMeasurement.java`
  ```java
  public record BodyCompositionMeasurement(
      String userId,
      String recordId,                 // Google Health record name's last segment
      BodyCompositionMetric metric,
      double value,
      Instant sampleTime,
      String sourcePlatform,           // "FITBIT" | "WITHINGS" | etc — from dataSource.platform
      String recordingMethod,          // "MANUAL" | "AUTOMATIC" | "UNKNOWN"
      Instant createdAt,
      Instant updatedAt
  ) {}
  ```
- `core/src/main/java/com/gte619n/healthfitness/core/bodycomposition/BodyCompositionRepository.java`
  ```java
  public interface BodyCompositionRepository {
      Optional<BodyCompositionMeasurement> findById(String userId, String recordId);
      List<BodyCompositionMeasurement> findByUserAndRange(
          String userId,
          BodyCompositionMetric metric,
          Instant from,
          Instant to
      );
      List<BodyCompositionMeasurement> findByUser(String userId);    // all metrics, recent first, capped at 500
      void save(BodyCompositionMeasurement measurement);
      void saveAll(List<BodyCompositionMeasurement> measurements);   // batched write
      void delete(String userId, String recordId);
  }
  ```
- Update `core/src/main/java/com/gte619n/healthfitness/core/user/User.java` to add a nested record:
  ```java
  public record User(
      String userId,
      String email,
      String displayName,
      GoogleHealthConnection googleHealth,   // nullable; populated after first scope grant
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record GoogleHealthConnection(
      String healthUserId,
      byte[] refreshTokenCiphertext,
      byte[] dekCiphertext,
      Instant connectedAt
  ) {}
  ```
  `UserService.provisionIfAbsent` keeps writing the same fields it does
  today; `GoogleHealthConnection` is populated only on connect.
- `core/src/main/java/com/gte619n/healthfitness/core/user/UserRepository.java` gains:
  ```java
  Optional<User> findByHealthUserId(String healthUserId);
  void recordGoogleHealthConnection(String userId, GoogleHealthConnection connection);
  void clearGoogleHealthConnection(String userId);
  ```

### Code — `integrations` module (`integrations/googlehealth/`)

- `GoogleHealthClient.java` — REST wrapper.
  - `listDataPoints(String accessToken, GoogleHealthDataType dataType, Instant from, Instant to)`
    → `List<GoogleHealthDataPoint>`. Handles pagination via `nextPageToken`.
  - URL: `https://health.googleapis.com/v4/users/me/dataTypes/{type}/dataPoints`
  - Filter format:
    `{snake_data_type}.sample_time.physical_time >= "${from}" AND
     {snake_data_type}.sample_time.physical_time <= "${to}"`
  - Uses `java.net.http.HttpClient` configured with virtual threads.
- `GoogleHealthDataType.java` — enum mapping our metric enum to Google's
  REST data-type identifiers:
  - `WEIGHT_KG` ↔ `"weight"`
  - `BODY_FAT_PERCENT` ↔ `"body-fat"`
  - `LEAN_MASS_KG` ↔ `"lean-mass"`
  - `BMI` ↔ `"bmi"`
- `GoogleHealthDataPoint.java` — record matching the API's response JSON.
  Mapper translates from this to `BodyCompositionMeasurement`.
- `GoogleHealthOAuthClient.java` — minimal OAuth2 token-exchange client.
  Trades a refresh token for an access token via
  `POST https://oauth2.googleapis.com/token`. No third-party dependency
  (avoids pulling Google's heavy OAuth client lib for a four-field
  request).
- `GoogleHealthSubscriberClient.java` — admin client for
  `projects.subscribers.{create,list,delete}`. Used only by
  `setup-google-health-subscriber.sh` (or its Java entry point); not on
  the request path.
- `BodyCompositionMapper.java` — converts a `GoogleHealthDataPoint` into
  a `BodyCompositionMeasurement`, including:
  - Pulling out `value` from per-type sub-objects (`bodyFat.percentage`,
    `weight.kilograms`, `leanMass.kilograms`, `bmi.value`)
  - Pulling out `recordId` (last segment of `name`)
  - Pulling out `healthUserId` (segment between `users/` and `/dataTypes`)
- `KmsTokenCipher.java` — thin wrapper over `KeyManagementServiceClient`
  for envelope encryption. Lives here because both the
  webhook/hydrate path and the connect path need it. Methods:
  - `encrypt(String plaintext) → EncryptedToken (byte[] ct, byte[] dekCt)`
  - `decrypt(EncryptedToken) → String plaintext`
  - DEK is a 32-byte AES key, generated per encrypt call. KEK is the KMS
    key, fetched lazily.

### Code — `api` module

- `api/auth/GoogleHealthConnectController.java`
  - `POST /api/me/google-health/connect` (authenticated):
    - Body: `{ refreshToken: string, accessToken: string }`
      forwarded once after the web client completes the incremental
      authorization
    - Encrypts refresh token via `KmsTokenCipher`
    - Calls Google Health REST once with the access token to discover
      `healthUserId` (parsed from the first response's record `name`)
    - Persists `GoogleHealthConnection` on the user
    - Triggers an async backfill (365-day pull) via `BackfillService`
    - Returns 204
  - `DELETE /api/me/google-health/connect` (authenticated):
    - Clears the `GoogleHealthConnection` from the user record. Does
      **not** remove already-synced measurements (those are the user's
      data; they can call a separate body-composition-wipe endpoint).
    - Disconnecting from Google's side (revoking the scope) is the user's
      responsibility via their Google Account UI; we don't proxy that.
- `api/bodycomposition/BodyCompositionController.java`
  - `GET /api/me/body-composition`
    - Query params: `from`, `to` (ISO instants), `metric` (optional,
      `WEIGHT_KG` | `BODY_FAT_PERCENT` | `LEAN_MASS_KG` | `BMI`)
    - Reads from `BodyCompositionRepository`
    - Returns `[{ recordId, metric, value, sampleTime, sourcePlatform,
      recordingMethod }]`
- `api/webhooks/GoogleHealthWebhookController.java`
  - `POST /api/webhooks/google-health`:
    - Verifies `Authorization` header against
      `app.googlehealth.webhook-secret` (constant-time compare)
    - On verification probe (Google sends a specific payload during
      subscriber create/update), responds 200 (authorized) or 401
      (unauthorized) as appropriate — see Google's docs section on
      domain verification
    - Otherwise parses the notification payload:
      `{ healthUserId, dataType, operation: "UPSERT" | "DELETE",
         intervals: [{ startTime, endTime }] }`
    - Resolves `healthUserId` → app `userId` via
      `UserRepository.findByHealthUserId`
    - Dispatches to `WebhookHandlerService`

### Code — `app` module

- `app/auth/SecurityConfig.java`:
  - Add `/api/webhooks/**` → `permitAll()`. Authentication on this path
    is via the configured shared secret, not the user JWT filter.
- `app/googlehealth/WebhookHandlerService.java`:
  - For UPSERT: fetch refresh token from the user, exchange for an
    access token, call `GoogleHealthClient.listDataPoints` for the
    notification's data type and interval, map to
    `BodyCompositionMeasurement`s, `saveAll` via the repository.
  - For DELETE: list the existing records in the interval for that
    metric, delete each.
- `app/googlehealth/BackfillService.java`:
  - Runs after `POST /api/me/google-health/connect`. Schedules a
    virtual-thread task that pulls all four data types for the
    `app.googlehealth.backfill-days` window. Iterates yearly chunks
    (e.g., 4y window → 4 chunks per data type = 16 REST calls baseline,
    plus pagination per call). `saveAll` writes the combined result in
    Firestore-batched chunks of 500.
- `app/googlehealth/AccessTokenService.java`:
  - Given a `userId`, loads the encrypted refresh token from the user
    doc, decrypts via KMS, exchanges via `GoogleHealthOAuthClient`,
    caches the resulting access token in-process for ~50 minutes
    (access tokens live one hour; we leave a 10-minute safety buffer).

### Code — `persistence` module

- `persistence/src/main/java/com/gte619n/healthfitness/persistence/bodycomposition/BodyCompositionRepository.java`
  Implements `core.bodycomposition.BodyCompositionRepository`. Writes to
  `users/{userId}/bodyComposition/{recordId}`. Document body:
  ```
  metric: "WEIGHT_KG"
  value: 82.4
  sampleTime: Timestamp
  sourcePlatform: "FITBIT"
  recordingMethod: "AUTOMATIC"
  createdAt: Timestamp (serverTimestamp on first write)
  updatedAt: Timestamp (serverTimestamp on every write)
  ```
  Composite index declared in `firestore.indexes.json` (see Infrastructure).
- `persistence/user/UserRepository.java` is extended to handle the new
  `googleHealth` nested map and the `findByHealthUserId` query (which
  requires a Firestore index on `googleHealth.healthUserId`).

### Config (`app/src/main/resources/application.yml`)

```yaml
app:
  googlehealth:
    api-base-url: https://health.googleapis.com/v4
    oauth-token-url: https://oauth2.googleapis.com/token
    backfill-days: 1460
    backfill-chunk-days: 365
    webhook-secret: ${GOOGLE_HEALTH_WEBHOOK_SECRET:}
    kms-key-name: ${GOOGLE_HEALTH_KMS_KEY:projects/health-fitness-160/locations/us-central1/keyRings/auth/cryptoKeys/google-health-refresh-tokens}
    web-oauth-client-id: ${OAUTH_WEB_CLIENT_ID:}
    web-oauth-client-secret: ${OAUTH_WEB_CLIENT_SECRET:}
```

The `web-oauth-client-id` / `web-oauth-client-secret` are needed for the
backend to do the refresh-token → access-token exchange — Google's token
endpoint requires the originating client's credentials.

### Tests

- `GoogleHealthClientTest` (unit) — uses a MockWebServer (OkHttp's
  testing utility, already transitive via Spring's test starter) to
  stub the REST responses. Validates URL construction, filter syntax,
  pagination, and the body composition response parsing for each of the
  four data types.
- `BodyCompositionMapperTest` (unit) — fixtures for one record of each
  metric, asserts mapper output. Tests both happy path and a record
  with missing optional fields (e.g., `recordingMethod` absent).
- `KmsTokenCipherTest` (unit) — mocks `KeyManagementServiceClient`,
  verifies envelope structure (DEK encrypted by KEK, plaintext encrypted
  by DEK with a fresh IV per call).
- `GoogleHealthWebhookControllerTest` (Spring slice) — `@WebMvcTest`
  asserts:
  - Probe pair (authorized 200 / unauthorized 401)
  - Unknown user (no `findByHealthUserId` match) → 404
  - UPSERT dispatches the right handler call
  - DELETE dispatches the right handler call
  - Wrong/missing Authorization → 401
- `BodyCompositionControllerTest` (Spring slice) — read endpoint with
  date range and metric filter against an in-memory fake.
- `BodyCompositionRepositoryIntegrationTest` (Testcontainers emulator,
  same pattern as IMPL-03) — round-trips one record per metric,
  composite-index query, `saveAll` idempotency, `delete`.
- `WebhookHandlerServiceTest` (Spring slice with mocked
  `GoogleHealthClient` and emulator-backed repository) — UPSERT
  notification → REST hydrate → saved measurements. DELETE notification
  → removed measurements.
- `BackfillServiceTest` — mocked client, verifies the 365-day window
  call across all four data types and that `saveAll` is called with the
  combined results.

## Web deliverables (`web/`)

### Auth.js incremental authorization

- `web/auth.ts`:
  - Add the body-comp scope to a named "step" that's not part of the
    baseline sign-in scope set.
  - Expose an `authorizationParams` override hook so the
    "Connect Google Health" action can request the extra scope without
    re-prompting for the baseline ones.
- `web/app/me/body-composition/page.tsx`:
  - Server Component. If `session.googleHealthConnected !== true`,
    renders a "Connect Google Health" call-to-action that fires the
    incremental authorization flow.
  - On callback completion, posts the refresh token + access token to
    `POST /api/me/google-health/connect`.
  - After connect, fetches `GET /api/me/body-composition?from=...&to=...`
    and renders the four metrics as a sparkline strip + a recent-readings
    table.
- `web/lib/api.ts` gains a `connectGoogleHealth(refreshToken,
  accessToken)` helper.

### Tests

- Playwright: complete the incremental authorization flow against a
  mocked Google provider (same shape as the IMPL-02 sign-in test).
  Verify the body-composition page renders fixture data once the
  callback completes.

## Infrastructure deliverables

### GCP — new

- **Enable the Google Health API** in `health-fitness-160` (one-time):
  `gcloud services enable health.googleapis.com --project=health-fitness-160`
- **KMS keyring + key**:
  - Keyring `auth` in `us-central1` (created if absent)
  - Symmetric encryption key
    `projects/health-fitness-160/locations/us-central1/keyRings/auth/cryptoKeys/google-health-refresh-tokens`
  - Runtime service account binding:
    `roles/cloudkms.cryptoKeyEncrypterDecrypter` on that key
- **OAuth scope addition** in Cloud Console:
  - On the existing web OAuth client, add
    `https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly`
    to the requested scopes
  - Add the email of the developer (and any test users) to the
    "Testing" audience until production review is done
- **Secret Manager** entries:
  - `google-health-webhook-secret` — random 32-byte base64 string
    Google sends back as the `Authorization` header on notifications
- **Firestore composite indexes** in `infra/firestore/firestore.indexes.json`:
  - On `bodyComposition` subcollection: `metric ASC, sampleTime DESC`
  - On `users` collection: `googleHealth.healthUserId ASC` (for
    webhook-time reverse lookups)
- **No Pub/Sub** — direct HTTPS push, so no topic / subscription needed.

### Subscriber registration

`infra/scripts/setup-google-health-subscriber.sh`:

1. Reads `google-health-webhook-secret` from Secret Manager.
2. Reads the backend Cloud Run URL.
3. Calls `POST
   https://health.googleapis.com/v4/projects/{project-id}/subscribers?subscriberId={id}`
   with body:
   ```
   {
     "endpointUri": "https://<backend-cloud-run>/api/webhooks/google-health",
     "authorization": { "secret": "<from secret manager>" },
     "dataTypes": ["weight", "body-fat", "lean-mass", "bmi"]
   }
   ```
4. Idempotent: if the subscriber already exists, PATCHes instead of
   POSTs.

Run once per environment. The backend Cloud Run service must be
deployed and reachable before this runs — Google performs the domain
verification probe pair during the call.

### Cloud Run / runtime

- Backend container gains env vars from Secret Manager + literals:
  `OAUTH_WEB_CLIENT_ID`, `OAUTH_WEB_CLIENT_SECRET` (already in IMPL-02),
  `GOOGLE_HEALTH_WEBHOOK_SECRET`, `GOOGLE_HEALTH_KMS_KEY`.
- No CPU / memory changes anticipated. Webhook traffic volume is low
  (smart-scale measurements happen a handful of times per day per user).

## ADR

Paired with a new
**[ADR-0004: Per-user Google Health refresh tokens, KMS-encrypted in
Firestore](../decisions/ADR-0004-google-health-refresh-token-storage.md)**.

The choice of webhook + REST is already settled in
[ADR-0001](../decisions/ADR-0001-three-component-architecture.md) and
the architecture overview, so this IMPL doesn't re-litigate it.

## Acceptance

1. `./gradlew :persistence:test :app:test :integrations:test` passes.
2. From a real Google account in the "Testing" audience: connect via
   the web flow, observe a single OAuth consent screen mentioning the
   `health_metrics_and_measurements.readonly` scope, then immediately
   see body-comp readings in `/me/body-composition` (backfill).
3. Recording a new weigh-in on the connected smart scale results in
   the matching record appearing in Firestore at
   `users/{sub}/bodyComposition/{recordId}` within ~30 seconds of the
   measurement.
4. Deleting a weigh-in from the source platform results in the
   corresponding Firestore record being removed within the same window.
5. `curl -i $BACKEND_URL/api/webhooks/google-health` (no auth header)
   → 401.
6. `gcloud kms keys list --keyring=auth --location=us-central1
   --project=health-fitness-160` shows
   `google-health-refresh-tokens`.
7. `curl https://health.googleapis.com/v4/projects/health-fitness-160/subscribers`
   (with developer auth) lists exactly one subscriber pointing at the
   backend's webhook endpoint.
8. The user document for a connected user has a populated
   `googleHealth` block; `refreshTokenCiphertext` and `dekCiphertext`
   are non-empty byte blobs and the raw refresh token never appears in
   logs.

## Open questions resolved before implementation

- **Architecture choice** — Webhook + REST. Architecture.md commit
  honored.
- **Refresh token storage** — Per-user, KMS-encrypted in Firestore.
  ADR-0004.
- **Subscriber granularity** — One per environment (per Google Cloud
  project). Per-user state is tracked automatically by Google once each
  user grants the scope.
- **Scope rollout** — Incremental authorization on first body-comp
  visit. Existing sessions untouched.
- **Data types in scope** — Four: weight, body fat, lean mass, BMI.
  Stored in one collection differentiated by an enum field.
- **Backfill window** — 4 years (1460 days) on first connect, chunked
  into yearly REST queries to keep response sizes bounded.
  Configurable.
- **healthUserId discovery** — On first API call after connect, parsed
  from the response's record `name`. Persisted on the user document.
- **DELETE notifications** — Translated to Firestore deletes for all
  records in the interval matching the notification's data type.
- **Android / Wear** — Out of scope. Phone reads the synced data from
  Firestore via the existing `/api/me/*` surface.

## Open questions deferred to implementation

- **Mapping `dataPoint.dataSource.platform` enum values to our
  `sourcePlatform` field** — start with passthrough strings; normalize
  if/when a UI surface needs uniform vocabulary.
- **Rate limits** — Google Health API rate limits aren't documented at
  the time of writing. If backfill of 365 days exceeds a per-minute
  cap, fall back to chunked windows (e.g., 30 days each). Default
  implementation is one-shot per data type; add chunking if a real run
  shows quota errors.
- **Concurrent webhook notifications for the same user** — Firestore
  writes by `recordId` are inherently idempotent; explicit per-user
  serialization isn't needed unless ordering matters (e.g., DELETE
  followed by UPSERT for the same record). Reassess after the first
  real-data run.
