package com.example.voiceclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object ClockAlarms {
    private fun manager(context: Context) = context.getSystemService(AlarmManager::class.java)
    fun permitted(context: Context) = Build.VERSION.SDK_INT < 31 || manager(context).canScheduleExactAlarms()
    private fun operation(context: Context, time: Long = 0) = PendingIntent.getForegroundService(
        context, 2, Intent(context, ClockService::class.java).setAction(ClockService.ALARM).putExtra("due", time),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    fun next(context: Context): ZonedDateTime? {
        val millis = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getLong("next", 0)
        return if (millis == 0L) null else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    }
    fun schedule(context: Context, time: ZonedDateTime?): Boolean {
        if (!permitted(context)) { cancel(context); return false }
        if (time == null) { cancel(context); return true }
        val millis = time.toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            // Alarm-clock alarms wake from Doze even for the supported one-minute interval.
            manager(context).setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), operation(context, millis))
        } catch (_: SecurityException) { cancel(context); return false }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putLong("next", millis).apply()
        return true
    }
    fun cancel(context: Context) {
        manager(context).cancel(operation(context))
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("next").apply()
    }
}
