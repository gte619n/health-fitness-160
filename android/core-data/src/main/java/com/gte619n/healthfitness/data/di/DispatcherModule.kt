package com.gte619n.healthfitness.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.BINARY

@Qualifier
@Retention(BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(BINARY)
annotation class MainDispatcher

/**
 * Surfaces the three coroutine dispatchers through Hilt so tests can swap
 * them for the `kotlinx-coroutines-test` `StandardTestDispatcher` without
 * reaching into globals. Per spec every suspending repository call uses
 * the injected `IoDispatcher`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}
