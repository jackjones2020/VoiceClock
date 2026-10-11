package com.example.voiceclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action != android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        if (!Settings.enabled(context) || !ClockAlarms.permitted(context)) return
        // Recompute without starting a foreground service from a time-change broadcast.
        val now = java.time.ZonedDateTime.now()
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val saved = prefs.getLong("anchor", now.toInstant().toEpochMilli())
        val anchor = java.time.Instant.ofEpochMilli(saved).atZone(now.zone)
        ClockAlarms.schedule(context, Settings.load(context).next(now, anchor))
    }
}
