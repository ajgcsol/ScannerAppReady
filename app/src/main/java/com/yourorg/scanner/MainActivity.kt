package com.yourorg.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourorg.scanner.export.ExportManager
import com.yourorg.scanner.export.exportAndShareCsv
import com.yourorg.scanner.export.exportAndShareXlsx
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.yourorg.scanner.model.ScanRecord
import com.yourorg.scanner.ui.theme.ScannerAppTheme
import com.yourorg.scanner.ui.StudentVerificationDialog
import com.yourorg.scanner.ui.ForgotIdDialog
import com.yourorg.scanner.ui.EventSelectorDialog
import com.yourorg.scanner.ui.NewEventDialog
import com.yourorg.scanner.ui.CameraPreviewScreen
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels { 
        ScannerViewModel.Factory(this) 
    }
    
    private lateinit var exportManager: ExportManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        exportManager = ExportManager(this)
        
        setContent {
            ScannerAppTheme {
                ScannerApp(
                    viewModel = viewModel,
                    exportManager = exportManager
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerApp(
    viewModel: ScannerViewModel,
    exportManager: ExportManager
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Scanner Pro", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Export",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.triggerScan() },
                icon = {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.QrCodeScanner, 
                            contentDescription = "Scan"
                        )
                    }
                },
                text = { 
                    Text(if (uiState.isScanning) "Scanning..." else "Scan") 
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Event Selection Header
            EventHeaderCard(
                currentEvent = uiState.currentEvent,
                onSelectEvent = { viewModel.showEventSelector() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusCard(
                    title = "Status",
                    value = if (uiState.isConnected) "Connected" else "Disconnected",
                    icon = if (uiState.isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                    color = if (uiState.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                
                StatusCard(
                    title = "Count",
                    value = uiState.scanCount.toString(),
                    icon = Icons.Default.Numbers,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Last Scan Card
            uiState.lastScan?.let { lastScan ->
                LastScanCard(scan = lastScan)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Error Message
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Forgot My ID Button
            OutlinedButton(
                onClick = { viewModel.showForgotIdDialog() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PersonSearch, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Forgot My ID? Search by Name")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Scan History
            Text(
                "Recent Scans",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn {
                items(uiState.scans.take(10)) { scan ->
                    ScanItem(scan = scan)
                }
            }
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExportCsv = {
                scope.launch {
                    exportManager.exportAndShareCsv(uiState.scans, uiState.currentListId)
                }
                showExportDialog = false
            },
            onExportXlsx = {
                scope.launch {
                    exportManager.exportAndShareXlsx(uiState.scans, uiState.currentListId)
                }
                showExportDialog = false
            },
            onEmail = {
                scope.launch {
                    uiState.currentEvent?.let { event ->
                        // Get attendees for current event and export as text delimited
                        val attendees = viewModel.getEventAttendeesOnce(event.id)
                        exportManager.emailEventReport(event, attendees)
                    } ?: run {
                        // Fallback to regular export if no event selected
                        exportManager.emailReport(uiState.scans, uiState.currentListId)
                    }
                }
                showExportDialog = false
            }
        )
    }
    
    // Student Verification Dialog
    if (uiState.showStudentDialog) {
        StudentVerificationDialog(
            student = uiState.verifiedStudent,
            scannedId = uiState.scannedStudentId,
            onDismiss = { viewModel.hideStudentDialog() }
        )
    }
    
    // Forgot ID Dialog
    if (uiState.showForgotIdDialog) {
        ForgotIdDialog(
            onDismiss = { viewModel.hideForgotIdDialog() },
            onSearchStudents = { query -> viewModel.searchStudents(query) },
            searchResults = uiState.studentSearchResults,
            isSearching = uiState.isSearchingStudents,
            onStudentSelected = { student -> viewModel.manualCheckIn(student) }
        )
    }
    
    // Event Selector Dialog
    if (uiState.showEventSelector) {
        EventSelectorDialog(
            events = uiState.availableEvents,
            currentEvent = uiState.currentEvent,
            onEventSelected = { event -> viewModel.selectEvent(event) },
            onCreateNewEvent = { viewModel.showNewEventDialog() },
            onDismiss = { viewModel.hideEventSelector() }
        )
    }
    
    // New Event Dialog
    if (uiState.showNewEventDialog) {
        NewEventDialog(
            onCreateEvent = { eventNumber, name, description -> 
                viewModel.createNewEvent(eventNumber, name, description)
            },
            onDismiss = { viewModel.hideNewEventDialog() }
        )
    }
    
    // Camera Preview Screen
    if (uiState.showCameraPreview) {
        CameraPreviewScreen(
            onScanResult = { scanResult ->
                viewModel.onCameraScanResult(scanResult)
            },
            onBackPressed = { viewModel.hideCameraPreview() }
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun LastScanCard(scan: ScanRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.QrCode,
                    contentDescription = "Last Scan",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Last Scan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                scan.code,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    scan.symbology ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(scan.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ScanItem(scan: ScanRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.QrCode2,
                contentDescription = "Scan",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scan.code,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    Text(
                        scan.symbology ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(scan.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onExportXlsx: () -> Unit,
    onEmail: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Scan Data") },
        text = { Text("Choose export format:") },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Column {
                TextButton(
                    onClick = onExportCsv,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export to CSV")
                }
                TextButton(
                    onClick = onExportXlsx,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export to Excel")
                }
                TextButton(
                    onClick = onEmail,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Email Event Report (Text Format)")
                }
            }
        }
    )
}

@Composable
fun EventHeaderCard(
    currentEvent: com.yourorg.scanner.model.Event?,
    onSelectEvent: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        onClick = onSelectEvent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = "Event",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Current Event",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                if (currentEvent != null) {
                    Text(
                        text = "Event #${currentEvent.eventNumber}: ${currentEvent.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (currentEvent.description.isNotEmpty()) {
                        Text(
                            text = currentEvent.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "No Event Selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Tap to select or create an event",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Select Event",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}