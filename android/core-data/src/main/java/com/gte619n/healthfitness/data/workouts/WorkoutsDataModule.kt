package com.gte619n.healthfitness.data.workouts

import com.gte619n.healthfitness.domain.workouts.EquipmentRepository
import com.gte619n.healthfitness.domain.workouts.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt bindings for the gym/equipment feature.
 *
 * Mirrors the [com.gte619n.healthfitness.data.blood.BloodDataModule] /
 * [com.gte619n.healthfitness.data.bodycomposition.BodyCompositionDataModule]
 * split: `@Binds` for repository interfaces, companion `@Provides`
 * methods for the Retrofit-built services so Retrofit's `create()`
 * stays out of feature modules.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkoutsDataModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl,
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindEquipmentRepository(
        impl: EquipmentRepositoryImpl,
    ): EquipmentRepository

    companion object {
        @Provides
        @Singleton
        internal fun provideLocationApi(retrofit: Retrofit): LocationApi =
            retrofit.create(LocationApi::class.java)

        @Provides
        @Singleton
        internal fun provideEquipmentApi(retrofit: Retrofit): EquipmentApi =
            retrofit.create(EquipmentApi::class.java)
    }
}
