package com.junkfood.seal.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.junkfood.seal.database.objects.Subscription
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Insert
    suspend fun insert(subscription: Subscription)

    @Update
    suspend fun update(subscription: Subscription)

    @Delete
    suspend fun delete(subscription: Subscription)

    @Query("SELECT * FROM Subscription")
    fun getAllSubscriptionsFlow(): Flow<List<Subscription>>

    @Query("SELECT * FROM Subscription")
    suspend fun getAllSubscriptions(): List<Subscription>

    @Query("SELECT * FROM Subscription WHERE id = :id")
    suspend fun getSubscriptionById(id: Int): Subscription?
}
