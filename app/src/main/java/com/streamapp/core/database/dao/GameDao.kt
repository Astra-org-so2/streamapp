package com.streamapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streamapp.core.database.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title ASC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE category = :category ORDER BY title ASC")
    fun getGamesByCategory(category: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE lastPlayedTimestamp IS NOT NULL ORDER BY lastPlayedTimestamp DESC LIMIT 10")
    fun getRecentGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE lastPlayedTimestamp IS NOT NULL ORDER BY lastPlayedTimestamp DESC LIMIT 3")
    fun getContinuePlaying(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games LIMIT 1")
    fun getFeaturedGame(): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGameById(id: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<GameEntity>)

    @Update
    suspend fun updateGame(game: GameEntity)

    @Query("UPDATE games SET isFavorite = NOT isFavorite WHERE id = :appId")
    suspend fun toggleFavorite(appId: String)

    @Query("UPDATE games SET lastPlayedTimestamp = :timestamp WHERE id = :appId")
    suspend fun updateLastPlayed(appId: String, timestamp: Long)
}
