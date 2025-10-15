package com.aiish.tinnitus.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Receives alarm broadcasts and triggers notification
 * Also reschedules the next alarm for API 23+ devices
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val role = prefs.getString("logged_in_role", "user")

        // Skip notifications for admins
        if (role == "admin") {
            return
        }

        // Trigger the notification worker immediately
        val workRequest = OneTimeWorkRequestBuilder<CheckInReminderWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)

        // For API 23+, we need to manually reschedule because setExactAndAllowWhileIdle doesn't repeat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scheduleDailyCheckInReminder(context)
        }
    }
}