package com.gte619n.healthfitness.data.dashboard

import com.gte619n.healthfitness.domain.dashboard.BloodMarkerSummaryRepository
import com.gte619n.healthfitness.domain.dashboard.TodaysDosesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt bindings for the dashboard's blood-panel + today's-doses repos
 * and the Retrofit-built [DashboardApi]. Companion `@Provides` for the
 * API; `@Binds` for the repos — the standard Dagger split.
 *
 * Round 2 Stage C: body-composition binding moved to
 * [com.gte619n.healthfitness.data.bodycomposition.BodyCompositionDataModule].
 * The dashboard now consumes the canonical
 * `domain.bodycomposition.BodyCompositionRepository`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DashboardDataModule {

    @Binds
    @Singleton
    abstract fun bindBloodMarkerSummaryRepository(
        impl: BloodMarkerSummaryRepositoryImpl,
    ): BloodMarkerSummaryRepository

    @Binds
    @Singleton
    abstract fun bindTodaysDosesRepository(
        impl: TodaysDosesRepositoryImpl,
    ): TodaysDosesRepository

    companion object {
        @Provides
        @Singleton
        internal fun provideDashboardApi(retrofit: Retrofit): DashboardApi =
            retrofit.create(DashboardApi::class.java)
    }
}
