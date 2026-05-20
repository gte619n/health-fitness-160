# CLAUDE.md — android

- Modular Compose architecture. **All UI in Compose**, no XML layouts beyond
  the obligatory theme XML and manifest.
- **DI**: Hilt (added in later impls; build classpath only for now).
- **Persistence**: Room as source of truth, DataStore for prefs.
- **Network**: Retrofit + OkHttp + Moshi.
- All async work via Coroutines + Flow.
- **Health Connect access goes through `core-health` only.** App and feature
  modules never import `androidx.health.connect:*` directly.
- **Wear module shares `core-domain` and `core-ui` (when relevant) but never
  depends on `app`** — pairing is by `applicationId`, not module dependency.
- Phone and wear share `applicationId` (required for pairing); namespaces differ.
- JVM toolchain 21. Min SDK 29 (phone), 30 (wear). Target SDK 35.
