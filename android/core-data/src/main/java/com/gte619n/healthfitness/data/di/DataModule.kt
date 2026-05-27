package com.gte619n.healthfitness.data.di

import com.gte619n.healthfitness.data.auth.IdTokenCacheAuthTokenProvider
import com.gte619n.healthfitness.network.AuthTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings owned by core-data. Currently this is just the
 * AuthTokenProvider adapter; per-feature DAO/repository bindings will land
 * here too as later IMPLs add real Retrofit interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthTokenProvider(
        impl: IdTokenCacheAuthTokenProvider,
    ): AuthTokenProvider
}
