package com.example.tinnitusaiish.util

import android.content.Context
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

fun insertFakeCheckInData(context: Context, email: String) {
    val firestore = FirebaseFirestore.getInstance()
    val scope = CoroutineScope(Dispatchers.IO)

    scope.launch {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -6) // start 6 months ago
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        while (calendar.before(today)) {
            val date = dateFormat.format(calendar.time)

            if (Random.nextFloat() < 0.2f) { // simulate missing check-ins
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                continue
            }

            // Random values
            val tinnitus = Random.nextInt(1, 11)
            val anxiety = Random.nextInt(1, 11)

            val relaxationDone = if (Random.nextBoolean()) "Yes" else "No"
            val relaxationDuration = listOf("<5 min", "5-10 min", "10-20 min", ">20 min").random()

            val soundDone = if (Random.nextBoolean()) "Yes" else "No"
            val soundDuration = listOf("<10 min", "10-30 min", "30-60 min", ">1 hour").random()

            val checkInData = mapOf(
                "userEmail" to email,
                "date" to date,
                "tinnitusLevel" to tinnitus,
                "anxietyLevel" to anxiety,
                "relaxationDone" to relaxationDone,
                "relaxationDuration" to relaxationDuration,
                "soundTherapyDone" to soundDone,
                "soundTherapyDuration" to soundDuration
            )

            try {
                val existing = firestore.collection("checkins")
                    .whereEqualTo("userEmail", email)
                    .whereEqualTo("date", date)
                    .get()
                    .await()

                if (existing.isEmpty) {
                    firestore.collection("checkins").add(checkInData).await()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error inserting: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Dummy data added for $email", Toast.LENGTH_LONG).show()
        }
    }
}

fun deleteAllCheckIns(context: Context, email: String) {
    val firestore = FirebaseFirestore.getInstance()
    val scope = CoroutineScope(Dispatchers.IO)

    scope.launch {
        try {
            val querySnapshot = firestore.collection("checkins")
                .whereEqualTo("userEmail", email)
                .get()
                .await()

            for (document in querySnapshot.documents) {
                firestore.collection("checkins").document(document.id).delete().await()
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Old check-ins deleted for $email", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

