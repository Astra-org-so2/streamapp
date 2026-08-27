package com.streamapp.core.di

import com.streamapp.core.streaming.client.StreamingClient
import com.streamapp.core.streaming.fake.FakeStreamingClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingModule {

    @Binds
    @Singleton
    abstract fun bindStreamingClient(
        fakeClient: FakeStreamingClient
    ): StreamingClient
}
