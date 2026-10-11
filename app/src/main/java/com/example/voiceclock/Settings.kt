package com.example.voiceclock

import android.content.Context

object Settings {
    fun load(context: Context): Schedule {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val days = prefs.getString("days", "1,2,3,4,5,6,7")!!.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet()
        val slots = prefs.getString("slots", "0-0")!!.split(',').mapNotNull {
            val values = it.split('-').mapNotNull(String::toIntOrNull)
            if (values.size == 2 && values.all { n -> n in 0..1439 }) TimeSlot(values[0], values[1]) else null
        }
        return Schedule(days, slots, prefs.getInt("mode", 0).coerceIn(0, 3), prefs.getInt("interval", 30).coerceAtLeast(1))
    }

    fun save(context: Context, schedule: Schedule) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("days", schedule.days.sorted().joinToString(","))
            .putString("slots", schedule.slots.joinToString(",") { "${it.start}-${it.end}" })
            .putInt("mode", schedule.mode).putInt("interval", schedule.interval).apply()
    }

    fun enabled(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
    }
}
