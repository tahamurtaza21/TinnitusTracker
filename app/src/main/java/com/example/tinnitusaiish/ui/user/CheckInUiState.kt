package com.example.tinnitusaiish.ui.user

data class CheckInUiState(
    var relaxationDone: String = "",
    var relaxationDuration: String = "",
    var soundTherapyDone: String = "",
    var soundTherapyDuration: String = "",
    var tinnitusLevel: Int = 5,
    var anxietyLevel: Int = 5,
    var docId: String? = null,
    var isUpdating: Boolean = false
)
