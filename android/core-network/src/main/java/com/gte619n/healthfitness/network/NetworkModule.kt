package com.gte619n.healthfitness.network

import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Wires the shared OkHttp + Retrofit + Moshi stack.
 *
 * Two things to call out:
 *  1. The bearer-token interceptor is added *before* the logging
 *     interceptor, so the `Authorization` header shows up in the log.
 *  2. The same `OkHttpClient` instance backs both Retrofit and the SSE
 *     consumer — they share the connection pool, the authenticator, and
 *     the auth interceptor.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder()
        // IMPL-AND-01: backend serialises java.time.Instant as ISO-8601
        // strings (e.g. "2025-09-12T08:14:00Z") and java.time.LocalDate
        // as "yyyy-MM-dd". Moshi has no built-in adapters for either,
        // so register them up front rather than threading @Json overrides
        // through every dashboard DTO.
        .add(InstantJsonAdapter)
        .add(LocalDateJsonAdapter)
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    @Provides
    @Singleton
    fun authInterceptor(tokenProvider: AuthTokenProvider): AuthInterceptor =
        AuthInterceptor(tokenProvider)

    @Provides
    @Singleton
    fun tokenAuthenticator(tokenProvider: AuthTokenProvider): TokenAuthenticator =
        TokenAuthenticator(tokenProvider)

    @Provides
    @Singleton
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

    @Provides
    @Singleton
    fun retrofit(
        client: OkHttpClient,
        moshi: Moshi,
        base: BackendBaseUrlProvider,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(base.baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun sseFactory(client: OkHttpClient): EventSource.Factory =
        EventSources.createFactory(client)
}

/**
 * Moshi adapter for [Instant]. Reads ISO-8601 (`Instant.parse`); writes
 * the same. Public so test code can register the same adapter on its
 * own Moshi instances.
 */
object InstantJsonAdapter {
    @FromJson
    fun fromJson(value: String): Instant = Instant.parse(value)

    @ToJson
    fun toJson(value: Instant): String = value.toString()
}

/**
 * Moshi adapter for [LocalDate]. Uses the ISO-8601 calendar-date format
 * (`yyyy-MM-dd`) that Jackson/Spring emit by default.
 */
object LocalDateJsonAdapter {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @FromJson
    fun fromJson(value: String): LocalDate = LocalDate.parse(value, formatter)

    @ToJson
    fun toJson(value: LocalDate): String = value.format(formatter)
}
