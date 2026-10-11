package com.example.voiceclock

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class ScheduleTest {
    private fun time(value: String) = ZonedDateTime.parse(value + "+08:00[Asia/Shanghai]")
    @Test fun selectedDaysAndEndBoundary() {
        val s = Schedule(setOf(1), listOf(TimeSlot(540, 720)))
        assertTrue(s.allows(time("2026-10-12T09:00:00")))
        assertFalse(s.allows(time("2026-10-12T12:00:00")))
        assertFalse(s.allows(time("2026-10-13T09:00:00")))
    }
    @Test fun overnightBelongsToStartingDay() {
        val s = Schedule(setOf(1), listOf(TimeSlot(1320, 120)))
        assertTrue(s.allows(time("2026-10-12T23:00:00")))
        assertTrue(s.allows(time("2026-10-13T01:59:00")))
        assertFalse(s.allows(time("2026-10-13T02:00:00")))
        assertFalse(s.allows(time("2026-10-12T01:00:00")))
    }
    @Test fun modesAlignWithClock() {
        val now = time("2026-10-12T09:17:42")
        assertEquals(time("2026-10-12T10:00:00"), Schedule(mode = 1).next(now, now))
        assertEquals(time("2026-10-12T09:30:00"), Schedule(mode = 2).next(now, now))
        assertEquals(time("2026-10-12T09:30:00"), Schedule(mode = 3).next(now, now))
        val half = time("2026-10-12T09:30:00")
        assertEquals(time("2026-10-12T10:00:00"), Schedule(mode = 3).next(half, half))
    }
    @Test fun intervalPreservesStartSecondsAndSkipsClosedSlots() {
        val now = time("2026-10-12T09:17:42")
        assertEquals(time("2026-10-12T09:47:42"), Schedule(interval = 30).next(now, now))
        val s = Schedule(slots = listOf(TimeSlot(540, 600), TimeSlot(840, 900)), mode = 1)
        assertEquals(time("2026-10-12T14:00:00"), s.next(time("2026-10-12T09:30:00"), now))
    }
    @Test fun findsNextWeekAndRejectsImpossibleSchedule() {
        val now = time("2026-10-12T10:00:00")
        val s = Schedule(setOf(1), listOf(TimeSlot(540, 600)), mode = 1)
        assertEquals(time("2026-10-19T09:00:00"), s.next(now, now))
        assertNull(Schedule(days = emptySet()).next(now, now))
        assertNull(Schedule(slots = listOf(TimeSlot(541, 550)), mode = 1).next(now, now))
    }
}
