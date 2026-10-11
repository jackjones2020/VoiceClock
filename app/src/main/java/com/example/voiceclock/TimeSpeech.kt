package com.example.voiceclock

import android.media.AudioAttributes
import android.speech.tts.TextToSpeech

/** The chime and spoken time share one queue, so speech starts only after all three beeps. */
object TimeSpeech {
    private const val CHIME = "voice-clock-chime"
    private const val CHIME_SUFFIX = ":chime"

    fun configure(tts: TextToSpeech, packageName: String): Boolean {
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        return tts.setAudioAttributes(audio) == TextToSpeech.SUCCESS &&
            tts.setSpeechRate(0.5f) == TextToSpeech.SUCCESS &&
            tts.addEarcon(CHIME, packageName, R.raw.time_chime) == TextToSpeech.SUCCESS
    }

    fun speak(tts: TextToSpeech?, text: String, id: String): Int {
        if (tts == null) return TextToSpeech.ERROR
        if (tts.playEarcon(CHIME, TextToSpeech.QUEUE_FLUSH, null, id + CHIME_SUFFIX) != TextToSpeech.SUCCESS)
            return TextToSpeech.ERROR
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        if (result != TextToSpeech.SUCCESS) tts.stop()
        return result
    }

    fun isChime(id: String) = id.endsWith(CHIME_SUFFIX)
    fun speechId(id: String) = id.removeSuffix(CHIME_SUFFIX)
}
