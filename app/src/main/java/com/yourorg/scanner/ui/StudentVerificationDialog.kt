package com.yourorg.scanner.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourorg.scanner.model.Student
import kotlinx.coroutines.delay

@Composable
fun StudentVerificationDialog(
    student: Student?,
    scannedId: String,
    onDismiss: () -> Unit,
    onSubmitErrorRecord: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(true) }
    var isInteracting by remember { mutableStateOf(false) }
    
    // Auto-dismiss after 4 seconds for successful verification, 6 seconds for failure
    // But only if user is not interacting with the dialog
    LaunchedEffect(student) {
        if (student != null) {
            // Success dialog - auto dismiss after 4 seconds
            delay(4000)
            if (!isInteracting) {
                showDialog = false
                onDismiss()
            }
        } else {
            // Failure dialog - auto dismiss after 6 seconds if not interacting
            delay(6000)
            if (!isInteracting) {
                showDialog = false
                onDismiss()
            }
        }
    }
    
    if (showDialog) {
        Dialog(
            onDismissRequest = { 
                showDialog = false
                onDismiss() 
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            val configuration = LocalConfiguration.current
            val isTablet = configuration.screenWidthDp > 600
            
            AnimatedVisibility(
                visible = showDialog,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            ) {
                if (student != null) {
                    SuccessDialog(
                        student = student,
                        isTablet = isTablet,
                        onDismiss = { 
                            showDialog = false
                            onDismiss() 
                        }
                    )
                } else {
                    FailureDialog(
                        scannedId = scannedId,
                        isTablet = isTablet,
                        onDismiss = { 
                            showDialog = false
                            onDismiss() 
                        },
                        onSubmitErrorRecord = onSubmitErrorRecord,
                        onInteractionChanged = { interacting ->
                            isInteracting = interacting
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessDialog(
    student: Student,
    isTablet: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(if (isTablet) 0.6f else 0.9f)
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            // Success Animation
            SuccessCheckAnimation()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Success Message
            Text(
                text = "You're all set, ${student.firstName}!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32), // Success green
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Please ensure the information below is relevant to your student record",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Student Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudentInfoRow(
                        icon = Icons.Default.Person,
                        label = "Name",
                        value = "${student.firstName} ${student.lastName}"
                    )
                    
                    StudentInfoRow(
                        icon = Icons.Default.Badge,
                        label = "Student ID",
                        value = student.studentId
                    )
                    
                    if (student.email.isNotEmpty()) {
                        StudentInfoRow(
                            icon = Icons.Default.Email,
                            label = "Email",
                            value = student.email
                        )
                    }
                    
                    if (student.program.isNotEmpty()) {
                        StudentInfoRow(
                            icon = Icons.Default.School,
                            label = "Program",
                            value = student.program
                        )
                    }
                    
                    if (student.year.isNotEmpty()) {
                        StudentInfoRow(
                            icon = Icons.Default.DateRange,
                            label = "Year",
                            value = student.year
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Close Button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FailureDialog(
    scannedId: String,
    isTablet: Boolean,
    onDismiss: () -> Unit,
    onSubmitErrorRecord: ((String, String) -> Unit)? = null,
    onInteractionChanged: (Boolean) -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var isEmailValid by remember { mutableStateOf(false) }
    var showEmailInput by remember { mutableStateOf(false) }
    
    // Notify parent when email input is shown/hidden
    LaunchedEffect(showEmailInput) {
        onInteractionChanged(showEmailInput)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth(if (isTablet) 0.6f else 0.9f)
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            // Error Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFFD32F2F)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Error Message
            Text(
                text = "Student Not Found",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "The scanned ID is not registered in our system",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Scanned ID Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scanned ID:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = scannedId,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Email Input Section
            if (showEmailInput) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Please enter your email to report this issue:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches()
                            onInteractionChanged(true) // User is typing
                        },
                        label = { Text("Email Address") },
                        placeholder = { Text("student@example.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = email.isNotEmpty() && !isEmailValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (email.isNotEmpty() && !isEmailValid) {
                        Text(
                            text = "Please enter a valid email address",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Submit and Cancel Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { 
                            showEmailInput = false
                            email = ""
                            onInteractionChanged(false)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Button(
                        onClick = {
                            if (isEmailValid) {
                                onSubmitErrorRecord?.invoke(scannedId, email)
                                onInteractionChanged(false)
                                onDismiss()
                            }
                        },
                        enabled = isEmailValid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Submit",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else {
                Text(
                    text = "Please contact the administrator or verify the correct student ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Report Issue and Close Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onSubmitErrorRecord != null) {
                        OutlinedButton(
                            onClick = { showEmailInput = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Report Issue",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = if (onSubmitErrorRecord != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)
                        )
                    ) {
                        Text(
                            "Close",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SuccessCheckAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(RoundedCornerShape(40.dp))
            .background(Color(0xFFE8F5E8)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = "Success",
            modifier = Modifier.size(48.dp),
            tint = Color(0xFF2E7D32)
        )
    }
}