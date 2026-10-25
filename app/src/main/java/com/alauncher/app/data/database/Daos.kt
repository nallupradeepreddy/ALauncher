package com.alauncher.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetadataDao {
    @Query("SELECT * FROM app_metadata")
    fun getAllMetadataFlow(): Flow<List<AppMetadataEntity>>

    @Query("SELECT * FROM app_metadata")
    suspend fun getAllMetadataDirect(): List<AppMetadataEntity>

    @Query("SELECT * FROM app_metadata WHERE packageName = :packageName")
    suspend fun getMetadata(packageName: String): AppMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: AppMetadataEntity)

    @Query("UPDATE app_metadata SET isFavorite = :isFavorite WHERE packageName = :packageName")
    suspend fun updateFavorite(packageName: String, isFavorite: Boolean)

    @Query("UPDATE app_metadata SET isHidden = :isHidden WHERE packageName = :packageName")
    suspend fun updateHidden(packageName: String, isHidden: Boolean)

    @Query("UPDATE app_metadata SET launchCount = launchCount + 1, lastOpenedTime = :time WHERE packageName = :packageName")
    suspend fun incrementLaunch(packageName: String, time: Long): Int

    @Query("UPDATE app_metadata SET launchCount = 0, lastOpenedTime = 0")
    suspend fun resetAllAppStats()

    @Query("DELETE FROM app_metadata WHERE packageName = :packageName")
    suspend fun deleteMetadata(packageName: String): Int

    @androidx.room.Transaction
    suspend fun upsertFavorite(packageName: String, isFavorite: Boolean) {
        val existing = getMetadata(packageName)
        if (existing == null) {
            insertMetadata(AppMetadataEntity(packageName = packageName, isFavorite = isFavorite))
        } else {
            updateFavorite(packageName, isFavorite)
        }
    }

    @androidx.room.Transaction
    suspend fun upsertHidden(packageName: String, isHidden: Boolean) {
        val existing = getMetadata(packageName)
        if (existing == null) {
            insertMetadata(AppMetadataEntity(packageName = packageName, isHidden = isHidden))
        } else {
            updateHidden(packageName, isHidden)
        }
    }

    @androidx.room.Transaction
    suspend fun recordLaunchAtomic(packageName: String, time: Long) {
        val updatedRows = incrementLaunch(packageName, time)
        if (updatedRows == 0) {
            insertMetadata(
                AppMetadataEntity(
                    packageName = packageName,
                    launchCount = 1,
                    lastOpenedTime = time
                )
            )
        }
    }
}

