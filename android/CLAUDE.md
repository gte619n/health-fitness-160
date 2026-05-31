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

## UI conventions
- **No white form/settings backgrounds.** Settings, profile, and other form
  screens sit directly on the canvas (`Hf.colors.canvas`) — never the white
  `Hf.colors.surface` fill. Wrap grouped content in `HfCard(transparent = true)`
  (border only, no fill) or a plain container. The filled `HfCard` is reserved
  for data/dashboard cards. Don't introduce `.background(Hf.colors.surface)` on a
  form surface.
- **Edge-to-edge insets.** The activity runs edge-to-edge, so every top-level
  screen must apply `windowInsetsPadding(WindowInsets.systemBars)` (or
  `statusBars`/`navigationBars`) to its root container, or use a `Scaffold`.
  Forgetting this puts top bars / back buttons *under* the status bar where taps
  don't register.
