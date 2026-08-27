package com.streamapp.core.database.dao

import androidx.room.*
import com.streamapp.core.database.entity.AudioTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioTrackDao {

    @Query("SELECT * FROM audio_tracks ORDER BY orderIndex ASC, createdAt ASC")
    fun getAllTracks(): Flow<List<AudioTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: AudioTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<AudioTrackEntity>)

    @Delete
    suspend fun deleteTrack(track: AudioTrackEntity)

    @Query("DELETE FROM audio_tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("DELETE FROM audio_tracks")
    suspend fun clearAllTracks()
}
