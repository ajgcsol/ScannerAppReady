package com.yourorg.scanner.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.yourorg.scanner.model.Student
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor() {
    
    private val firestore = FirebaseFirestore.getInstance()
    
    // Cache for student search optimization
    private var cachedStudents: List<Student>? = null
    private var cacheLastUpdated: Long = 0
    private val cacheValidityMs = 5 * 60 * 1000L // 5 minutes
    
    companion object {
        private const val TAG = "StudentRepository"
        private const val STUDENTS_COLLECTION = "students"
        private const val SCANS_COLLECTION = "scans"
    }
    
    /**
     * Look up student by student ID
     */
    suspend fun findStudentById(studentId: String): Student? {
        return try {
            Log.d(TAG, "Looking up student with ID: $studentId")
            
            val document = firestore.collection(STUDENTS_COLLECTION)
                .document(studentId)
                .get()
                .await()
            
            if (document.exists()) {
                val data = document.data ?: return null
                val student = Student(
                    studentId = data["studentId"] as? String ?: studentId,
                    firstName = data["firstName"] as? String ?: "",
                    lastName = data["lastName"] as? String ?: "",
                    email = data["email"] as? String ?: "",
                    program = data["program"] as? String ?: "",
                    year = data["year"] as? String ?: "",
                    active = data["active"] as? Boolean ?: true
                )
                
                Log.d(TAG, "Found student: ${student.firstName} ${student.lastName}")
                student
            } else {
                Log.d(TAG, "Student not found with ID: $studentId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up student: $studentId", e)
            null
        }
    }
    
    /**
     * Record a scan event with verification status
     */
    suspend fun recordScan(studentId: String, student: Student?, deviceId: String): Boolean {
        return try {
            val scanRecord = hashMapOf(
                "code" to studentId,
                "studentId" to studentId,
                "timestamp" to System.currentTimeMillis(),
                "deviceId" to deviceId,
                "verified" to (student != null),
                "symbology" to "QR_CODE",
                "listId" to "default-list",
                "userId" to "scanner-user",
                "synced" to true
            )
            
            // Add student info if found
            student?.let {
                scanRecord["firstName"] = it.firstName
                scanRecord["lastName"] = it.lastName
                scanRecord["email"] = it.email
                scanRecord["program"] = it.program
                scanRecord["year"] = it.year
            }
            
            firestore.collection(SCANS_COLLECTION)
                .add(scanRecord)
                .await()
            
            Log.d(TAG, "Scan recorded for student ID: $studentId, verified: ${student != null}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording scan for student: $studentId", e)
            false
        }
    }
    
    /**
     * Get all students (for admin purposes)
     */
    fun getAllStudents(): Flow<List<Student>> = flow {
        try {
            val snapshot = firestore.collection(STUDENTS_COLLECTION)
                .orderBy("lastName")
                .get()
                .await()
            
            val students = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                Student(
                    studentId = data["studentId"] as? String ?: doc.id,
                    firstName = data["firstName"] as? String ?: "",
                    lastName = data["lastName"] as? String ?: "",
                    email = data["email"] as? String ?: "",
                    program = data["program"] as? String ?: "",
                    year = data["year"] as? String ?: "",
                    active = data["active"] as? Boolean ?: true
                )
            }
            
            emit(students)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all students", e)
            emit(emptyList())
        }
    }
    
    /**
     * Get all students with caching for performance
     */
    private suspend fun getAllStudentsFromFirestore(): List<Student> {
        // Check cache first
        val now = System.currentTimeMillis()
        if (cachedStudents != null && (now - cacheLastUpdated) < cacheValidityMs) {
            Log.d(TAG, "Returning ${cachedStudents!!.size} students from cache")
            return cachedStudents!!
        }
        
        Log.d(TAG, "Cache miss or expired, fetching students from Firestore")
        
        val allStudentsSnapshot = firestore.collection(STUDENTS_COLLECTION)
            .get()
            .await()
        
        Log.d(TAG, "Fetched ${allStudentsSnapshot.size()} students from Firestore")
        
        val students = allStudentsSnapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            Student(
                studentId = data["studentId"] as? String ?: doc.id,
                firstName = data["firstName"] as? String ?: "",
                lastName = data["lastName"] as? String ?: "",
                email = data["email"] as? String ?: "",
                program = data["program"] as? String ?: "",
                year = data["year"] as? String ?: "",
                active = data["active"] as? Boolean ?: true
            )
        }
        
        // Update cache
        cachedStudents = students
        cacheLastUpdated = now
        
        return students
    }
    
    /**
     * Search students by name or ID - optimized with caching
     */
    suspend fun searchStudents(query: String): List<Student> {
        return try {
            Log.d(TAG, "Searching for students with query: '$query'")
            
            if (query.length < 2) {
                return emptyList()
            }
            
            // Get all students (potentially from cache)
            val allStudents = getAllStudentsFromFirestore()
            
            if (allStudents.isEmpty()) {
                Log.w(TAG, "No students found in database! Please upload student data first.")
                return emptyList()
            }
            
            // Optimized filtering with early returns and case-insensitive matching
            val queryLower = query.lowercase().trim()
            val matchedStudents = allStudents.asSequence()
                .filter { student ->
                    student.studentId.lowercase().contains(queryLower) ||
                    student.firstName.lowercase().contains(queryLower) ||
                    student.lastName.lowercase().contains(queryLower) ||
                    "${student.firstName} ${student.lastName}".lowercase().contains(queryLower) ||
                    student.email.lowercase().contains(queryLower)
                }
                .sortedWith(compareBy<Student> { student ->
                    // Prioritize exact matches
                    when {
                        student.studentId.equals(query, ignoreCase = true) -> 0
                        student.firstName.equals(query, ignoreCase = true) -> 1
                        student.lastName.equals(query, ignoreCase = true) -> 2
                        "${student.firstName} ${student.lastName}".equals(query, ignoreCase = true) -> 3
                        student.studentId.startsWith(queryLower, ignoreCase = true) -> 4
                        student.firstName.startsWith(queryLower, ignoreCase = true) -> 5
                        student.lastName.startsWith(queryLower, ignoreCase = true) -> 6
                        else -> 7
                    }
                }.thenBy { it.lastName }.thenBy { it.firstName })
                .take(25) // Reduced limit for better performance
                .toList()
            
            Log.d(TAG, "Found ${matchedStudents.size} matching students for query '$query'")
            
            matchedStudents
        } catch (e: Exception) {
            Log.e(TAG, "Error searching students: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Clear the cache (call when students are updated)
     */
    fun clearCache() {
        cachedStudents = null
        cacheLastUpdated = 0
        Log.d(TAG, "Student cache cleared")
    }
    
    /**
     * Update analytics after successful scan
     */
    suspend fun updateScanAnalytics(verified: Boolean) {
        try {
            val today = com.google.firebase.Timestamp.now()
            val analyticsRef = firestore.collection("analytics").document("daily")
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(analyticsRef)
                val currentData = snapshot.data ?: hashMapOf()
                
                val totalScans = (currentData["totalScans"] as? Long ?: 0) + 1
                val verifiedScans = if (verified) {
                    (currentData["verifiedScans"] as? Long ?: 0) + 1
                } else {
                    currentData["verifiedScans"] as? Long ?: 0
                }
                
                val updatedData = hashMapOf(
                    "totalScans" to totalScans,
                    "verifiedScans" to verifiedScans,
                    "lastUpdated" to today
                )
                
                transaction.set(analyticsRef, updatedData, com.google.firebase.firestore.SetOptions.merge())
            }.await()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scan analytics", e)
        }
    }
}