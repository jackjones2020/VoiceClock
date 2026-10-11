package com.example.voiceclock

import java.time.ZonedDateTime
import java.util.UUID

data class SpeechRecord(val time: ZonedDateTime, val text: String, val source: String)

/** TTS callbacks can arrive on different threads; only a completed utterance is successful. */
class SpeechTracker {
    private val pending = mutableMapOf<String, SpeechRecord>()
    @Synchronized fun begin(record: SpeechRecord): String = UUID.randomUUID().toString().also { pending[it] = record }
    @Synchronized fun finish(id: String): SpeechRecord? = pending.remove(id)
    @Synchronized fun fail(id: String) { pending.remove(id) }
    @Synchronized fun clear() { pending.clear() }
}
