package com.aiish.tinnitus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiish.tinnitus.notifications.scheduleDailyCheckInReminder
import com.aiish.tinnitus.ui.admin.AdminDashboardScreen
import com.aiish.tinnitus.ui.admin.AdminUserReportsScreen
import com.aiish.tinnitus.ui.auth.LoginScreen
import com.aiish.tinnitus.ui.auth.SignUpScreen
import com.aiish.tinnitus.ui.user.CheckInScreen
import com.aiish.tinnitus.ui.user.HomeScreen
import com.aiish.tinnitus.ui.user.ReportScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private var isOnLoginScreen = false

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isOnLoginScreen) {
            // ✅ Exit app when on login screen - use finishAffinity to clear everything
            finishAffinity()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)  // ✅ Pass null to prevent state restoration
        enableEdgeToEdge()

        setContent {
            // ✅ Check auth state and role dynamically
            var startDestination by remember { mutableStateOf<String?>(null) }
            val currentUser = FirebaseAuth.getInstance().currentUser

            LaunchedEffect(Unit) {
                // ✅ ALWAYS start at login to avoid back stack issues
                // The login screen will immediately navigate if user is already logged in
                startDestination = "login"
            }

            // ✅ Wait until we determine the start destination
            if (startDestination != null) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = startDestination!!
                ) {
                    composable("login") {
                        // ✅ Track that we're on login screen
                        LaunchedEffect(Unit) {
                            isOnLoginScreen = true

                            // ✅ Auto-navigate if already logged in
                            val currentUser = FirebaseAuth.getInstance().currentUser
                            if (currentUser != null) {
                                try {
                                    val tokenResult = currentUser.getIdToken(true).await()
                                    val role = tokenResult.claims["role"] as? String

                                    if (role == "admin") {
                                        isOnLoginScreen = false
                                        navController.navigate("admin_dashboard") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    } else {
                                        scheduleDailyCheckInReminder(this@MainActivity)
                                        isOnLoginScreen = false
                                        navController.navigate("home") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Stay on login if error
                                }
                            }
                        }

                        LoginScreen(
                            onLoginSuccess = {
                                isOnLoginScreen = false
                                navController.navigate("home") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onAdminLoginSuccess = {
                                isOnLoginScreen = false
                                navController.navigate("admin_dashboard") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onNavigateToSignUp = {
                                isOnLoginScreen = false
                                navController.navigate("signup")
                            }
                        )
                    }

                    composable("signup") {
                        LaunchedEffect(Unit) {
                            isOnLoginScreen = false
                        }
                        SignUpScreen(
                            onSignUpSuccess = { navController.navigate("login") },
                            onNavigateToLogin = { navController.navigate("login") }
                        )
                    }

                    composable("home") {
                        LaunchedEffect(Unit) {
                            isOnLoginScreen = false
                        }

                        HomeScreen(
                            navController = navController,
                            onLogout = {
                                android.util.Log.d("MainActivity", "Logout clicked - current user: ${FirebaseAuth.getInstance().currentUser?.email}")
                                FirebaseAuth.getInstance().signOut()
                                android.util.Log.d("MainActivity", "After signout - current user: ${FirebaseAuth.getInstance().currentUser}")

                                // ✅ Kill everything and restart fresh
                                finishAffinity()
                                val intent = packageManager.getLaunchIntentForPackage(packageName)
                                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            }
                        )
                    }

                    composable("checkin") {
                        LaunchedEffect(Unit) {
                            isOnLoginScreen = false
                        }
                        CheckInScreen(navController = navController)
                    }

                    composable("report") {
                        LaunchedEffect(Unit) {
                            isOnLoginScreen = false
                        }
                        ReportScreen(navController = navController)
                    }

                    composable("admin_dashboard") {
                        LaunchedEffect(Unit) {
                            isOnLoginScreen = false
                        }

                        // ✅ Handle back button to sign out
                        androidx.activity.compose.BackHandler {
                            FirebaseAuth.getInstance().signOut()
                            finishAffinity()
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }

                        AdminDashboardScreen(
                            navController = navController,
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                finishAffinity()
                                val intent = packageManager.getLaunchIntentForPackage(packageName)
                                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            }
                        )
                    }

                    composable("admin_user_reports/{uid}") { backStackEntry ->
                        LaunchedEffect(Unit) {
                            isOnLoginScreen = false
                        }
                        val uid = backStackEntry.arguments?.getString("uid") ?: ""
                        AdminUserReportsScreen(uid = uid)
                    }
                }
            }
        }
    }
}