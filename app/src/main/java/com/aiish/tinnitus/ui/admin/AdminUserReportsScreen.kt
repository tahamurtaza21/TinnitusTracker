package com.aiish.tinnitus.ui.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.storage.FirebaseStorage

data class ReportFile(
    val name: String,
    val url: String
)

@Composable
fun AdminUserReportsScreen(uid: String) {
    val context = LocalContext.current
    var reports by remember { mutableStateOf<List<ReportFile>>(emptyList()) }

    // Fetch reports from Storage when screen opens
    LaunchedEffect(uid) {
        val storageRef = FirebaseStorage.getInstance().reference
        val userReportsRef = storageRef.child("reports").child(uid)

        userReportsRef.listAll()
            .addOnSuccessListener { listResult ->
                val tempList = mutableListOf<ReportFile>()
                listResult.items.forEach { fileRef ->
                    fileRef.downloadUrl.addOnSuccessListener { uri ->
                        tempList.add(ReportFile(fileRef.name, uri.toString()))
                        reports = tempList.toList() // trigger UI update
                    }
                }
            }
            .addOnFailureListener {
                // Handle errors (e.g. permissions)
            }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Reports", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        if (reports.isEmpty()) {
            Text("No reports found.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn {
                items(reports) { report ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable {
                                // Open PDF in browser or PDF viewer
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(report.url))
                                context.startActivity(intent)
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("File: ${report.name}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
