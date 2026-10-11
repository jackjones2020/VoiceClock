package com.example.voiceclock

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class SpeechTrackerTest {
    private fun record(time: String = "2026-10-11T23:59:58+08:00") = SpeechRecord(ZonedDateTime.parse(time), "现在时间", "自动报时")
    @Test fun completedSpeechIsRecordedOnlyOnce() {
        val tracker = SpeechTracker()
        val expected = record()
        val id = tracker.begin(expected)
        assertEquals(expected, tracker.finish(id))
        assertNull(tracker.finish(id))
    }
    @Test fun failuresInterruptionsAndShutdownNeverProduceSuccess() {
        val tracker = SpeechTracker()
        val failed = tracker.begin(record())
        tracker.fail(failed)
        assertNull(tracker.finish(failed))
        val stopped = tracker.begin(record())
        tracker.clear()
        assertNull(tracker.finish(stopped))
    }
    @Test fun overlappingSpeechHasSeparateIdsAndKeepsTheSpokenDate() {
        val tracker = SpeechTracker()
        val first = record()
        val second = record("2026-10-12T00:00:01+08:00")
        val a = tracker.begin(first)
        val b = tracker.begin(second)
        assertNotEquals(a, b)
        assertEquals("2026-10-12", tracker.finish(b)!!.time.toLocalDate().toString())
        assertEquals("2026-10-11", tracker.finish(a)!!.time.toLocalDate().toString())
    }
}
