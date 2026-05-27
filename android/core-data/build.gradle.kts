plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gte619n.healthfitness.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(project(":core-domain"))
    // IMPL-AND-00: the AuthTokenProvider adapter that bridges IdTokenCache +
    // GoogleAuthRepository to the network module lives in core-data, so we
    // depend on core-network. Retrofit/OkHttp/Moshi moved to core-network.
    implementation(project(":core-network"))
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // IMPL-02: Credential Manager + Google ID for Google sign-in on phone.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    // IMPL-AND-02: GIS AuthorizationClient for the Google Health scope
    // upgrade. Credential Manager can't request OAuth scopes, so the
    // scope upgrade flow uses play-services-auth instead. The coroutines
    // play-services bridge gives us `Task<...>.await()` for the
    // AuthorizationClient's `Task`-shaped API.
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // IMPL-AND-01: dashboard repository tests use MockWebServer + Turbine.
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.retrofit)
    testImplementation(libs.retrofit.moshi)
    testImplementation(libs.moshi)
    testImplementation(libs.moshi.kotlin)
    // IMPL-AND-03: DrugLookupStreamClient tests need OkHttp SSE directly
    // (the prod path gets it transitively through core-network's
    // implementation() dependency).
    testImplementation(libs.okhttp.sse)
}
