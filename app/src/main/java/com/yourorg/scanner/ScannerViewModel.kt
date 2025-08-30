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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.*

data class ScannerUiState(
    val scans: List<ScanRecord> = emptyList(),
    val currentListId: String = "default-list",
    val lastScan: ScanRecord? = null,
    val scanCount: Int = 0,
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    // Offline sync status
    val unsyncedScanCount: Int = 0,
    val lastSyncTime: Long? = null,
    val isSyncing: Boolean = false,
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
    val showNewEventDialog: Boolean = false,
    // Duplicate scan detection
    val showDuplicateDialog: Boolean = false,
    val duplicateStudent: Student? = null,
    // Event statistics
    val uniqueStudentCount: Int = 0,
    val duplicateScanCount: Int = 0,
    val errorCount: Int = 0,
    val forgotIdCount: Int = 0,
    // No event selected dialog
    val showNoEventDialog: Boolean = false
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
    ).fallbackToDestructiveMigration()
    .build()
    
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scanDao = database.scanDao()
    private val firestoreSync = FirestoreSync()
    private val honeywellScanner = HoneywellScanner(context)
    
    // Offline support components
    private val connectivityMonitor = com.yourorg.scanner.data.sync.ConnectivityMonitor(context)
    private val syncManager = com.yourorg.scanner.data.sync.OfflineSyncManager(context, database, connectivityMonitor)
    private val studentRepository = StudentRepository(context, database, connectivityMonitor, syncManager)
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
        observeConnectivity()
        updateSyncStatus()
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
                    entity.toScanRecord()
                }
                
                // Calculate statistics
                val statistics = calculateEventStatistics(scanRecords)
                
                updateState { 
                    it.copy(
                        scans = scanRecords,
                        lastScan = scanRecords.firstOrNull(),
                        scanCount = scanRecords.size,
                        uniqueStudentCount = statistics.uniqueStudents,
                        duplicateScanCount = statistics.duplicates,
                        errorCount = statistics.errors,
                        forgotIdCount = statistics.forgotIds
                    ) 
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading scans", e)
                updateState { it.copy(errorMessage = "Failed to load scans: ${e.message}") }
            }
        }
    }
    
    /**
     * Calculate event statistics from scan records
     */
    private fun calculateEventStatistics(scans: List<ScanRecord>): EventStatistics {
        val uniqueStudents = scans.filter { it.verified }.distinctBy { it.code }.size
        val duplicates = scans.size - scans.distinctBy { it.code }.size
        val errors = scans.count { !it.verified && it.email.isEmpty() }
        val forgotIds = scans.count { it.symbology == "MANUAL_CHECKIN" }
        
        return EventStatistics(
            uniqueStudents = uniqueStudents,
            duplicates = duplicates,
            errors = errors,
            forgotIds = forgotIds
        )
    }
    
    data class EventStatistics(
        val uniqueStudents: Int,
        val duplicates: Int,
        val errors: Int,
        val forgotIds: Int
    )

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
                
                // Check for duplicate scan in current event
                val currentEventId = _uiState.value.currentEvent?.id
                if (currentEventId != null) {
                    val isDuplicate = checkForDuplicateScan(code, currentEventId)
                    if (isDuplicate) {
                        // Show duplicate dialog instead of recording
                        updateState { 
                            it.copy(
                                showDuplicateDialog = true,
                                duplicateStudent = student,
                                scannedStudentId = code,
                                isScanning = false
                            )
                        }
                        return@launch
                    }
                }
                
                // Record the scan with verification status (this handles both local storage and background sync)
                studentRepository.recordScan(
                    studentId = code,
                    student = student,
                    deviceId = _uiState.value.deviceInfo,
                    eventId = currentEventId,
                    listId = _uiState.value.currentListId
                )
                
                // Update analytics in background (non-blocking)
                CoroutineScope(Dispatchers.IO).launch {
                    studentRepository.updateScanAnalytics(student != null)
                }

                // Quickly update UI with new scan instead of full refresh
                val newScan = ScanRecord(
                    id = UUID.randomUUID().toString(),
                    code = code,
                    symbology = symbology ?: "QR_CODE",
                    timestamp = System.currentTimeMillis(),
                    deviceId = _uiState.value.deviceInfo,
                    userId = _uiState.value.userName,
                    listId = _uiState.value.currentListId,
                    verified = student != null,
                    firstName = student?.firstName ?: "",
                    lastName = student?.lastName ?: "",
                    email = student?.email ?: "",
                    program = student?.program ?: "",
                    year = student?.year ?: ""
                )
                
                updateState { state ->
                    state.copy(
                        scans = listOf(newScan) + state.scans,
                        lastScan = newScan,
                        scanCount = state.scanCount + 1
                    )
                }
                
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
    
    /**
     * Check if a student has already been scanned for the current event
     */
    private suspend fun checkForDuplicateScan(studentId: String, eventId: String): Boolean {
        return try {
            val existingScans = scanDao.getScansForStudentInEvent(studentId, eventId)
            existingScans.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for duplicate scan", e)
            false
        }
    }
    
    /**
     * Dismiss duplicate scan dialog
     */
    fun dismissDuplicateDialog() {
        updateState { 
            it.copy(
                showDuplicateDialog = false,
                duplicateStudent = null,
                scannedStudentId = ""
            ) 
        }
    }

    fun triggerScan() {
        if (_uiState.value.currentEvent == null) {
            updateState { it.copy(showNoEventDialog = true) }
            return
        }
        
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
        if (_uiState.value.currentEvent == null) {
            updateState { it.copy(showNoEventDialog = true) }
            return
        }
        
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
    
    fun dismissNoEventDialog() {
        updateState { it.copy(showNoEventDialog = false) }
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
    
    fun submitErrorRecord(scannedId: String, email: String) {
        viewModelScope.launch {
            try {
                val currentEvent = _uiState.value.currentEvent
                if (currentEvent != null) {
                    // Save error record to Firebase
                    firestoreSync.saveErrorRecord(
                        scannedId = scannedId,
                        email = email,
                        eventId = currentEvent.id,
                        eventName = currentEvent.name,
                        eventDate = currentEvent.formattedDate
                    )
                    Log.d(TAG, "Error record submitted for $scannedId with email $email")
                }
                hideStudentDialog()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit error record", e)
                updateState { it.copy(errorMessage = "Failed to submit error record") }
            }
        }
    }

    // Event Management Functions
    private fun loadEvents() {
        viewModelScope.launch {
            try {
                eventRepository.getAllEvents().collect { allEvents ->
                    // Show all events (both active and completed) in the available list
                    // But prioritize active events for current selection
                    val activeEvents = allEvents.filter { !it.isCompleted }
                    
                    // Try to restore previously selected event
                    val savedEventId = sharedPrefs.getString(KEY_CURRENT_EVENT_ID, null)
                    val currentEvent = allEvents.find { it.id == savedEventId }
                        ?: activeEvents.find { it.isActive } 
                        ?: activeEvents.firstOrNull()
                    
                    updateState { 
                        it.copy(
                            availableEvents = allEvents, // Show all events in selector
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
            throw e
        }
    }
    
    /**
     * Start/activate an event
     */
    fun startEvent(event: Event) {
        viewModelScope.launch {
            try {
                val updatedEvent = event.copy(isActive = true, isCompleted = false)
                eventRepository.updateEvent(updatedEvent)
                loadEvents()
                Log.d(TAG, "Event started: ${event.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start event", e)
                updateState { it.copy(errorMessage = "Failed to start event: ${e.message}") }
            }
        }
    }
    
    /**
     * Complete an event
     */
    fun completeEvent(event: Event) {
        viewModelScope.launch {
            try {
                val updatedEvent = event.copy(
                    isActive = false, 
                    isCompleted = true,
                    completedAt = System.currentTimeMillis()
                )
                eventRepository.updateEvent(updatedEvent)
                loadEvents()
                Log.d(TAG, "Event completed: ${event.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete event", e)
                updateState { it.copy(errorMessage = "Failed to complete event: ${e.message}") }
            }
        }
    }
    
    /**
     * Reopen a completed event
     */
    fun reopenEvent(event: Event) {
        viewModelScope.launch {
            try {
                val updatedEvent = event.copy(
                    isActive = true, 
                    isCompleted = false,
                    completedAt = null
                )
                eventRepository.updateEvent(updatedEvent)
                loadEvents()
                Log.d(TAG, "Event reopened: ${event.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reopen event", e)
                updateState { it.copy(errorMessage = "Failed to reopen event: ${e.message}") }
            }
        }
    }
    
    /**
     * Complete the current event and notify admin portal
     */
    fun completeCurrentEvent() {
        viewModelScope.launch {
            try {
                val currentEvent = _uiState.value.currentEvent
                if (currentEvent != null) {
                    // Mark event as completed
                    val updatedEvent = currentEvent.copy(
                        isActive = false,
                        isCompleted = true,
                        completedAt = System.currentTimeMillis()
                    )
                    
                    // Update event in repository
                    eventRepository.updateEvent(updatedEvent)
                    
                    // Send notification to admin portal via Firestore
                    firestoreSync.notifyEventCompleted(
                        eventId = currentEvent.id,
                        eventName = currentEvent.name,
                        eventNumber = currentEvent.eventNumber,
                        completedAt = updatedEvent.completedAt ?: System.currentTimeMillis(),
                        totalScans = _uiState.value.scanCount
                    )
                    
                    // Clear current event and reload
                    sharedPrefs.edit().remove(KEY_CURRENT_EVENT_ID).apply()
                    loadEvents()
                    
                    updateState { 
                        it.copy(
                            errorMessage = null,
                            currentEvent = null
                        ) 
                    }
                    
                    Log.d(TAG, "Event completed and admin notified: ${currentEvent.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete event", e)
                updateState { it.copy(errorMessage = "Failed to complete event: ${e.message}") }
            }
        }
    }

    private fun updateState(update: (ScannerUiState) -> ScannerUiState) {
        _uiState.value = update(_uiState.value)
    }
    
    /**
     * Observe network connectivity changes
     */
    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityMonitor.isConnected.collect { isConnected ->
                updateState { 
                    it.copy(isConnected = isConnected)
                }
                if (isConnected) {
                    updateSyncStatus()
                }
            }
        }
    }
    
    /**
     * Update sync status information
     */
    private fun updateSyncStatus() {
        viewModelScope.launch {
            try {
                val syncStatus = syncManager.getSyncStatus()
                updateState { 
                    it.copy(
                        unsyncedScanCount = syncStatus.unsyncedScanCount,
                        lastSyncTime = syncStatus.lastSyncTime,
                        isConnected = syncStatus.isConnected
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating sync status", e)
            }
        }
    }
    
    /**
     * Get local scans for export (includes all scans, not just UI list)
     */
    suspend fun getLocalScansForExport(): List<ScanRecord> {
        return try {
            val allLocalScans = scanDao.getAllScans()
            allLocalScans.map { it.toScanRecord() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local scans for export", e)
            _uiState.value.scans // Fallback to UI state scans
        }
    }
    
    /**
     * Get local scans for a specific event
     */
    suspend fun getLocalScansForEvent(eventId: String): List<ScanRecord> {
        return try {
            val eventScans = scanDao.getScansForEvent(eventId)
            eventScans.map { it.toScanRecord() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting event scans for export", e)
            emptyList()
        }
    }
    
    /**
     * Trigger manual sync
     */
    fun triggerSync() {
        viewModelScope.launch {
            if (!connectivityMonitor.isCurrentlyConnected()) {
                updateState { 
                    it.copy(errorMessage = "No internet connection available for sync")
                }
                return@launch
            }
            
            try {
                updateState { it.copy(isSyncing = true) }
                syncManager.startSync()
                
                // Wait a moment then update status
                kotlinx.coroutines.delay(2000)
                updateSyncStatus()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering sync", e)
                updateState { 
                    it.copy(errorMessage = "Sync failed: ${e.message}")
                }
            } finally {
                updateState { it.copy(isSyncing = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        honeywellScanner.release()
        connectivityMonitor.stopMonitoring()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScannerViewModel(context) as T
        }
    }
}