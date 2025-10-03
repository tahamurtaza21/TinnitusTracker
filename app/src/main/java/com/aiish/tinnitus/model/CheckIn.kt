package com.aiish.tinnitus.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkins")
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val date: String, // "YYYY-MM-DD"
    val relaxationDone: String,
    val relaxationDuration: String,
    val soundTherapyDone: String,
    val soundTherapyDuration: String,
    val tinnitusLevel: Int?,
    val anxietyLevel: Int?
)
