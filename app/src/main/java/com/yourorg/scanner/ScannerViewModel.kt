package com.yourorg.scanner

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.yourorg.scanner.data.room.AppDb
import com.yourorg.scanner.data.room.ScanEntity
import com.yourorg.scanner.data.sync.FirestoreSync
import com.yourorg.scanner.data.StudentRepository
import com.yourorg.scanner.model.ScanRecord
import com.yourorg.scanner.model.Student
import com.yourorg.scanner.model.Event
import com.yourorg.scanner.model.EventAttendee
import com.yourorg.scanner.data.EventRepository
import com.yourorg.scanner.scanner.HoneywellScanner
import com.yourorg.scanner.scanner.CameraXScanner
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class ScannerUiState(
    val scans: List<ScanRecord> = emptyList(),
    val currentListId: String = "default-list",
    val lastScan: ScanRecord? = null,
    val scanCount: Int = 0,
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val isExporting: Boolean = false,
    val deviceInfo: String = "",
    val userName: String = "User",
    val showStudentDialog: Boolean = false,
    val verifiedStudent: Student? = null,
    val scannedStudentId: String = "",
    val showForgotIdDialog: Boolean = false,
    val isSearchingStudents: Boolean = false,
    val studentSearchResults: List<Student> = emptyList(),
    // Camera scanning
    val showCameraPreview: Boolean = false,
    // Event management
    val currentEvent: Event? = null,
    val availableEvents: List<Event> = emptyList(),
    val showEventSelector: Boolean = false,
    val showNewEventDialog: Boolean = false
)

class ScannerViewModel(
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "ScannerViewModel"
        private const val PREFS_NAME = "scanner_prefs"
        private const val KEY_CURRENT_EVENT_ID = "current_event_id"
    }

    // Initialize database and sync
    private val database = Room.databaseBuilder(
        context,
        AppDb::class.java,
        "scanner_db"
    ).build()
    
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scanDao = database.scanDao()
    private val firestoreSync = FirestoreSync()
    private val honeywellScanner = HoneywellScanner(context)
    private val studentRepository = StudentRepository()
    private val eventRepository = EventRepository()

    // UI State
    private val _uiState = MutableStateFlow(
        ScannerUiState(
            deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            userName = "Scanner User"
        )
    )
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    init {
        initializeScanner()
        loadEvents()
        loadScans()
        observeFirestoreChanges()
    }

    private fun initializeScanner() {
        viewModelScope.launch {
            try {
                honeywellScanner.initialize { scanResult ->
                    handleScanResult(scanResult.code, scanResult.symbology)
                }
                updateState { it.copy(isConnected = true, errorMessage = null) }
                Log.d(TAG, "Scanner initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize scanner", e)
                updateState { 
                    it.copy(
                        isConnected = false, 
                        errorMessage = "Scanner not available: ${e.message}"
                    ) 
                }
            }
        }
    }

    private fun loadScans() {
        viewModelScope.launch {
            try {
                val currentListId = _uiState.value.currentListId
                val localScans = scanDao.scansForList(currentListId)
                val scanRecords = localScans.map { entity ->
                    ScanRecord(
                        id = entity.id,
                        code = entity.code,
                        symbology = entity.symbology,
                        timestamp = entity.timestamp,
                        deviceId = entity.deviceId,
                        userId = entity.userId,
                        listId = entity.listId
                    )
                }
                
                updateState { 
                    it.copy(
                        scans = scanRecords,
                        lastScan = scanRecords.firstOrNull(),
                        scanCount = scanRecords.size
                    ) 
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading scans", e)
                updateState { it.copy(errorMessage = "Failed to load scans: ${e.message}") }
            }
        }
    }

    private fun observeFirestoreChanges() {
        viewModelScope.launch {
            firestoreSync.listenScans(_uiState.value.currentListId).collect { cloudScans ->
                // Merge cloud scans with local data
                if (cloudScans.isNotEmpty()) {
                    syncCloudScans(cloudScans)
                }
            }
        }
    }

    private suspend fun syncCloudScans(cloudScans: List<ScanRecord>) {
        try {
            val entities = cloudScans.map { scan ->
                ScanEntity(
                    id = scan.id,
                    code = scan.code,
                    symbology = scan.symbology,
                    timestamp = scan.timestamp,
                    deviceId = scan.deviceId,
                    userId = scan.userId,
                    listId = scan.listId,
                    synced = true
                )
            }
            
            scanDao.upsertAll(entities)
            loadScans() // Refresh UI
            Log.d(TAG, "Synced ${cloudScans.size} scans from cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing cloud scans", e)
        }
    }

    private fun handleScanResult(code: String, symbology: String?) {
        viewModelScope.launch {
            try {
                updateState { it.copy(isScanning = true) }
                
                // Look up student by scanned ID
                val student = studentRepository.findStudentById(code)
                
                // Record the scan with verification status
                studentRepository.recordScan(
                    studentId = code,
                    student = student,
                    deviceId = _uiState.value.deviceInfo
                )
                
                // Update analytics
                studentRepository.updateScanAnalytics(student != null)
                
                val scanRecord = ScanRecord(
                    id = UUID.randomUUID().toString(),
                    code = code,
                    symbology = symbology ?: "UNKNOWN",
                    timestamp = System.currentTimeMillis(),
                    deviceId = _uiState.value.deviceInfo,
                    userId = _uiState.value.userName,
                    listId = _uiState.value.currentListId
                )

                // Save locally
                val entity = ScanEntity(
                    id = scanRecord.id,
                    code = scanRecord.code,
                    symbology = scanRecord.symbology,
                    timestamp = scanRecord.timestamp,
                    deviceId = scanRecord.deviceId,
                    userId = scanRecord.userId,
                    listId = scanRecord.listId,
                    synced = false
                )
                
                scanDao.upsertAll(listOf(entity))

                // Upload to cloud
                firestoreSync.addScan(_uiState.value.currentListId, scanRecord)
                scanDao.markSynced(listOf(scanRecord.id))

                loadScans() // Refresh UI
                
                // Show student verification dialog
                updateState { 
                    it.copy(
                        showStudentDialog = true,
                        verifiedStudent = student,
                        scannedStudentId = code,
                        isScanning = false
                    )
                }
                
                Log.d(TAG, "Scan processed: $code ($symbology) - Student ${if (student != null) "found" else "not found"}")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing scan", e)
                updateState { 
                    it.copy(
                        isScanning = false,
                        errorMessage = "Failed to process scan: ${e.message}"
                    ) 
                }
            }
        }
    }

    fun triggerScan() {
        // Show camera preview for scanning
        updateState { it.copy(showCameraPreview = true) }
    }
    
    fun hideCameraPreview() {
        updateState { it.copy(showCameraPreview = false) }
    }
    
    fun onCameraScanResult(scanResult: com.yourorg.scanner.scanner.ScanResult) {
        hideCameraPreview()
        handleScanResult(scanResult.code, scanResult.symbology)
    }

    fun clearError() {
        updateState { it.copy(errorMessage = null) }
    }

    fun setListId(listId: String) {
        updateState { it.copy(currentListId = listId) }
        loadScans()
        observeFirestoreChanges()
    }

    fun setUserName(userName: String) {
        updateState { it.copy(userName = userName) }
    }

    // Forgot ID functionality
    fun showForgotIdDialog() {
        updateState { it.copy(showForgotIdDialog = true) }
    }
    
    fun hideForgotIdDialog() {
        updateState { 
            it.copy(
                showForgotIdDialog = false, 
                studentSearchResults = emptyList(),
                isSearchingStudents = false
            ) 
        }
    }
    
    fun searchStudents(query: String) {
        if (query.length < 2) {
            updateState { it.copy(studentSearchResults = emptyList()) }
            return
        }
        
        viewModelScope.launch {
            try {
                updateState { it.copy(isSearchingStudents = true) }
                
                val results = studentRepository.searchStudents(query)
                
                updateState { 
                    it.copy(
                        studentSearchResults = results,
                        isSearchingStudents = false
                    ) 
                }
                
                Log.d(TAG, "Found ${results.size} students for query: $query")
            } catch (e: Exception) {
                Log.e(TAG, "Error searching students", e)
                updateState { 
                    it.copy(
                        isSearchingStudents = false,
                        errorMessage = "Search failed: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun manualCheckIn(student: Student) {
        // Simulate scanning the student's ID
        // This uses the same flow as a regular barcode scan
        Log.d(TAG, "Manual check-in for student: ${student.fullName} (${student.studentId})")
        
        // Hide the forgot ID dialog
        hideForgotIdDialog()
        
        // Process as if they scanned their ID
        handleScanResult(student.studentId, "MANUAL_CHECKIN")
    }
    
    fun hideStudentDialog() {
        updateState { 
            it.copy(
                showStudentDialog = false,
                verifiedStudent = null,
                scannedStudentId = ""
            ) 
        }
    }

    // Event Management Functions
    private fun loadEvents() {
        viewModelScope.launch {
            try {
                eventRepository.getAllEvents().collect { events ->
                    // Try to restore previously selected event
                    val savedEventId = sharedPrefs.getString(KEY_CURRENT_EVENT_ID, null)
                    val currentEvent = events.find { it.id == savedEventId }
                        ?: events.find { it.isActive } 
                        ?: events.firstOrNull()
                    
                    updateState { 
                        it.copy(
                            availableEvents = events,
                            currentEvent = currentEvent
                        ) 
                    }
                    
                    // Update current list ID to match current event
                    currentEvent?.let { event ->
                        updateState { it.copy(currentListId = event.id) }
                        // Save the current event ID
                        sharedPrefs.edit().putString(KEY_CURRENT_EVENT_ID, event.id).apply()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading events", e)
            }
        }
    }
    
    fun showEventSelector() {
        updateState { it.copy(showEventSelector = true) }
    }
    
    fun hideEventSelector() {
        updateState { it.copy(showEventSelector = false) }
    }
    
    fun selectEvent(event: Event) {
        // Save selected event to preferences
        sharedPrefs.edit().putString(KEY_CURRENT_EVENT_ID, event.id).apply()
        
        updateState { 
            it.copy(
                currentEvent = event,
                currentListId = event.id,
                showEventSelector = false
            ) 
        }
        loadScans()
        observeFirestoreChanges()
    }
    
    fun showNewEventDialog() {
        updateState { it.copy(showNewEventDialog = true) }
    }
    
    fun hideNewEventDialog() {
        updateState { it.copy(showNewEventDialog = false) }
    }
    
    fun createNewEvent(eventNumber: Int, name: String, description: String = "") {
        viewModelScope.launch {
            try {
                val newEvent = Event.createNew(
                    eventNumber = eventNumber,
                    name = name,
                    description = description
                )
                
                eventRepository.createEvent(newEvent)
                
                // Select the new event
                selectEvent(newEvent)
                hideNewEventDialog()
                
                Log.d(TAG, "Created new event: $name (ID: $eventNumber)")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating event", e)
                updateState { it.copy(errorMessage = "Failed to create event: ${e.message}") }
            }
        }
    }

    /**
     * Get attendees for an event (one-time fetch)
     */
    suspend fun getEventAttendeesOnce(eventId: String): List<EventAttendee> {
        return try {
            eventRepository.getEventAttendees(eventId).first()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting event attendees", e)
            emptyList()
        }
    }

    private fun updateState(update: (ScannerUiState) -> ScannerUiState) {
        _uiState.value = update(_uiState.value)
    }

    override fun onCleared() {
        super.onCleared()
        honeywellScanner.release()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScannerViewModel(context) as T
        }
    }
}