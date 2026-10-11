package com.example.voiceclock

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.tts.TextToSpeech
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ClockService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingImmediate = false
    private val handler = Handler(Looper.getMainLooper())
    private var schedule = Schedule()
    private var anchor = ZonedDateTime.now()
    private var next: ZonedDateTime? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!Settings.enabled(this@ClockService)) { stopSelf(); return }
            val now = ZonedDateTime.now()
            val due = next
            if (due != null && !now.isBefore(due)) {
                if (ready && schedule.allows(now)) announce()
                next = schedule.next(now, anchor)
                updateNotification()
            }
            if (next?.zone != now.zone || (next != null && java.time.Duration.between(now, next).toDays() > 8)) {
                anchor = now; next = schedule.next(now, anchor); updateNotification()
            }
            val delay = next?.let { java.time.Duration.between(now, it).toMillis().coerceIn(100L, 30_000L) } ?: 30_000L
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("voice_clock", "语音报时", NotificationManager.IMPORTANCE_LOW)
        )
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        schedule = Settings.load(this)
        anchor = ZonedDateTime.now()
        next = schedule.next(anchor, anchor)
        startForeground(1, notification())
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voiceclock:schedule").apply { acquire() }
        }
        pendingImmediate = intent?.action == START && schedule.allows(anchor)
        if (pendingImmediate && ready) { announce(); pendingImmediate = false }
        handler.removeCallbacks(tick); handler.post(tick)
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val language = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            ready = language != null && language >= TextToSpeech.LANG_AVAILABLE
        }
        if (pendingImmediate) {
            if (ready && schedule.allows(ZonedDateTime.now())) announce()
            pendingImmediate = false
        }
        updateNotification()
    }

    private fun announce() {
        val now = ZonedDateTime.now()
        tts?.speak("现在时间，${now.hour}点${now.minute}分", TextToSpeech.QUEUE_FLUSH, null, "voice-clock-time")
    }

    private fun updateNotification() {
        if (Settings.enabled(this)) getSystemService(NotificationManager::class.java).notify(1, notification())
    }

    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val detail = if (!ready) "正在准备中文语音；如无声音请检查系统语音设置" else
            "下次：${next?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "没有可用时间"}"
        return Notification.Builder(this, "voice_clock").setContentTitle("语音报时已开启")
            .setContentText(detail).setStyle(Notification.BigTextStyle().bigText("${Schedule.modes[schedule.mode]} · $detail"))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(pending).setOngoing(true).build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        const val START = "com.example.voiceclock.START"
        const val RELOAD = "com.example.voiceclock.RELOAD"
    }
}
