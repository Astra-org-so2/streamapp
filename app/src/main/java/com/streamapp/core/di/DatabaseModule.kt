package com.streamapp.core.di

import android.content.Context
import androidx.room.Room
import com.streamapp.core.database.AppDatabase
import com.streamapp.core.database.dao.AudioTrackDao
import com.streamapp.core.database.dao.DestinationDao
import com.streamapp.core.database.dao.SceneDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "streamapp_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideDestinationDao(appDatabase: AppDatabase): DestinationDao {
        return appDatabase.destinationDao()
    }

    @Provides
    fun provideSceneDao(appDatabase: AppDatabase): SceneDao {
        return appDatabase.sceneDao()
    }

    @Provides
    fun provideAudioTrackDao(appDatabase: AppDatabase): AudioTrackDao {
        return appDatabase.audioTrackDao()
    }
}
