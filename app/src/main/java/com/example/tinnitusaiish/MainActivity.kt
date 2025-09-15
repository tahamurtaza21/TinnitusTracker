package com.example.tinnitusaiish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tinnitusaiish.ui.admin.AdminDashboardScreen
import com.example.tinnitusaiish.ui.admin.AdminUserReportsScreen
import com.example.tinnitusaiish.ui.auth.LoginScreen
import com.example.tinnitusaiish.ui.auth.SignUpScreen
import com.example.tinnitusaiish.ui.user.CheckInScreen
import com.example.tinnitusaiish.ui.user.HomeScreen
import com.example.tinnitusaiish.ui.user.ReportScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 🔐 Check if user is logged in via Firebase
        val isLoggedIn = FirebaseAuth.getInstance().currentUser != null

        setContent {

            // added dummy data

//            val email = "tahamurtaza15@gmail.com"
//            deleteAllCheckIns(this, email)
//            insertFakeCheckInData(this, email)

            val navController = rememberNavController()

            // Optional override to go directly to "checkin" if triggered by notification
            val startDestination = if (intent?.getStringExtra("navigate_to") == "checkin") {
                "checkin"
            } else {
                "home"
            }

            NavHost(
                navController = navController,
                startDestination = if (isLoggedIn) "home" else "login"
            ) {
                composable("login") {
                    LoginScreen(
                        onLoginSuccess = { navController.navigate("home") },
                        onAdminLoginSuccess = { navController.navigate("admin_dashboard") },
                        onNavigateToSignUp = { navController.navigate("signup") }
                    )
                }

                composable("signup") {
                    SignUpScreen(
                        onSignUpSuccess = { navController.navigate("login") },
                        onNavigateToLogin = { navController.navigate("login") }
                    )
                }

                composable("home") {
                    HomeScreen(navController = navController)
                }

                composable("checkin") {
                    CheckInScreen(navController = navController)
                }
                composable("report") {
                    ReportScreen(navController = navController)
                }
                composable("admin_dashboard") {
                    AdminDashboardScreen(navController = navController)
                }
                composable("admin_user_reports/{uid}") { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("uid") ?: ""
                    AdminUserReportsScreen(uid = uid)
                }

            }
        }
    }
}

