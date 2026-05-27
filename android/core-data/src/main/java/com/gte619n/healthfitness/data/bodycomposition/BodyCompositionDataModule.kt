package com.gte619n.healthfitness.data.bodycomposition

import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionRepository
import com.gte619n.healthfitness.domain.bodycomposition.DexaScanRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt bindings for the body-composition + DEXA repositories and the
 * Retrofit-built services. Matches the [com.gte619n.healthfitness.data.blood.BloodDataModule]
 * split: `@Binds` for repos, companion `@Provides` for the API.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BodyCompositionDataModule {

    @Binds
    @Singleton
    abstract fun bindBodyCompositionRepository(
        impl: BodyCompositionRepositoryImpl,
    ): BodyCompositionRepository

    @Binds
    @Singleton
    abstract fun bindDexaScanRepository(
        impl: DexaScanRepositoryImpl,
    ): DexaScanRepository

    companion object {
        @Provides
        @Singleton
        internal fun provideBodyCompositionApi(retrofit: Retrofit): BodyCompositionApi =
            retrofit.create(BodyCompositionApi::class.java)

        @Provides
        @Singleton
        internal fun provideDexaScanApi(retrofit: Retrofit): DexaScanApi =
            retrofit.create(DexaScanApi::class.java)
    }
}
