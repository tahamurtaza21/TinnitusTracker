package com.aiish.tinnitus.ui.user

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aiish.tinnitus.data.CheckInRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CheckInScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val email = prefs.getString("logged_in_email", "") ?: ""
    val repo = CheckInRepository()

    var uiState by remember { mutableStateOf(CheckInUiState()) }
    var showRecommendationPopup by remember { mutableStateOf(false) }
    var popupMessage by remember { mutableStateOf("") }

    // Load today's check-in
    LaunchedEffect(email) {
        if (email.isNotEmpty()) {
            val (docId, data) = repo.getTodayCheckIn(email)
            if (data != null) {
                uiState = uiState.copy(
                    relaxationDone = data["relaxationDone"] as? String ?: "",
                    relaxationDuration = data["relaxationDuration"] as? String ?: "",
                    soundTherapyDone = data["soundTherapyDone"] as? String ?: "",
                    soundTherapyDuration = data["soundTherapyDuration"] as? String ?: "",
                    tinnitusLevel = (data["tinnitusLevel"] as? Long)?.toInt() ?: 5,
                    anxietyLevel = (data["anxietyLevel"] as? Long)?.toInt() ?: 5,
                    docId = docId,
                    isUpdating = true
                )
            }
        }
    }

    val submitCheckIn: () -> Unit = {
        if (email.isNotEmpty()) {
            scope.launch {
                val data = mapOf(
                    "relaxationDone" to uiState.relaxationDone,
                    "relaxationDuration" to uiState.relaxationDuration,
                    "soundTherapyDone" to uiState.soundTherapyDone,
                    "soundTherapyDuration" to uiState.soundTherapyDuration,
                    "tinnitusLevel" to uiState.tinnitusLevel,
                    "anxietyLevel" to uiState.anxietyLevel
                )

                val docId = repo.saveCheckIn(email, uiState.docId, data)
                uiState = uiState.copy(docId = docId, isUpdating = true)

                withContext(Dispatchers.Main) {
                    val recommendationText = when {
                        uiState.relaxationDone == "No" && uiState.soundTherapyDone == "No" ->
                            "You skipped both relaxation and sound therapy. Would you like to update your check-in?"
                        uiState.relaxationDone == "No" ->
                            "You skipped relaxation today. Would you like to update it?"
                        uiState.soundTherapyDone == "No" ->
                            "You skipped sound therapy today. Want to log it now?"
                        else -> ""
                    }

                    if (recommendationText.isNotEmpty()) {
                        popupMessage = recommendationText
                        showRecommendationPopup = true
                    } else {
                        Toast.makeText(context, "Check-in submitted!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Daily Check-In", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        CheckInQuestion("Have you done your relaxation exercises today?",
            options = listOf("Yes", "No"),
            selected = uiState.relaxationDone,
            onSelect = { uiState = uiState.copy(relaxationDone = it) })

        CheckInQuestion("How long did you practice the exercises today?",
            options = listOf("<5 min", "5-10 min", "10-20 min", ">20 min"),
            selected = uiState.relaxationDuration,
            onSelect = { uiState = uiState.copy(relaxationDuration = it) })

        CheckInQuestion("Did you use sound therapy today?",
            options = listOf("Yes", "No"),
            selected = uiState.soundTherapyDone,
            onSelect = { uiState = uiState.copy(soundTherapyDone = it) })

        CheckInQuestion("What was the duration of sound therapy?",
            options = listOf("<10 min", "10-30 min", "30-60 min", ">1 hour"),
            selected = uiState.soundTherapyDuration,
            onSelect = { uiState = uiState.copy(soundTherapyDuration = it) })

        LevelSlider("How is your tinnitus today? (1–10)",
            uiState.tinnitusLevel) { uiState = uiState.copy(tinnitusLevel = it) }

        LevelSlider("How anxious do you feel today? (1–10)",
            uiState.anxietyLevel) { uiState = uiState.copy(anxietyLevel = it) }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = submitCheckIn, modifier = Modifier.fillMaxWidth()) {
            Text(if (uiState.isUpdating) "Update Check-In" else "Submit")
        }

        if (showRecommendationPopup) {
            RecommendationDialog(
                message = popupMessage,
                onDismiss = {
                    showRecommendationPopup = false
                    Toast.makeText(context, "Check-in submitted!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                onUpdate = { showRecommendationPopup = false }
            )
        }
    }
}

@Composable
fun CheckInQuestion(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(title)
    RadioRow(options = options, selected = selected, onSelect = onSelect)
}

@Composable
fun LevelSlider(title: String, value: Int, onChange: (Int) -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(title)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = 1f..10f,
        steps = 8
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("1 (Lowest)", style = MaterialTheme.typography.bodySmall)
        Text("10 (Highest)", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun RecommendationDialog(message: String, onDismiss: () -> Unit, onUpdate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recommendation") },
        text = { Text(message) },
        confirmButton = { Button(onClick = onUpdate) { Text("Update Check-In") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Skip") } }
    )
}

@Composable
fun RadioRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEach { option ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RadioButton(
                    selected = selected == option,
                    onClick = { onSelect(option) }
                )
                Text(option)
            }
        }
    }
}