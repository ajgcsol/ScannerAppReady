package com.yourorg.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import android.util.Log
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.yourorg.scanner.model.ScanRecord
import com.yourorg.scanner.ui.theme.ScannerAppTheme
import com.yourorg.scanner.ui.StudentVerificationDialog
import com.yourorg.scanner.ui.DuplicateScanDialog
import com.yourorg.scanner.ui.ForgotIdDialog
import com.yourorg.scanner.ui.EventSelectorDialog
import com.yourorg.scanner.ui.NewEventDialog
import com.yourorg.scanner.ui.CameraPreviewScreen
import com.yourorg.scanner.model.Event
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels { 
        ScannerViewModel.Factory(this) 
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ScannerAppTheme {
                ScannerApp(
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerApp(
    viewModel: ScannerViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isLoading by remember { mutableStateOf(true) }
    
    // Show loading animation for 2 seconds on initial load
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        isLoading = false
    }
    
    if (isLoading) {
        LoadingSplashScreen()
    } else {
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FactCheck,
                            contentDescription = "App Logo",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "EventCheck Pro", 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "Professional Event Management",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
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
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Event Selection Header (above tabs)
            EventHeaderCard(
                currentEvent = uiState.currentEvent,
                onSelectEvent = { viewModel.showEventSelector() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Event Home") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Recent Scans") }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tab Content
            when (selectedTabIndex) {
                0 -> {
                    // Event Home Tab Content - Everything that was between Event Card and Recent Scans
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
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
                        
                        // Complete Event Button - only show if event is active
                        uiState.currentEvent?.let { event ->
                            if (event.isActive && !event.isCompleted) {
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Button(
                                    onClick = { viewModel.completeCurrentEvent() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Complete Event Scanning")
                                }
                                
                                Text(
                                    "This will mark the event as complete and notify administrators",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Recent Scans Tab Content - ONLY the recent scans list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.scans.take(15)) { scan ->
                            ScanItem(scan = scan)
                        }
                    }
                }
            }
        }
    }
        
        // Duplicate Scan Dialog
        if (uiState.showDuplicateDialog) {
            DuplicateScanDialog(
                student = uiState.duplicateStudent,
                studentId = uiState.scannedStudentId,
                onDismiss = { viewModel.dismissDuplicateDialog() }
            )
        }
        
        // Student Verification Dialog
    if (uiState.showStudentDialog) {
        StudentVerificationDialog(
            student = uiState.verifiedStudent,
            scannedId = uiState.scannedStudentId,
            onDismiss = { viewModel.hideStudentDialog() },
            onSubmitErrorRecord = { scannedId, email ->
                viewModel.submitErrorRecord(scannedId, email)
            }
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
    
    // Camera Preview Screen - shown as full-screen overlay
    if (uiState.showCameraPreview) {
        CameraPreviewScreen(
            onScanResult = { scanResult ->
                viewModel.onCameraScanResult(scanResult)
            },
            onBackPressed = { viewModel.hideCameraPreview() },
            modifier = Modifier.fillMaxSize()
        )
    }
    }
    }
}

@Composable
fun LoadingSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated scale logo
            var scale by remember { mutableFloatStateOf(0.5f) }
            
            LaunchedEffect(Unit) {
                animate(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { value, _ ->
                    scale = value
                }
            }
            
            // Law/Justice scales icon with scanning effect
            Box(
                modifier = Modifier.scale(scale),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = "Logo",
                    modifier = Modifier.size(120.dp),
                    tint = Color.White
                )
                
                // Scanning line animation
                var offsetY by remember { mutableFloatStateOf(-60f) }
                LaunchedEffect(Unit) {
                    while(true) {
                        animate(
                            initialValue = -60f,
                            targetValue = 60f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        ) { value, _ ->
                            offsetY = value
                        }
                    }
                }
                
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(2.dp)
                        .offset(y = offsetY.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "EventCheck Pro",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Professional Event Management",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Initializing Scanner...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
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
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
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
fun EventItem(
    event: Event,
    isSelected: Boolean,
    onEventSelected: () -> Unit,
    onStartEvent: (Event) -> Unit = {},
    onCompleteEvent: (Event) -> Unit = {},
    onReopenEvent: (Event) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onEventSelected() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isSelected) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.Event,
                contentDescription = if (isSelected) "Selected" else "Event",
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Event #${event.eventNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = when (event.status) {
                            com.yourorg.scanner.model.EventStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                            com.yourorg.scanner.model.EventStatus.INACTIVE -> MaterialTheme.colorScheme.outline
                            com.yourorg.scanner.model.EventStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            when (event.status) {
                                com.yourorg.scanner.model.EventStatus.ACTIVE -> "ACTIVE"
                                com.yourorg.scanner.model.EventStatus.INACTIVE -> "INACTIVE"
                                com.yourorg.scanner.model.EventStatus.COMPLETED -> "COMPLETED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    event.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                if (event.description.isNotEmpty()) {
                    Text(
                        event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    when (event.status) {
                        com.yourorg.scanner.model.EventStatus.INACTIVE -> {
                            OutlinedButton(
                                onClick = { onStartEvent(event) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    "Start",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        com.yourorg.scanner.model.EventStatus.ACTIVE -> {
                            OutlinedButton(
                                onClick = { onCompleteEvent(event) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    "Complete",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        com.yourorg.scanner.model.EventStatus.COMPLETED -> {
                            OutlinedButton(
                                onClick = { onReopenEvent(event) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    "Reopen",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.RadioButtonChecked,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    if (currentEvent.description.isNotEmpty()) {
                        Text(
                            text = currentEvent.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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