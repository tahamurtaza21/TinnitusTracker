package com.aiish.tinnitus.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

/**
 * Reschedules notifications after device reboot
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if user is logged in
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                val role = prefs.getString("logged_in_role", "user")

                // Only reschedule for non-admin users
                if (role != "admin") {
                    scheduleDailyCheckInReminder(context)
                }
            }
        }
    }
}