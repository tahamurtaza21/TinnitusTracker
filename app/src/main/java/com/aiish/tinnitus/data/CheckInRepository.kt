package com.aiish.tinnitus.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckInRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun getTodayCheckIn(email: String): Pair<String?, Map<String, Any>?> {
        val today = dateFormat.format(Date())
        val snapshot = firestore.collection("checkins")
            .whereEqualTo("userEmail", email)
            .whereEqualTo("date", today)
            .get()
            .await()

        val doc = snapshot.documents.firstOrNull()
        return if (doc != null) Pair(doc.id, doc.data) else Pair(null, null)
    }

    suspend fun saveCheckIn(email: String, docId: String?, data: Map<String, Any>): String {
        val today = dateFormat.format(Date())
        val checkIns = firestore.collection("checkins")

        val checkInData = data.toMutableMap().apply {
            put("userEmail", email)
            put("date", today)
        }

        return if (docId != null) {
            checkIns.document(docId).set(checkInData).await()
            docId
        } else {
            val added = checkIns.add(checkInData).await()
            added.id
        }
    }
}
