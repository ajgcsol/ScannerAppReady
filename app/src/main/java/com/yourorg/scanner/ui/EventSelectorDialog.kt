package com.yourorg.scanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourorg.scanner.model.Event
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSelectorDialog(
    events: List<Event>,
    currentEvent: Event?,
    onEventSelected: (Event) -> Unit,
    onCreateNewEvent: () -> Unit,
    onStartEvent: (Event) -> Unit = {},
    onCompleteEvent: (Event) -> Unit = {},
    onReopenEvent: (Event) -> Unit = {},
    onDismiss: () -> Unit
) {
    var showCompletedEvents by remember { mutableStateOf(false) }
    
    // Filter events based on toggle
    val filteredEvents = if (showCompletedEvents) {
        events.filter { it.isCompleted }
    } else {
        events.filter { !it.isCompleted }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Event",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    TextButton(
                        onClick = onCreateNewEvent
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Event",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Event")
                    }
                }
                
                // Toggle Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showCompletedEvents) "Completed Events" else "Active Events",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Completed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = showCompletedEvents,
                            onCheckedChange = { showCompletedEvents = it }
                        )
                    }
                }
                
                // Events List
                if (filteredEvents.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (showCompletedEvents) "No Completed Events" else "No Active Events",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (showCompletedEvents) 
                                    "Complete an event to see it here" 
                                else 
                                    "Create your first event to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredEvents) { event ->
                            EventCard(
                                event = event,
                                isSelected = currentEvent?.id == event.id,
                                onSelected = { onEventSelected(event) },
                                onStartEvent = onStartEvent,
                                onCompleteEvent = onCompleteEvent,
                                onReopenEvent = onReopenEvent
                            )
                        }
                    }
                }
                
                // Cancel Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: Event,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onStartEvent: (Event) -> Unit = {},
    onCompleteEvent: (Event) -> Unit = {},
    onReopenEvent: (Event) -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onSelected,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp, 
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Event #${event.eventNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (event.status) {
                                com.yourorg.scanner.model.EventStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                com.yourorg.scanner.model.EventStatus.INACTIVE -> MaterialTheme.colorScheme.outline
                                com.yourorg.scanner.model.EventStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                    ) {
                        Text(
                            text = when (event.status) {
                                com.yourorg.scanner.model.EventStatus.ACTIVE -> "ACTIVE"
                                com.yourorg.scanner.model.EventStatus.INACTIVE -> "INACTIVE"
                                com.yourorg.scanner.model.EventStatus.COMPLETED -> "COMPLETED"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                if (event.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Created: ${dateFormat.format(Date(event.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}