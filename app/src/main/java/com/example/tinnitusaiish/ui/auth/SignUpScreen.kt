package com.example.tinnitusaiish.ui.auth

import android.content.Context
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
import androidx.compose.material3.OutlinedTextField
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
import com.example.tinnitusaiish.data.AuthRepository
import com.example.tinnitusaiish.util.SignUpValidator
import kotlinx.coroutines.launch

data class SignUpUiState(
    var name: String = "",
    var email: String = "",
    var password: String = "",
    var confirmPassword: String = "",
    var nameError: Boolean = false,
    var emailError: Boolean = false,
    var passwordError: Boolean = false,
    var confirmPasswordError: Boolean = false
)

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = AuthRepository()

    var uiState by remember { mutableStateOf(SignUpUiState()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        InputField(
            value = uiState.name,
            onValueChange = {
                uiState = uiState.copy(name = it, nameError = it.isEmpty())
            },
            label = "Full Name",
            error = uiState.nameError,
            errorMessage = "Name is required",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        InputField(
            value = uiState.email,
            onValueChange = {
                uiState = uiState.copy(
                    email = it,
                    emailError = !SignUpValidator.isValidEmail(it)
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

        InputField(
            value = uiState.password,
            onValueChange = {
                uiState = uiState.copy(
                    password = it,
                    passwordError = !SignUpValidator.isValidPassword(it)
                )
            },
            label = "Password",
            error = uiState.passwordError && uiState.password.isNotEmpty(),
            errorMessage = "Password must be at least 6 characters",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            )
        )

        InputField(
            value = uiState.confirmPassword,
            onValueChange = {
                uiState = uiState.copy(
                    confirmPassword = it,
                    confirmPasswordError = !SignUpValidator.doPasswordsMatch(
                        uiState.password,
                        it
                    )
                )
            },
            label = "Confirm Password",
            error = uiState.confirmPasswordError && uiState.confirmPassword.isNotEmpty(),
            errorMessage = "Passwords do not match",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        Button(
            onClick = {
                uiState = uiState.copy(
                    nameError = uiState.name.isEmpty(),
                    emailError = !SignUpValidator.isValidEmail(uiState.email),
                    passwordError = !SignUpValidator.isValidPassword(uiState.password),
                    confirmPasswordError = !SignUpValidator.doPasswordsMatch(
                        uiState.password,
                        uiState.confirmPassword
                    )
                )

                if (!uiState.nameError && !uiState.emailError &&
                    !uiState.passwordError && !uiState.confirmPasswordError
                ) {
                    scope.launch {
                        val result = repo.signUpUser(context, uiState.email, uiState.password)
                        result.onSuccess {
                            // Save role as "user" by default
                            val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("logged_in_email", uiState.email)
                                .putString("logged_in_role", "user")
                                .apply()

                            Toast.makeText(context, "Account created successfully!", Toast.LENGTH_LONG).show()
                            onSignUpSuccess()
                        }.onFailure { e ->
                            Toast.makeText(context, "Sign up failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("SIGN UP")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Already have an account? ")
            TextButton(onClick = onNavigateToLogin) {
                Text("Sign In")
            }
        }
    }
}

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: Boolean,
    errorMessage: String,
    keyboardOptions: KeyboardOptions,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation ?: androidx.compose.ui.text.input.VisualTransformation.None
        )
        if (error) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}
