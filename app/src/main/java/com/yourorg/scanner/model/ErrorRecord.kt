package com.yourorg.scanner.model

import com.google.firebase.Timestamp

/**
 * Error record for unrecognized student IDs
 */
data class ErrorRecord(
    val id: String = "",
    val scannedId: String = "",
    val studentEmail: String = "",
    val eventId: String = "",
    val eventName: String = "",
    val eventDate: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val resolved: Boolean = false
)