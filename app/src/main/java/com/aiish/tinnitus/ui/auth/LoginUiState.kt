package com.aiish.tinnitus.ui.auth

data class LoginUiState(
    var email: String = "",
    var password: String = "",
    var emailError: Boolean = false,
    var passwordError: Boolean = false,
    var loginError: String = ""
)
