# health-fitness-android

Native Android (Kotlin 2.0, Jetpack Compose, Material 3), multi-module.
Phone app + standalone Wear OS app, sharing `core-*` modules.

## Modules
- `app` — phone application (`com.gte619n.healthfitness.mobile`)
- `wear` — Wear OS application (`com.gte619n.healthfitness.wear`)
- `core-data` — Room, DataStore, Retrofit
- `core-domain` — use cases + models, pure Kotlin
- `core-ui` — Compose theme + shared composables (phone)
- `core-health` — Health Connect wrapper
- `feature-workouts`, `feature-medical`, `feature-chat` — feature modules

## Build
```bash
cp local.properties.example local.properties   # edit sdk.dir
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug
```

Phone and Wear share `applicationId` (`com.gte619n.healthfitness`) for pairing,
but use different namespaces.
