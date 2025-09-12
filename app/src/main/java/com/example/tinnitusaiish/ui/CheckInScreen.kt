package com.example.tinnitusaiish.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun CheckInScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val email = prefs.getString("logged_in_email", "") ?: ""
    val firestore = FirebaseFirestore.getInstance()

    var relaxationDone by remember { mutableStateOf("") }
    var relaxationDuration by remember { mutableStateOf("") }
    var soundTherapyDone by remember { mutableStateOf("") }
    var soundTherapyDuration by remember { mutableStateOf("") }
    var tinnitusLevel by remember { mutableIntStateOf(5) }
    var anxietyLevel by remember { mutableIntStateOf(5) }

    var isUpdating by remember { mutableStateOf(false) }
    var docIdToUpdate by remember { mutableStateOf<String?>(null) }

    var showRecommendationPopup by remember { mutableStateOf(false) }
    var popupMessage by remember { mutableStateOf("") }

    LaunchedEffect(email) {
        if (email.isNotEmpty()) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
            val snapshot = firestore.collection("checkins")
                .whereEqualTo("userEmail", email)
                .whereEqualTo("date", today)
                .get()
                .await()

            val doc = snapshot.documents.firstOrNull()
            if (doc != null) {
                isUpdating = true
                docIdToUpdate = doc.id

                relaxationDone = doc.getString("relaxationDone") ?: ""
                relaxationDuration = doc.getString("relaxationDuration") ?: ""
                soundTherapyDone = doc.getString("soundTherapyDone") ?: ""
                soundTherapyDuration = doc.getString("soundTherapyDuration") ?: ""
                tinnitusLevel = (doc.getLong("tinnitusLevel") ?: 5).toInt()
                anxietyLevel = (doc.getLong("anxietyLevel") ?: 5).toInt()
            }
        }
    }

    val submitCheckIn: () -> Unit = {
        if (email.isNotEmpty()) {
            scope.launch {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
                val checkIns = firestore.collection("checkins")

                val checkInData = hashMapOf(
                    "userEmail" to email,
                    "date" to today,
                    "relaxationDone" to relaxationDone,
                    "relaxationDuration" to relaxationDuration,
                    "soundTherapyDone" to soundTherapyDone,
                    "soundTherapyDuration" to soundTherapyDuration,
                    "tinnitusLevel" to tinnitusLevel,
                    "anxietyLevel" to anxietyLevel
                )

                if (docIdToUpdate != null) {
                    checkIns.document(docIdToUpdate!!).set(checkInData).await()
                } else {
                    val added = checkIns.add(checkInData).await()
                    docIdToUpdate = added.id
                    isUpdating = true
                }

                withContext(Dispatchers.Main) {
                    val recommendationText = when {
                        relaxationDone == "No" && soundTherapyDone == "No" ->
                            "You skipped both relaxation and sound therapy. Would you like to update your check-in?"
                        relaxationDone == "No" ->
                            "You skipped relaxation today. Would you like to update it?"
                        soundTherapyDone == "No" ->
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

        Text("Have you done your relaxation exercises today?")
        RadioRow(options = listOf("Yes", "No"), selected = relaxationDone) {
            relaxationDone = it
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("How long did you practice the exercises today?")
        RadioRow(options = listOf("<5 min", "5-10 min", "10-20 min", ">20 min"), selected = relaxationDuration) {
            relaxationDuration = it
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Did you use sound therapy today?")
        RadioRow(options = listOf("Yes", "No"), selected = soundTherapyDone) {
            soundTherapyDone = it
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("What was the duration of sound therapy?")
        RadioRow(options = listOf("<10 min", "10-30 min", "30-60 min", ">1 hour"), selected = soundTherapyDuration) {
            soundTherapyDuration = it
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("How is your tinnitus today? (1–10)")
        Slider(
            value = tinnitusLevel.toFloat(),
            onValueChange = { tinnitusLevel = it.toInt() },
            valueRange = 1f..10f,
            steps = 8
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("How anxious do you feel today? (1–10)")
        Slider(
            value = anxietyLevel.toFloat(),
            onValueChange = { anxietyLevel = it.toInt() },
            valueRange = 1f..10f,
            steps = 8
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = submitCheckIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isUpdating) "Update Check-In" else "Submit")
        }

        if (showRecommendationPopup) {
            AlertDialog(
                onDismissRequest = { showRecommendationPopup = false },
                title = { Text("Recommendation") },
                text = { Text(popupMessage) },
                confirmButton = {
                    Button(onClick = {
                        showRecommendationPopup = false
                    }) {
                        Text("Update Check-In")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        showRecommendationPopup = false
                        Toast.makeText(context, "Check-in submitted!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }) {
                        Text("Skip")
                    }
                }
            )
        }
    }
}

@Composable
fun RadioRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
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
