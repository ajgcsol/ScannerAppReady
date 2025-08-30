package com.yourorg.scanner.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Student(
    val studentId: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val program: String = "",
    val year: String = "",
    val active: Boolean = true
) : Parcelable {
    
    val fullName: String
        get() = "$firstName $lastName"
    
    val displayInfo: String
        get() = buildString {
            append("$firstName $lastName")
            if (program.isNotEmpty()) {
                append(" • $program")
            }
            if (year.isNotEmpty()) {
                append(" • Year $year")
            }
        }
    
    companion object {
        fun empty() = Student(
            studentId = "",
            firstName = "",
            lastName = "",
            email = "",
            program = "",
            year = "",
            active = false
        )
    }
}