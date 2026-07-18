package com.junkfood.seal.database.objects

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "download_operation",
    indices = [
        Index(value = ["url"]),
        Index(value = ["title"])
    ]
)
@Serializable
data class DownloadOperation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val status: String,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val fileSize: Long = 0,
    val filePath: String? = null,
    val playlistName: String? = null,
    val playlistIndex: Int? = null
)
