package com.streamapp.core.di

import com.streamapp.core.common.dispatchers.AppDispatchers
import com.streamapp.core.common.dispatchers.DefaultAppDispatchers
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatchersModule {

    @Binds
    @Singleton
    abstract fun bindAppDispatchers(
        defaultAppDispatchers: DefaultAppDispatchers
    ): AppDispatchers
}
