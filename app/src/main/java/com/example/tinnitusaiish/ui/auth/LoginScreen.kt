package com.example.tinnitusaiish.ui.auth

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.tinnitusaiish.data.AuthRepository
import com.example.tinnitusaiish.notifications.CheckInReminderWorker
import com.example.tinnitusaiish.notifications.scheduleDailyCheckInReminder
import com.example.tinnitusaiish.util.LoginValidator
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onAdminLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = AuthRepository()

    var uiState by remember { mutableStateOf(LoginUiState()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Log In",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Email input
        InputField(
            value = uiState.email,
            onValueChange = {
                uiState = uiState.copy(
                    email = it,
                    emailError = !LoginValidator.isValidEmail(it) && it.isNotEmpty(),
                    loginError = ""
                )
            },
            label = "Email Address",
            error = uiState.emailError && uiState.email.isNotEmpty(),
            errorMessage = "Please enter a valid email address",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        // Password input
        InputField(
            value = uiState.password,
            onValueChange = {
                uiState = uiState.copy(
                    password = it,
                    passwordError = !LoginValidator.isValidPassword(it),
                    loginError = ""
                )
            },
            label = "Password",
            error = uiState.passwordError && uiState.password.isNotEmpty(),
            errorMessage = "Password is required",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        if (uiState.loginError.isNotEmpty()) {
            Text(
                text = uiState.loginError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = {
                uiState = uiState.copy(
                    emailError = !LoginValidator.isValidEmail(uiState.email),
                    passwordError = !LoginValidator.isValidPassword(uiState.password)
                )

                if (!uiState.emailError && !uiState.passwordError) {
                    scope.launch {
                        val result = repo.loginUser(context, uiState.email, uiState.password)
                        result.onSuccess { (email, role) ->
                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()

                            // Notification permissions (API 33+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    ActivityCompat.requestPermissions(
                                        context as Activity,
                                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                        1001
                                    )
                                }
                            }

                            // Daily reminder setup
                            scheduleDailyCheckInReminder(context)
                            val nowWork = OneTimeWorkRequestBuilder<CheckInReminderWorker>().build()
                            WorkManager.getInstance(context).enqueue(nowWork)

                            // 🚨 Role-based navigation
                            if (role == "admin") {
                                onAdminLoginSuccess()
                            } else {
                                onLoginSuccess()
                            }
                        }.onFailure { e ->
                            uiState = uiState.copy(loginError = "Login failed: ${e.message}")
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("LOG IN")
        }

        TextButton(
            onClick = { /* forgot password logic */ },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Forgot Password?")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Don't have an account? ")
            TextButton(onClick = onNavigateToSignUp) {
                Text("Sign Up")
            }
        }
    }
}
