package com.aiish.tinnitus.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

fun scheduleDailyCheckInReminder(context: Context) {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9) // 9 AM
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }

    val now = Calendar.getInstance()
    var delay = calendar.timeInMillis - now.timeInMillis
    if (delay < 0) delay += TimeUnit.DAYS.toMillis(1)

    val workRequest = PeriodicWorkRequestBuilder<CheckInReminderWorker>(
        1, TimeUnit.DAYS
    )
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(false)  // run even if battery is low
                .setRequiresDeviceIdle(false)     // don't wait for idle mode
                .build()
        )
        .build()


    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily_checkin_reminder",
        ExistingPeriodicWorkPolicy.REPLACE,
        workRequest
    )
}
