package com.aiish.tinnitus.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

@SuppressLint("ScheduleExactAlarm")
fun scheduleDailyCheckInReminder(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
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

    // Cancel any existing alarm
    alarmManager.cancel(pendingIntent)

    // Use setAlarmClock for guaranteed delivery even when app is killed
    val clockInfo = AlarmManager.AlarmClockInfo(
        calendar.timeInMillis,
        pendingIntent
    )
    alarmManager.setAlarmClock(clockInfo, pendingIntent)
}