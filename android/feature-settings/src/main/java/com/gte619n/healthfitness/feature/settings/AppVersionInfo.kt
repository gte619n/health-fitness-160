package com.gte619n.healthfitness.feature.settings

/**
 * App-version metadata surfaced by the About card. Provided by `app/`'s
 * Hilt module so this feature module doesn't have to depend on the app's
 * `BuildConfig` (which is flavor-scoped and only resolvable inside
 * `:app`).
 */
data class AppVersionInfo(
    val versionName: String,
    val versionCode: Int,
)
