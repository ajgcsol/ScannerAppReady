package com.yourorg.scanner.data.room

import androidx.room.*

@Entity(tableName = "scans", primaryKeys = ["id"])
data class ScanEntity(
    val id: String,
    val code: String,
    val symbology: String?,
    val timestamp: Long,
    val deviceId: String,
    val userId: String?,
    val listId: String,
    val synced: Boolean = false
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans WHERE listId = :listId ORDER BY timestamp DESC")
    suspend fun scansForList(listId: String): List<ScanEntity>

    @Query("SELECT * FROM scans WHERE synced = 0 AND listId = :listId")
    suspend fun pending(listId: String): List<ScanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ScanEntity>)

    @Query("UPDATE scans SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Database(entities = [ScanEntity::class], version = 1)
abstract class AppDb: RoomDatabase() { abstract fun scanDao(): ScanDao }
