package com.aiish.tinnitus.ui.admin

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.aiish.tinnitus.data.ReportRepository
import com.aiish.tinnitus.model.generateReport
import com.aiish.tinnitus.util.generatePdf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportFile(
    val name: String,
    val url: String
)

@Composable
fun AdminUserReportsScreen(uid: String) {

    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        android.util.Log.d("AdminDebug", "=== AUTH DEBUG ===")
        android.util.Log.d("AdminDebug", "Current Auth UID: ${currentUser?.uid}")
        android.util.Log.d("AdminDebug", "Current Auth Email: ${currentUser?.email}")
        android.util.Log.d("AdminDebug", "Is Anonymous: ${currentUser?.isAnonymous}")

        // Check if this UID exists in admins collection
        try {
            val db = FirebaseFirestore.getInstance()
            val adminDoc = currentUser?.uid?.let { db.collection("admins").document(it).get().await() }
            android.util.Log.d("AdminDebug", "Admin doc exists: ${adminDoc?.exists()}")
            android.util.Log.d("AdminDebug", "Admin doc data: ${adminDoc?.data}")
        } catch (e: Exception) {
            android.util.Log.e("AdminDebug", "Error checking admin: ${e.message}")
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<ReportFile>>(emptyList()) }
    var currentAdminEmail by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    // ✅ Get logged-in admin email
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        currentAdminEmail = prefs.getString("logged_in_email", null)
    }

    // ✅ Fetch uploaded report files for the patient
    LaunchedEffect(uid) {
        val storageRef = FirebaseStorage.getInstance().reference
        val userReportsRef = storageRef.child("reports").child(uid)

        userReportsRef.listAll()
            .addOnSuccessListener { listResult ->
                val tempList = mutableListOf<ReportFile>()
                listResult.items.forEach { fileRef ->
                    fileRef.downloadUrl.addOnSuccessListener { uri ->
                        tempList.add(ReportFile(fileRef.name, uri.toString()))
                        reports = tempList.toList()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "❌ Failed to load reports.", Toast.LENGTH_SHORT).show()
            }
    }

    // ✅ UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Reports", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (reports.isEmpty()) {
            Text("No reports found.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reports) { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 📄 Clicking name opens PDF
                            Text(
                                text = "📄 ${report.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(report.url)
                                        )
                                        context.startActivity(intent)
                                    }
                            )

                            // ✉️ Generate FRESH PDF and send
                            IconButton(
                                onClick = {
                                    val email = currentAdminEmail
                                    if (email.isNullOrBlank()) {
                                        Toast.makeText(context, "⚠️ No admin email found.", Toast.LENGTH_SHORT).show()
                                        return@IconButton
                                    }

                                    scope.launch {
                                        try {
                                            isSending = true
                                            Toast.makeText(context, "🔄 Generating fresh report...", Toast.LENGTH_SHORT).show()

                                            val db = FirebaseFirestore.getInstance()
                                            val userDoc = db.collection("users").document(uid).get().await()
                                            val patientName = userDoc.getString("name") ?: "Patient"
                                            val patientEmail = userDoc.getString("email") ?: ""

                                            // Determine report range from filename
                                            val reportRange = when {
                                                report.name.contains("Weekly", ignoreCase = true) -> "weekly"
                                                report.name.contains("Monthly", ignoreCase = true) -> "monthly"
                                                report.name.contains("Full", ignoreCase = true) -> "since_signup"
                                                else -> "weekly"
                                            }

                                            // ✅ FETCH FRESH DATA from Firestore
                                            val repository = ReportRepository()
                                            val allCheckIns = repository.fetchCheckIns(patientEmail)

                                            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                            val today = df.format(Date())

                                            // Filter check-ins based on range
                                            val filteredCheckIns = repository.filterByRange(allCheckIns, reportRange, today)

                                            // Generate report data
                                            val reportData = generateReport(filteredCheckIns)

                                            // ✅ GENERATE FRESH PDF with TODAY's date ranges
                                            val pdfFile = generatePdf(
                                                context = context,
                                                report = reportData,
                                                patientName = patientName,
                                                userNote = "Generated on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}",
                                                reportRange = reportRange
                                            )

                                            // Upload fresh PDF to Storage with timestamp
                                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                            val newFileName = "Tinnitus_${reportRange}_${timestamp}.pdf"
                                            val storageRef = FirebaseStorage.getInstance().reference
                                                .child("reports")
                                                .child(uid)
                                                .child(newFileName)

                                            FileInputStream(pdfFile).use { stream ->
                                                storageRef.putStream(stream).await()
                                            }

                                            val downloadUrl = storageRef.downloadUrl.await().toString()

                                            // Send email via Cloud Function
                                            val data = hashMapOf(
                                                "email" to email,
                                                "fileUrl" to downloadUrl,
                                                "fileName" to newFileName,
                                                "patientName" to patientName,
                                                "reportRange" to reportRange
                                            )

                                            FirebaseFunctions.getInstance()
                                                .getHttpsCallable("sendDoctorReport")
                                                .call(data)
                                                .await()

                                            Toast.makeText(context, "✅ Fresh report sent to $email", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "❌ ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            e.printStackTrace()
                                        } finally {
                                            isSending = false
                                        }
                                    }
                                },
                                enabled = !isSending
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send Fresh Report",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}