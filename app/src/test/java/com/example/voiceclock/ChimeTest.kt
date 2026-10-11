package com.example.voiceclock

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class ChimeTest {
    @Test fun chimeHasThreeClearSpacedTonesThatGrowLouder() {
        val bytes = File("src/main/res/raw/time_chime.wav").readBytes()
        val wav = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(1, wav.getShort(22).toInt())
        assertEquals(16000, wav.getInt(24))
        assertEquals(16, wav.getShort(34).toInt())
        assertEquals(2.6, wav.getInt(40) / 32000.0, 0.00001)
        fun sample(index: Int) = wav.getShort(44 + index * 2).toInt()
        // Detect actual sound in 50 ms windows: 0.6 s tones separated by 0.4 s gaps.
        val audible = (0 until 52).map { window ->
            (0 until 800).any { offset -> abs(sample(window * 800 + offset)) > 100 }
        }
        assertEquals(List(12) { true } + List(8) { false } + List(12) { true } + List(8) { false } + List(12) { true }, audible)
        // Measure the pitch from upward zero crossings in the steady part of the first tone.
        val cycles = (800 until 2400).count { sample(it - 1) < 0 && sample(it) >= 0 }
        assertEquals(660.0, cycles / 0.1, 10.0)
        assertTrue((0 until wav.getInt(40) / 2).maxOf { abs(sample(it)) } <= 7600)
        // Verify the waveform itself has three increasingly loud beeps.
        val rms = listOf(0, 16000, 32000).map { start ->
            kotlin.math.sqrt((start until start + 9600).map { sample(it).toDouble() * sample(it) }.average())
        }
        assertTrue(rms[1] > rms[0] * 1.5)
        assertTrue(rms[2] > rms[1] * 1.5)
        assertEquals(0, sample(0))
        assertEquals(0, sample(9599))
    }
}
