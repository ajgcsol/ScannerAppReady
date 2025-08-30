package com.yourorg.scanner.model

/**
 * ScanRecord represents a single barcode/QR code scan
 * Compatible with Firebase Firestore and Room database
 */
data class ScanRecord(
    val id: String = "",
    val code: String = "",
    val symbology: String = "QR_CODE",
    val timestamp: Long = 0L,
    val deviceId: String = "",
    val userId: String = "scanner-user",
    val listId: String = "default-list",
    // Extended fields for offline support
    val synced: Boolean = false,
    val verified: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val program: String = "",
    val year: String = "",
    val eventId: String? = null
) {
    // No-argument constructor for Firebase
    constructor() : this("", "", "QR_CODE", 0L, "", "scanner-user", "default-list")
    
    /**
     * Convert to display-friendly format
     */
    fun getDisplayTime(): String {
        return java.text.SimpleDateFormat("MMM dd, HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
    
    /**
     * Get readable symbology name
     */
    fun getSymbologyDisplay(): String {
        return when (symbology?.uppercase()) {
            "CODE128", "CODE_128" -> "Code 128"
            "CODE39", "CODE_39" -> "Code 39"
            "EAN13", "EAN_13" -> "EAN-13"
            "EAN8", "EAN_8" -> "EAN-8"
            "UPC_A", "UPCA" -> "UPC-A"
            "QR_CODE", "QRCODE" -> "QR Code"
            "DATA_MATRIX", "DATAMATRIX" -> "Data Matrix"
            "PDF417" -> "PDF417"
            "AZTEC" -> "Aztec"
            else -> symbology ?: "Unknown"
        }
    }
}
