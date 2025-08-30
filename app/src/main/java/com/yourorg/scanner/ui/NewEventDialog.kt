package com.yourorg.scanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEventDialog(
    onCreateEvent: (eventNumber: Int, name: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var eventNumber by remember { mutableStateOf("") }
    var eventName by remember { mutableStateOf("") }
    var eventDescription by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf("") }
    
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
                Text(
                    text = "Create New Event",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                
                // Event Number Field
                OutlinedTextField(
                    value = eventNumber,
                    onValueChange = { 
                        eventNumber = it
                        showError = ""
                    },
                    label = { Text("Event Number") },
                    placeholder = { Text("e.g., 889") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true
                )
                
                // Event Name Field
                OutlinedTextField(
                    value = eventName,
                    onValueChange = { 
                        eventName = it
                        showError = ""
                    },
                    label = { Text("Event Name") },
                    placeholder = { Text("e.g., Spring Career Fair 2024") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true
                )
                
                // Event Description Field (Optional)
                OutlinedTextField(
                    value = eventDescription,
                    onValueChange = { eventDescription = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Brief description of the event...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    minLines = 2,
                    maxLines = 3
                )
                
                // Error Message
                if (showError.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = showError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            // Validation
                            when {
                                eventNumber.isBlank() -> {
                                    showError = "Please enter an event number"
                                }
                                eventNumber.toIntOrNull() == null -> {
                                    showError = "Event number must be a valid number"
                                }
                                eventNumber.toInt() <= 0 -> {
                                    showError = "Event number must be greater than 0"
                                }
                                eventName.isBlank() -> {
                                    showError = "Please enter an event name"
                                }
                                eventName.length < 3 -> {
                                    showError = "Event name must be at least 3 characters"
                                }
                                else -> {
                                    // Valid input - create event
                                    onCreateEvent(
                                        eventNumber.toInt(),
                                        eventName.trim(),
                                        eventDescription.trim()
                                    )
                                }
                            }
                        }
                    ) {
                        Text("Create Event")
                    }
                }
            }
        }
    }
}