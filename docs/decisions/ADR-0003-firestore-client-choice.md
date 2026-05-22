# ADR-0003: Direct `google-cloud-firestore` client over the Spring Data starter

- Status: Accepted
- Date: 2026-05-21

## Context

The backend needs a Firestore client. Two libraries were declared in
`gradle/libs.versions.toml` during IMPL-00 but neither was used yet:

1. **`com.google.cloud:google-cloud-firestore`** — the low-level Google
   client. Synchronous-blocking APIs that return `ApiFuture<T>`. Manual
   document-to-record mapping. Direct access to the full Firestore feature
   set (transactions, batched writes, listeners, `FieldValue.serverTimestamp()`).
2. **`com.google.cloud:spring-cloud-gcp-starter-data-firestore`** — Spring
   Data wrapper. Reactive (`Mono`/`Flux`) repository abstraction.
   `@Document`-annotated POJOs. Hides the underlying client.

Constraints that influenced the choice:

- `backend/CLAUDE.md` rules: *"Don't add `spring-boot-starter-webflux`
  without a real reason — reactive is heavier to read and maintain than
  synchronous-on-virtual-threads for this app."* The Spring Data Firestore
  starter pulls Reactor into the dependency graph and turns every
  repository signature into `Mono<T>` / `Flux<T>`.
- Java 21 with virtual threads enabled. A blocking `ApiFuture#get()`
  parks a virtual thread, not a carrier thread, so the synchronous client
  scales fine for the request volumes this app will see.
- The repository surface is tiny (User, Workout, DailyMetric) and the
  mapping per type is ~20 lines. Spring Data saves boilerplate at scale;
  at this scale the savings are negative once Reactor signatures and
  `@Document` annotations get factored in.
- Firestore-specific features we need or will need are easier to reach on
  the low-level client: `FieldValue.serverTimestamp()` for audit fields,
  `runTransaction` for read-modify-write, batched writes for ingestion.

## Decision

Adopt **`com.google.cloud:google-cloud-firestore`** as the only Firestore
client in the backend. Remove `spring-cloud-gcp-starter-data-firestore`
from `gradle/libs.versions.toml`.

- A single `Firestore` bean is configured in `persistence/FirestoreConfig`
  using `FirestoreOptions.getDefaultInstance()`.
- Repositories under `persistence/` are plain `@Repository` classes that
  call the Firestore SDK directly and return `Optional<T>` / `List<T>`.
- A small `FirestoreMapper` holds shared conversions (`Instant` ⇄
  `Timestamp`, `LocalDate` → ISO-8601 document ID).
- The `Firestore` SDK auto-detects `FIRESTORE_EMULATOR_HOST`, so local
  development and Testcontainers-driven tests use the same code path as
  production.

## Consequences

Positive:

- No Reactor types in `core` or `persistence`. The whole backend stays
  synchronous-on-virtual-threads, matching the rule in `backend/CLAUDE.md`.
- Direct access to Firestore primitives — server timestamps, transactions,
  batched writes — without fighting through a Spring Data abstraction.
- One dependency to keep current. The Spring Data starter is removed from
  `libs.versions.toml`, so the catalog reflects what's actually compiled in.
- Testcontainers + the emulator gives real Firestore semantics in tests
  without needing the Spring Data layer.

Negative:

- ~20 lines of mapping code per entity, indefinitely. At ~3 entities planned
  for the first year, that's ~60 lines we wouldn't otherwise write.
- No automatic schema-style validation of stored documents — a field
  rename has to be coordinated by hand.
- If we ever want a streaming listener (`addSnapshotListener`) feeding a
  Server-Sent Events endpoint, we'll wrap it ourselves rather than
  inheriting a `Flux<T>` from Spring Data.

## Revisit when

- Repository count grows past ~10 and per-entity mapping becomes a
  meaningful share of the codebase.
- We adopt reactive style elsewhere in the backend (e.g. a streaming
  LLM endpoint backed by Firestore listeners) and want a uniform
  programming model across the persistence path.
- Spring Data Firestore ships a non-reactive variant (it does not, today).
