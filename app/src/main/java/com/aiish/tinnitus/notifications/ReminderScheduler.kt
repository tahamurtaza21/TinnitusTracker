package com.aiish.tinnitus.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

fun scheduleDailyCheckInReminder(context: Context) {
    // Use both WorkManager (for most devices) and AlarmManager (as backup for problematic devices)
    scheduleWithWorkManager(context)

    // Also schedule with AlarmManager as backup for devices with aggressive power management
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            scheduleWithAlarmManager(context)
        }
    } else {
        scheduleWithAlarmManager(context)
    }
}

private fun scheduleWithWorkManager(context: Context) {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9) // 9 AM
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }

    val now = Calendar.getInstance()
    var delay = calendar.timeInMillis - now.timeInMillis
    if (delay < 0) delay += TimeUnit.DAYS.toMillis(1)

    // Create constraints that are more lenient
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(false)  // Don't require battery not low
        .setRequiresDeviceIdle(false)     // Don't require device idle
        .setRequiresCharging(false)       // Don't require charging
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)  // Don't require network
        .build()

    val workRequest = PeriodicWorkRequestBuilder<CheckInReminderWorker>(
        1, TimeUnit.DAYS,
        15, TimeUnit.MINUTES  // Flex period: can run within 15 min window
    )
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .addTag("daily_checkin_reminder")  // Add tag for debugging
        .build()

    // REPLACE ensures we don't create duplicate work
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily_checkin_reminder",
        ExistingPeriodicWorkPolicy.REPLACE,
        workRequest
    )
}

private fun scheduleWithAlarmManager(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        100,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        // If time has passed today, schedule for tomorrow
        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    // Use setRepeating for better reliability on older devices
    // Use setExactAndAllowWhileIdle for newer devices to ensure delivery even in Doze mode
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            // API 23+: Use setExactAndAllowWhileIdle with manual rescheduling
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
            // API 19+: Use setExact
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        else -> {
            // Older versions: Use setRepeating
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }
}

fun cancelDailyCheckInReminder(context: Context) {
    // Cancel WorkManager work
    WorkManager.getInstance(context).cancelUniqueWork("daily_checkin_reminder")

    // Cancel AlarmManager alarm
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        100,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}