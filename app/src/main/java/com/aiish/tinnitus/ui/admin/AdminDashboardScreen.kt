package com.aiish.tinnitus.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class User(
    val uid: String = "",
    val name: String = ""
)

@Composable
fun AdminDashboardScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<User>>(emptyList()) }

    // Fetch users when screen opens
    LaunchedEffect(Unit) {
        scope.launch {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("users").get().await()
            users = snapshot.documents.map { doc ->
                User(
                    uid = doc.id,
                    name = doc.getString("name") ?: ""
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "📊 Admin Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
        )

        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            TableCell("Name", isHeader = true)
        }

        // User rows
        LazyColumn {
            items(users) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TableCell(
                        text = user.name.ifEmpty { "N/A" },
                        isHeader = false,
                        isClickable = true
                    ) {
                        navController.navigate("admin_user_reports/${user.uid}")
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    isHeader: Boolean = false,
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .weight(1f)
            .border(1.dp, Color.Gray)
            .background(
                when {
                    isHeader -> Color(0xFFE0E0E0) // header gray
                    else -> Color.White
                }
            )
            .clickable(
                enabled = isClickable,
                indication = null, // suppress ripple, we’ll do custom color
                interactionSource = interactionSource
            ) {
                onClick?.invoke()
            }
            .padding(12.dp)
    ) {
        Text(
            text = text,
            color = if (isClickable) Color(0xFF1565C0) else Color.Black, // blue link style
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
