package com.gte619n.healthfitness.data.auth

/**
 * Single source of truth for the Google Health OAuth scope on Android.
 * Mirrors `GOOGLE_HEALTH_SCOPE` in `web/auth.ts` so the same scope
 * string is used for both client OAuth flows.
 */
object GoogleHealthScopes {
    const val METRICS_READ_ONLY: String =
        "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly"
}
