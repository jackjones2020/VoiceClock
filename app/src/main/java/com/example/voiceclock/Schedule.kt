package com.example.voiceclock

import java.time.ZonedDateTime

data class TimeSlot(val start: Int, val end: Int) {
    fun contains(minute: Int) = when {
        start == end -> true
        start < end -> minute >= start && minute < end
        else -> minute >= start || minute < end
    }
}

data class Schedule(
    val days: Set<Int> = (1..7).toSet(),
    val slots: List<TimeSlot> = listOf(TimeSlot(0, 0)),
    val mode: Int = 0,
    val interval: Int = 30
) {
    fun allows(time: ZonedDateTime): Boolean {
        val minute = time.hour * 60 + time.minute
        return slots.any { slot ->
            if (!slot.contains(minute)) false
            else {
                // 跨午夜时段的凌晨部分归属前一天。
                val day = if (slot.start > slot.end && minute < slot.end)
                    time.minusDays(1).dayOfWeek.value else time.dayOfWeek.value
                day in days
            }
        }
    }

    fun next(after: ZonedDateTime, anchor: ZonedDateTime): ZonedDateTime? {
        if (mode == 0) {
            val elapsed = java.time.Duration.between(anchor, after).toMinutes().coerceAtLeast(0)
            var candidate = anchor.plusMinutes((elapsed / interval + 1) * interval)
            repeat(8 * 24 * 60 / interval + 1) {
                if (candidate.isAfter(after) && allows(candidate)) return candidate
                candidate = candidate.plusMinutes(interval.toLong())
            }
            return null
        }
        var candidate = after.withSecond(0).withNano(0).plusMinutes(1)
        repeat(8 * 24 * 60) {
            val matches = when (mode) {
                1 -> candidate.minute == 0
                2 -> candidate.minute == 30
                3 -> candidate.minute == 0 || candidate.minute == 30
                else -> Math.floorMod(java.time.Duration.between(anchor.withSecond(0).withNano(0), candidate).toMinutes(), interval.toLong()) == 0L
            }
            if (matches && allows(candidate)) return candidate
            candidate = candidate.plusMinutes(1)
        }
        return null
    }

    companion object {
        val modes = listOf("每隔 N 分钟", "仅整点（每小时 :00）", "仅半点（每小时 :30）", "整点和半点（:00 / :30）")
        fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    }
}
