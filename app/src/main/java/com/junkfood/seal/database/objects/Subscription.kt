package com.junkfood.seal.database.objects

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Subscription")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val lastCheckedTimestamp: Long
)
