package com.yourorg.scanner.data.room

import androidx.room.*
import com.yourorg.scanner.model.Student
import com.yourorg.scanner.model.ScanRecord

@Entity(tableName = "scans", primaryKeys = ["id"])
data class ScanEntity(
    val id: String,
    val code: String,
    val symbology: String?,
    val timestamp: Long,
    val deviceId: String,
    val userId: String?,
    val listId: String,
    val synced: Boolean = false,
    // Extended fields for offline support
    val verified: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val program: String = "",
    val year: String = "",
    val eventId: String? = null
) {
    fun toScanRecord(): ScanRecord {
        return ScanRecord(
            id = id,
            code = code,
            symbology = symbology ?: "QR_CODE",
            timestamp = timestamp,
            deviceId = deviceId,
            userId = userId ?: "scanner-user",
            listId = listId,
            synced = synced,
            verified = verified,
            firstName = firstName,
            lastName = lastName,
            email = email,
            program = program,
            year = year,
            eventId = eventId
        )
    }
    
    companion object {
        fun fromScanRecord(scanRecord: ScanRecord): ScanEntity {
            return ScanEntity(
                id = scanRecord.id,
                code = scanRecord.code,
                symbology = scanRecord.symbology,
                timestamp = scanRecord.timestamp,
                deviceId = scanRecord.deviceId,
                userId = scanRecord.userId,
                listId = scanRecord.listId,
                synced = scanRecord.synced,
                verified = scanRecord.verified,
                firstName = scanRecord.firstName,
                lastName = scanRecord.lastName,
                email = scanRecord.email,
                program = scanRecord.program,
                year = scanRecord.year,
                eventId = scanRecord.eventId
            )
        }
    }
}

@Entity(tableName = "students", primaryKeys = ["studentId"])
data class StudentEntity(
    val studentId: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val program: String,
    val year: String,
    val active: Boolean = true,
    val lastSyncTime: Long = System.currentTimeMillis()
) {
    fun toStudent(): Student {
        return Student(
            studentId = studentId,
            firstName = firstName,
            lastName = lastName,
            email = email,
            program = program,
            year = year,
            active = active
        )
    }
    
    companion object {
        fun fromStudent(student: Student): StudentEntity {
            return StudentEntity(
                studentId = student.studentId,
                firstName = student.firstName,
                lastName = student.lastName,
                email = student.email,
                program = student.program,
                year = student.year,
                active = student.active
            )
        }
    }
}

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans WHERE listId = :listId ORDER BY timestamp DESC")
    suspend fun scansForList(listId: String): List<ScanEntity>

    @Query("SELECT * FROM scans WHERE synced = 0 AND listId = :listId")
    suspend fun pending(listId: String): List<ScanEntity>
    
    @Query("SELECT * FROM scans WHERE synced = 0 ORDER BY timestamp")
    suspend fun getUnsyncedScans(): List<ScanEntity>
    
    @Query("SELECT * FROM scans WHERE eventId = :eventId ORDER BY timestamp DESC")
    suspend fun getScansForEvent(eventId: String): List<ScanEntity>
    
    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    suspend fun getAllScans(): List<ScanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ScanEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity)

    @Query("UPDATE scans SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
    
    @Query("UPDATE scans SET synced = 1 WHERE id = :scanId")
    suspend fun markScanAsSynced(scanId: String)
    
    @Query("SELECT COUNT(*) FROM scans WHERE synced = 0")
    suspend fun getUnsyncedScanCount(): Int
    
    @Query("SELECT * FROM scans WHERE code = :studentId AND eventId = :eventId")
    suspend fun getScansForStudentInEvent(studentId: String, eventId: String): List<ScanEntity>
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE studentId = :studentId LIMIT 1")
    suspend fun getStudentById(studentId: String): StudentEntity?
    
    @Query("SELECT * FROM students ORDER BY lastName, firstName")
    suspend fun getAllStudents(): List<StudentEntity>
    
    @Query("""
        SELECT * FROM students 
        WHERE LOWER(studentId) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(firstName) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(lastName) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(firstName || ' ' || lastName) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(email) LIKE '%' || LOWER(:query) || '%'
        ORDER BY 
            CASE 
                WHEN LOWER(studentId) = LOWER(:query) THEN 0
                WHEN LOWER(firstName) = LOWER(:query) THEN 1
                WHEN LOWER(lastName) = LOWER(:query) THEN 2
                WHEN LOWER(firstName || ' ' || lastName) = LOWER(:query) THEN 3
                WHEN LOWER(studentId) LIKE LOWER(:query) || '%' THEN 4
                WHEN LOWER(firstName) LIKE LOWER(:query) || '%' THEN 5
                WHEN LOWER(lastName) LIKE LOWER(:query) || '%' THEN 6
                ELSE 7
            END,
            lastName, firstName
        LIMIT 25
    """)
    suspend fun searchStudents(query: String): List<StudentEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)
    
    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()
    
    @Query("SELECT COUNT(*) FROM students")
    suspend fun getStudentCount(): Int
    
    @Query("SELECT MAX(lastSyncTime) FROM students")
    suspend fun getLastSyncTime(): Long?
}

@Database(entities = [ScanEntity::class, StudentEntity::class], version = 2)
abstract class AppDb: RoomDatabase() { 
    abstract fun scanDao(): ScanDao 
    abstract fun studentDao(): StudentDao
}
