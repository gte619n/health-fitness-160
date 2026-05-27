package com.gte619n.healthfitness.data.medications

import com.gte619n.healthfitness.domain.medications.AdherenceRepository
import com.gte619n.healthfitness.domain.medications.DrugRepository
import com.gte619n.healthfitness.domain.medications.MedicationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt bindings for the three medication repositories + the three
 * Retrofit-built APIs. `@Binds` for the repos, companion `@Provides` for
 * the APIs — the standard Dagger split that matches DashboardDataModule.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class MedicationsDataModule {

    @Binds
    @Singleton
    abstract fun bindMedicationRepository(
        impl: DefaultMedicationRepository,
    ): MedicationRepository

    @Binds
    @Singleton
    abstract fun bindDrugRepository(
        impl: DefaultDrugRepository,
    ): DrugRepository

    @Binds
    @Singleton
    abstract fun bindAdherenceRepository(
        impl: DefaultAdherenceRepository,
    ): AdherenceRepository

    companion object {
        @Provides
        @Singleton
        internal fun provideMedicationsApi(retrofit: Retrofit): MedicationsApi =
            retrofit.create(MedicationsApi::class.java)

        @Provides
        @Singleton
        internal fun provideDrugsApi(retrofit: Retrofit): DrugsApi =
            retrofit.create(DrugsApi::class.java)

        @Provides
        @Singleton
        internal fun provideAdherenceApi(retrofit: Retrofit): AdherenceApi =
            retrofit.create(AdherenceApi::class.java)
    }
}
