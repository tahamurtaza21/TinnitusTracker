package com.aiish.tinnitus.util

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    /**
     * Checks if all notification requirements are met
     */
    fun areNotificationsFullyEnabled(context: Context): Boolean {
        // 1. Check notification permission (API 33+)
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // 2. Check if notifications are enabled at system level
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        // 3. Check battery optimization
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)

        return hasPermission && notificationsEnabled && batteryOptimizationIgnored
    }

    /**
     * Shows a dialog explaining notification issues and guiding user to settings
     */
    fun showNotificationSetupDialog(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val manufacturer = Build.MANUFACTURER.lowercase()

        val message = buildString {
            append("To receive daily check-in reminders, please enable:\n\n")

            // Check what's missing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    append("✓ Notification Permission\n")
                }
            }

            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                append("✓ App Notifications\n")
            }

            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                append("✓ Battery Optimization\n")
            }

            // Add manufacturer-specific guidance
            append("\n")
            when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                    append("📱 Xiaomi/Redmi Users:\n")
                    append("• Enable Autostart\n")
                    append("• Disable battery saver for this app\n")
                    append("• Lock app in recent apps\n")
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    append("📱 Huawei/Honor Users:\n")
                    append("• Enable 'App launch' manually\n")
                    append("• Disable 'Close apps after screen lock'\n")
                }
                manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                    append("📱 Oppo/Realme Users:\n")
                    append("• Enable Autostart\n")
                    append("• Disable battery optimization\n")
                }
                manufacturer.contains("oneplus") -> {
                    append("📱 OnePlus Users:\n")
                    append("• Disable battery optimization\n")
                    append("• Enable background activity\n")
                }
                manufacturer.contains("samsung") -> {
                    append("📱 Samsung Users:\n")
                    append("• Put app in 'Never sleeping apps'\n")
                    append("• Disable 'Put app to sleep'\n")
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Enable Notifications")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                openNotificationSettings(context)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    /**
     * Opens the appropriate settings screen
     */
    fun openNotificationSettings(context: Context) {
        try {
            // Try to open app-specific notification settings
            val intent = Intent()
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                else -> {
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.data = Uri.parse("package:${context.packageName}")
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Request battery optimization exemption
     */
    fun requestBatteryOptimization(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to battery optimization settings
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
    }

    /**
     * Opens manufacturer-specific battery/power management settings
     */
    fun openPowerManagementSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()

        try {
            val intent = when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                manufacturer.contains("oppo") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }
                manufacturer.contains("vivo") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }
                else -> {
                    // Fallback to battery settings
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Final fallback
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}