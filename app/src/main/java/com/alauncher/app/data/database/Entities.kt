package com.alauncher.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val packageName: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val launchCount: Int = 0,
    val lastOpenedTime: Long = 0,
    val sortWeight: Int = 0
)

