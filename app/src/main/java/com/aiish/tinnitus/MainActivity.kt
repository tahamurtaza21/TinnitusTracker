package com.aiish.tinnitus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
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
            finishAffinity()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        requestNotificationPermissionIfNeeded()
        requestBatteryOptimizationExemption()  // Add this


        setContent {
            var startDestination by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                startDestination = "login"
            }

            if (startDestination != null) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = startDestination!!
                ) {

                    composable("login") {
                        LaunchedEffect(Unit) {
                            isOnLoginScreen = true

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
                                } catch (_: Exception) {
                                }
                            }
                        }

                        LoginScreen(
                            onLoginSuccess = {
                                scheduleDailyCheckInReminder(this@MainActivity)
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
                        isOnLoginScreen = false
                        SignUpScreen(
                            onSignUpSuccess = { navController.navigate("login") },
                            onNavigateToLogin = { navController.navigate("login") }
                        )
                    }

                    composable("home") {
                        isOnLoginScreen = false
                        HomeScreen(
                            navController = navController,
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                finishAffinity()
                                startActivity(
                                    packageManager.getLaunchIntentForPackage(packageName)
                                )
                            }
                        )
                    }

                    composable("checkin") {
                        isOnLoginScreen = false
                        CheckInScreen(navController)
                    }

                    composable("report") {
                        isOnLoginScreen = false
                        ReportScreen(navController)
                    }

                    composable("admin_dashboard") {
                        isOnLoginScreen = false

                        BackHandler {
                            FirebaseAuth.getInstance().signOut()
                            finishAffinity()
                            startActivity(
                                packageManager.getLaunchIntentForPackage(packageName)
                            )
                        }

                        AdminDashboardScreen(
                            navController = navController,
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                finishAffinity()
                                startActivity(
                                    packageManager.getLaunchIntentForPackage(packageName)
                                )
                            }
                        )
                    }

                    composable("admin_user_reports/{uid}") { backStackEntry ->
                        val uid = backStackEntry.arguments?.getString("uid") ?: ""
                        AdminUserReportsScreen(uid)
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}
