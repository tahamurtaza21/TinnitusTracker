package com.aiish.tinnitus.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiish.tinnitus.util.LoginValidator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ForgotPasswordScreen(onBackToLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Reset Password",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Enter your email address and we'll send you a link to reset your password.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        InputField(
            value = email,
            onValueChange = {
                email = it
                emailError = !LoginValidator.isValidEmail(it) && it.isNotEmpty()
                errorMessage = ""
            },
            label = "Email Address",
            error = emailError && email.isNotEmpty(),
            errorMessage = "Please enter a valid email address",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            )
        )

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = {
                emailError = !LoginValidator.isValidEmail(email)

                if (!emailError && email.isNotEmpty()) {
                    isLoading = true
                    scope.launch {
                        try {
                            auth.sendPasswordResetEmail(email).await()
                            Toast.makeText(
                                context,
                                "Password reset email sent! Check your inbox.",
                                Toast.LENGTH_LONG
                            ).show()
                            onBackToLogin()
                        } catch (e: Exception) {
                            errorMessage = when {
                                e.message?.contains("no user record", ignoreCase = true) == true ->
                                    "No account found with this email address"
                                e.message?.contains("network", ignoreCase = true) == true ->
                                    "Network error. Please check your connection"
                                else -> "Failed to send reset email: ${e.message}"
                            }
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "SENDING..." else "SEND RESET EMAIL")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBackToLogin) {
            Text("Back to Login")
        }
    }
}